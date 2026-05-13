package lucee.runtime.component;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import lucee.runtime.ComponentImpl;
import lucee.runtime.ComponentPageImpl;
import lucee.runtime.ComponentProperties;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
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
	final ComponentProperties properties;
	final ComponentImpl base;
	final ComponentPageImpl cp;
	final StaticStruct staticHead;

	// Class-level cached data. udfsTemplate is read-only after build (defensive copy at mint time
	// via the constructed udfsMapClass instance).
	final Map<Key, UDF> udfsTemplate;
	final List<DefaultEntry> defaultsInOrder;
	final boolean isRestEnabled;
	final Class<? extends Map> udfsMapClass;

	private ComponentFactory(ComponentProperties properties, ComponentImpl base, ComponentPageImpl cp, StaticStruct staticHead, Map<Key, UDF> udfsTemplate,
			List<DefaultEntry> defaultsInOrder, boolean isRestEnabled, Class<? extends Map> udfsMapClass) {
		this.properties = properties;
		this.base = base;
		this.cp = cp;
		this.staticHead = staticHead;
		this.udfsTemplate = udfsTemplate;
		this.defaultsInOrder = defaultsInOrder;
		this.isRestEnabled = isRestEnabled;
		this.udfsMapClass = udfsMapClass;
	}

	/**
	 * Build a factory from a seed instance produced by the standard init path. Captures class-level
	 * derivable state and freezes a per-instance reconstruction recipe. The Evaluator partition and
	 * per-instance state capture land in later iterations; for now only the {@code cp} reference is
	 * stored so the cache contract holds.
	 */
	public static ComponentFactory fromSeed(ComponentImpl seed) {
		ComponentPageImpl cp = seed._getComponentPageImpl();
		return new ComponentFactory(
				/* properties */ null,
				/* base */ null,
				/* cp */ cp,
				/* staticHead */ null,
				/* udfsTemplate */ java.util.Collections.<Key, UDF>emptyMap(),
				/* defaultsInOrder */ java.util.Collections.<DefaultEntry>emptyList(),
				/* isRestEnabled */ false,
				/* udfsMapClass */ java.util.HashMap.class);
	}

	/**
	 * Allocate a fresh ComponentImpl per the cached recipe. Shares class-level refs, builds
	 * per-instance state, optionally dispatches the user's init() method.
	 */
	public ComponentImpl mint(PageContext pc, boolean executeConstr) throws PageException {
		throw new UnsupportedOperationException("ComponentFactory.mint not yet implemented");
	}

	/**
	 * One entry in the ordered defaults list. The evaluator produces the per-mint value; the order
	 * across entries matches declaration order in the source so chained defaults
	 * (e.g. {@code propB default="#variables.propA & 'y'#"}) seed in the same order they do today.
	 */
	static final class DefaultEntry {
		final Key key;
		final Evaluator evaluator;

		DefaultEntry(Key key, Evaluator evaluator) {
			this.key = key;
			this.evaluator = evaluator;
		}
	}

	/** Produces a value for a property default each time {@link #eval(PageContext)} is called. */
	interface Evaluator {
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
