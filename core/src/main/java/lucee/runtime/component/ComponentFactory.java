package lucee.runtime.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

import lucee.runtime.ComponentImpl;
import lucee.runtime.ComponentPageImpl;
import lucee.runtime.ComponentProperties;
import lucee.runtime.PageContext;
import lucee.runtime.StaticScope;
import lucee.runtime.exp.PageException;
import lucee.runtime.op.Duplicator;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.UDF;

/**
 * Cached recipe for instantiating a body-declarative CFC class. Built once per class from a seed
 * instance produced by the standard init path; subsequent {@link #mint(PageContext, boolean)} calls
 * allocate a fresh ComponentImpl wired to shared class-level state without re-running the seed's
 * per-instance work. Eligibility is gated by
 * {@link ComponentPageImpl#hasPseudoConstructorBody()} returning false.
 */
public final class ComponentFactory {

	// Class-level shared refs. All immutable after build.
	private final ComponentProperties properties;
	private final ComponentImpl base;
	private final ComponentPageImpl cp;

	// Class-level cached data. udfsTemplate is read-only after build (defensive copy at mint time).
	private final Map<Key, UDF> udfsTemplate;
	private final List<DefaultEntry> defaultsInOrder;
	private final boolean isRestEnabled;

	private ComponentFactory(ComponentProperties properties, ComponentImpl base, ComponentPageImpl cp, Map<Key, UDF> udfsTemplate, List<DefaultEntry> defaultsInOrder,
			boolean isRestEnabled) {
		this.properties = properties;
		this.base = base;
		this.cp = cp;
		this.udfsTemplate = udfsTemplate;
		this.defaultsInOrder = defaultsInOrder;
		this.isRestEnabled = isRestEnabled;
	}

	public ComponentProperties getProperties()       { return properties; }
	public ComponentImpl getBase()                   { return base; }
	public ComponentPageImpl getCp()                 { return cp; }
	public Map<Key, UDF> getUdfsTemplate()           { return udfsTemplate; }
	public List<DefaultEntry> getDefaultsInOrder()   { return defaultsInOrder; }
	public boolean isRestEnabled()                   { return isRestEnabled; }

	/**
	 * Build a factory from a seed instance produced by the standard init path. Captures the seed's
	 * class-level references and freezes a per-instance reconstruction recipe for property defaults.
	 */
	public static ComponentFactory fromSeed(ComponentImpl seed) {
		List<DefaultEntry> defaults = partitionDefaults(seed);
		Map<Key, UDF> udfsTemplate = new HashMap<>(seed._getUdfs());
		return new ComponentFactory(
				/* properties */ seed._getProperties(),
				/* base */ seed._getBase(),
				/* cp */ seed._getComponentPageImpl(),
				/* udfsTemplate */ udfsTemplate,
				/* defaultsInOrder */ defaults,
				/* isRestEnabled */ seed._isRestEnabled());
	}

	/**
	 * Walk the seed's {@code _data} and classify each entry's value into an Evaluator. Order is
	 * preserved from the underlying map's iteration order (typically a concurrent map populated in
	 * declaration order via {@code PropertyFactory.createGetter}, so chained defaults seed in the
	 * same order they do today).
	 */
	private static List<DefaultEntry> partitionDefaults(ComponentImpl seed) {
		Map<Key, Member> data = seed._getData();
		List<DefaultEntry> entries = new ArrayList<>(data.size());
		for (Entry<Key, Member> e: data.entrySet()) {
			Object value = e.getValue() == null ? null : e.getValue().getValue();
			entries.add(new DefaultEntry(e.getKey(), classify(value)));
		}
		return entries;
	}

	/**
	 * Pick an Evaluator kind for a resolved default value. Primitive boxed types, String, and null
	 * are immutable and safe to share by reference. Everything else gets a per-mint deep-duplicate
	 * to guarantee the LDEV-6303 mutable-default-isolation contract — even seemingly-immutable
	 * types (Date, custom Java objects) are bias-classed as mutable to avoid silent leaks.
	 */
	private static Evaluator classify(Object value) {
		if (value == null) return new LiteralRef(null);
		if (value instanceof String) return new LiteralRef(value);
		if (value instanceof Number) return new LiteralRef(value);
		if (value instanceof Boolean) return new LiteralRef(value);
		if (value instanceof Character) return new LiteralRef(value);
		final Object captured = value;
		return new LiteralBuilder(() -> Duplicator.duplicate(captured, true));
	}

	/**
	 * Allocate a fresh ComponentImpl per the cached recipe. Shares class-level refs, builds
	 * per-instance state, optionally dispatches the user's init() method.
	 */
	public ComponentImpl mint(PageContext pc, boolean executeConstr) throws PageException {
		ComponentImpl fresh = new ComponentImpl();
		fresh._initFromFactory(this, pc, executeConstr);
		return fresh;
	}

	/**
	 * One entry in the ordered defaults list. The evaluator produces the per-mint value; the order
	 * across entries matches declaration order in the source so chained defaults
	 * (e.g. {@code propB default="#variables.propA & 'y'#"}) seed in the same order they do today.
	 */
	public static final class DefaultEntry {
		private final Key key;
		private final Evaluator evaluator;

		DefaultEntry(Key key, Evaluator evaluator) {
			this.key = key;
			this.evaluator = evaluator;
		}

		public Key getKey() { return key; }
		public Evaluator getEvaluator() { return evaluator; }
	}

	/** Produces a value for a property default each time {@link #eval(PageContext)} is called. */
	public interface Evaluator {
		Object eval(PageContext pc) throws PageException;
	}

	/**
	 * Primitive boxed / String / null default. Same reference handed back every mint — safe because
	 * the value is immutable.
	 */
	static final class LiteralRef implements Evaluator {
		private final Object value;

		LiteralRef(Object value) {
			this.value = value;
		}

		@Override
		public Object eval(PageContext pc) {
			return value;
		}
	}

	/**
	 * Reference-typed default (Array, Struct, anything mutable). Fresh instance per mint via the
	 * supplier — prevents cross-instance leak of mutations to a shared default.
	 */
	static final class LiteralBuilder implements Evaluator {
		private final Supplier<Object> supplier;

		LiteralBuilder(Supplier<Object> supplier) {
			this.supplier = supplier;
		}

		@Override
		public Object eval(PageContext pc) {
			return supplier.get();
		}
	}

	// Expression-form property defaults (e.g. default="#now()#") are not currently handled here.
	// The walker in ASMUtil.hasPseudoConstructorBodyStatements classifies any CFC with an
	// expression-default cfproperty as factory-ineligible (True bucket), so they route through the
	// standard init path. A future iteration emits a per-class helper method that the factory can
	// call cheaply for fresh per-instance evaluation.
}
