package lucee.runtime.type;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import lucee.commons.io.SystemUtil;
import lucee.runtime.Page;
import lucee.runtime.PageContext;
import lucee.runtime.PageSource;
import lucee.runtime.PageSourceImpl;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.op.Caster;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.util.KeyConstants;

/**
 * LDEV-6716 spike instrumentation, off unless -Dlucee.key.census=true. Counts, for every dynamic key
 * made by KeyImpl.source, whether a per-class literal table or a per-parse table would have had it.
 * Prints a line to stderr every 30s.
 */
public final class KeyCensus {

	public static final boolean ENABLED = Caster.toBooleanValue(SystemUtil.getSystemPropOrEnvVar("lucee.key.census", null), false);

	private static final int MAX_MISS_NAMES = 20000;

	private static final LongAdder total = new LongAdder();
	private static final LongAdder noPage = new LongAdder();
	private static final LongAdder classHit = new LongAdder();
	private static final LongAdder anyLiteralHit = new LongAdder();
	private static final LongAdder constHit = new LongAdder();
	private static final int MAX_STACKS = 200;
	private static final Map<String, String> missStacks = new ConcurrentHashMap<>();
	private static final LongAdder miss = new LongAdder();
	private static final LongAdder parses = new LongAdder();
	private static final LongAdder parseKeys = new LongAdder();
	private static final LongAdder parseDistinct = new LongAdder();
	private static final LongAdder parseLiteralHit = new LongAdder();

	// a miss whose folded h64 is a compiled name: another spelling of something the code knows
	private static final LongAdder foldHit = new LongAdder();
	private static final Map<String, LongAdder> foldNames = new ConcurrentHashMap<>();
	private static volatile Set<Long> folded = Set.of();
	private static volatile int foldedAt = -1;
	private static volatile long foldedTime;

	// a miss name's stack is captured once it has been missed this often, so the hot names get one
	private static final long STACK_AT = 10000;

	// 1 in SAMPLE calls records its caller chain, per outcome
	private static final int SAMPLE = 256;
	private static final int MAX_CHAINS = 5000;
	private static final String[] OUTCOMES = { "class", "const", "literal", "fold", "miss" };
	private static final int CLASS = 0, CONST = 1, LITERAL = 2, FOLD = 3, MISS = 4;
	private static final LongAdder[] sampled = { new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder() };
	private static final LongAdder[] sampledCfml = { new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder() };
	@SuppressWarnings("unchecked")
	private static final Map<String, LongAdder>[] chains = new Map[] { new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
			new ConcurrentHashMap<>(), new ConcurrentHashMap<>() };
	private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	private static final Map<String, LongAdder> missNames = new ConcurrentHashMap<>();
	private static final Map<Class<?>, Set<String>> classLiterals = new ConcurrentHashMap<>();
	private static final ThreadLocal<Set<String>> parse = new ThreadLocal<>();

	static {
		if (ENABLED) {
			Thread t = new Thread(KeyCensus::report, "lucee-key-census");
			t.setDaemon(true);
			t.start();
		}
	}

	private KeyCensus() {
	}

	/** a deserializeJSON parse starts on this thread; source() calls until end() count against it */
	public static void begin() {
		parse.set(new HashSet<>());
	}

	public static void end() {
		Set<String> names = parse.get();
		parse.remove();
		if (names == null) return;
		parses.increment();
		parseDistinct.add(names.size());
	}

	static void source(String key) {
		Set<String> names = parse.get();
		if (names != null) {
			parseKeys.increment();
			if (names.add(key) && (KeyConstants.getKeys().containsKey(key) || KeyImpl.isLiteral(key))) parseLiteralHit.increment();
			return;
		}
		total.increment();
		int outcome = outcome(key);
		if (ThreadLocalRandom.current().nextInt(SAMPLE) == 0) sample(outcome);
	}

	private static int outcome(String key) {
		Set<String> literals = currentClassLiterals();
		if (literals == null) noPage.increment();
		else if (literals.contains(key)) {
			classHit.increment();
			return CLASS;
		}
		if (KeyConstants.getKeys().containsKey(key)) {
			constHit.increment();
			return CONST;
		}
		if (KeyImpl.isLiteral(key)) {
			anyLiteralHit.increment();
			return LITERAL;
		}
		if (folded().contains(KeyImpl.createHash64UC(key))) {
			foldHit.increment();
			count(foldNames, key);
			return FOLD;
		}
		miss.increment();
		LongAdder c = count(missNames, key);
		if (c != null && missStacks.size() < MAX_STACKS && !missStacks.containsKey(key) && c.sum() >= STACK_AT) missStacks.putIfAbsent(key, stack());
		return MISS;
	}

	private static LongAdder count(Map<String, LongAdder> names, String key) {
		LongAdder c = names.get(key);
		if (c == null && names.size() < MAX_MISS_NAMES) c = names.computeIfAbsent(key, k -> new LongAdder());
		if (c != null) c.increment();
		return c;
	}

	/** folded h64 of every compiled name, rebuilt when the table has grown, at most once a second */
	private static Set<Long> folded() {
		int n = KeyImpl.literalCount();
		long now = System.currentTimeMillis();
		if (n != foldedAt && now - foldedTime > 1000) {
			synchronized (KeyCensus.class) {
				if (n != foldedAt && now - foldedTime > 1000) {
					Set<Long> set = new HashSet<>();
					KeyImpl.forEachLiteral(k -> set.add(k.hash()));
					folded = set;
					foldedAt = n;
					foldedTime = now;
				}
			}
		}
		return folded;
	}

	/** the first frames outside KeyImpl / KeyCensus, compiled CFML collapsed to [cfml] */
	private static void sample(int outcome) {
		List<StackWalker.StackFrame> frames = WALKER.walk(s -> s.filter(f -> f.getDeclaringClass() != KeyCensus.class && f.getDeclaringClass() != KeyImpl.class).limit(3)
				.collect(Collectors.toList()));
		if (frames.isEmpty()) return;
		sampled[outcome].increment();
		if (Page.class.isAssignableFrom(frames.get(0).getDeclaringClass())) sampledCfml[outcome].increment();
		StringBuilder sb = new StringBuilder();
		for (StackWalker.StackFrame f: frames) {
			if (sb.length() > 0) sb.append(" < ");
			if (Page.class.isAssignableFrom(f.getDeclaringClass())) {
				sb.append("[cfml]");
				break;
			}
			String cn = f.getClassName();
			sb.append(cn.substring(cn.lastIndexOf('.') + 1)).append('.').append(f.getMethodName());
		}
		Map<String, LongAdder> m = chains[outcome];
		LongAdder c = m.get(sb.toString());
		if (c == null && m.size() < MAX_CHAINS) c = m.computeIfAbsent(sb.toString(), k -> new LongAdder());
		if (c != null) c.increment();
	}

	private static String stack() {
		StringBuilder sb = new StringBuilder();
		StackTraceElement[] st = new Throwable().getStackTrace();
		for (int i = 3; i < Math.min(st.length, 3 + 14); i++) {
			sb.append("\n    at ").append(st[i]);
		}
		return sb.toString();
	}

	private static Set<String> currentClassLiterals() {
		PageContext pc = ThreadLocalPageContext.get();
		if (pc == null) return null;
		PageSource ps = pc.getCurrentPageSource(null);
		if (!(ps instanceof PageSourceImpl)) return null;
		Page page = ((PageSourceImpl) ps).getPage();
		if (page == null) return null;
		Set<String> literals = classLiterals.get(page.getClass());
		if (literals == null) {
			literals = readLiterals(page);
			classLiterals.put(page.getClass(), literals);
		}
		return literals;
	}

	/** the generated page class keeps its literal keys in a field called keys, static or per instance */
	private static Set<String> readLiterals(Page page) {
		Set<String> set = new HashSet<>();
		try {
			Field f = page.getClass().getDeclaredField("keys");
			f.setAccessible(true);
			Key[] keys = (Key[]) f.get(Modifier.isStatic(f.getModifiers()) ? null : page);
			if (keys != null) for (Key k: keys) {
				if (k != null) set.add(k.getString());
			}
		}
		catch (NoSuchFieldException | IllegalAccessException e) {
			throw new IllegalStateException("can't read the keys field of [" + page.getClass().getName() + "]", e);
		}
		return set;
	}

	private static String top(Map<String, LongAdder> names, int n) {
		List<Map.Entry<String, Long>> list = new ArrayList<>();
		for (Map.Entry<String, LongAdder> e: names.entrySet()) {
			list.add(Map.entry(e.getKey(), e.getValue().sum()));
		}
		list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(n, list.size()); i++) {
			sb.append(" {").append(list.get(i).getKey()).append("}=").append(list.get(i).getValue());
		}
		return sb.toString();
	}

	private static void report() {
		while (true) {
			try {
				Thread.sleep(30000);
			}
			catch (InterruptedException e) {
				return;
			}
			long t = total.sum();
			StringBuilder sb = new StringBuilder("LDEV6716-CENSUS source=").append(t);
			sb.append(" classHit=").append(classHit.sum()).append(" const=").append(constHit.sum()).append(" anyLiteral=").append(anyLiteralHit.sum());
			sb.append(" miss=").append(miss.sum()).append(" noPage=").append(noPage.sum());
			sb.append(" classesSeen=").append(classLiterals.size());
			sb.append(" | json parses=").append(parses.sum()).append(" keys=").append(parseKeys.sum());
			sb.append(" distinct=").append(parseDistinct.sum()).append(" distinctLiteral=").append(parseLiteralHit.sum());
			sb.append(" | missNames=").append(missNames.size());
			// snapshot first: the counters keep moving and a sort on live values breaks TimSort's contract
			List<Map.Entry<String, Long>> top = new ArrayList<>();
			for (Map.Entry<String, LongAdder> e: missNames.entrySet()) {
				top.add(Map.entry(e.getKey(), e.getValue().sum()));
			}
			top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
			sb.append(" top:");
			for (int i = 0; i < Math.min(25, top.size()); i++) {
				sb.append(' ').append(top.get(i).getKey()).append('=').append(top.get(i).getValue());
			}
			System.err.println(sb);
			System.err.println("LDEV6716-CENSUS-FOLD fold=" + foldHit.sum() + " foldNames=" + foldNames.size() + " compiledFolded=" + folded.size() + " top:" + top(foldNames, 40));
			for (int o = 0; o < OUTCOMES.length; o++) {
				System.err.println("LDEV6716-CENSUS-CALLERS " + OUTCOMES[o] + " sampled=" + sampled[o].sum() + " cfml=" + sampledCfml[o].sum() + " top:" + top(chains[o], 15));
			}
			// where the top missed names come from
			for (int i = 0; i < Math.min(8, top.size()); i++) {
				String name = top.get(i).getKey();
				System.err.println("LDEV6716-CENSUS-STACK [" + name + "]" + missStacks.getOrDefault(name, " (none)"));
			}
		}
	}
}
