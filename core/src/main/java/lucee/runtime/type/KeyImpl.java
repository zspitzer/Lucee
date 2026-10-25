/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Association Switzerland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package lucee.runtime.type;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import lucee.commons.digest.WangJenkins;
import lucee.commons.io.SystemUtil;
import lucee.commons.lang.StringUtil;
import lucee.runtime.exp.CasterException;
import lucee.runtime.exp.PageException;
import lucee.runtime.op.Castable;
import lucee.runtime.op.Caster;
import lucee.runtime.op.OpUtil;
import lucee.runtime.op.date.DateCaster;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.dt.DateTime;

public class KeyImpl implements Collection.Key, Castable, Comparable, Externalizable, WangJenkins, CharSequence {

	private static final long serialVersionUID = -8864844181140115609L; // do not change

	/**
	 * bump whenever createHash64UC or createHash64CS change: compiled classes bake both hashes with
	 * this version, and initKeys recomputes them for a class from another version
	 */
	public static final int HASH_VERSION = 2;

	private static final long HSTART = 0xBB40E64DA205B064L;
	// exact-spelling hash starts elsewhere, so it never lines up with the case-folded one
	private static final long CSTART = HSTART ^ 0x9E3779B97F4A7C15L;
	private static final long HMULT = 7664345821815920749L;
	private static final long[] HASH_TABLE = createHashTable();
	private static final long[] FOLD_TABLE = createFoldTable();

	private static final int MAX = Caster.toInteger(SystemUtil.getSystemPropOrEnvVar("lucee.cache.variableKeys", null), 50000);

	// Reference fields (8 bytes each) - group together to minimize padding
	private String key;
	private transient String lcKey;
	private transient String ucKey;

	// long field (8 bytes)
	private transient long h64;

	// int field (4 bytes)
	private transient int wjh;

	// static field
	private static Map<String, Key> keys = new HashMap<String, Key>();
	private static final KeyTable literals = new KeyTable();

	public KeyImpl() {
		// DO NOT USE, JUST FOR UNSERIALIZE

	}

	public KeyImpl(String key) {
		this.key = key;
		h64 = createHash64UC(key);
		// print.e(key + ":" + (++count) + ":" + keys.size());
	}

	private KeyImpl(String key, long h64) {
		this.key = key;
		this.h64 = h64;
	}

	public static Map<String, Key> getKeys() {
		return keys;
	}

	/**
	 * splitmix64 stream, low byte table in [0,256), high byte table in [256,512). One continuous
	 * stream: seeding a stream per table makes the high table the low one shifted by one index
	 */
	private static final long[] createHashTable() {
		long[] t = new long[512];
		long x = 0x2545F4914F6CDD1DL;
		for (int i = 0; i < t.length; i++) {
			x += 0x9E3779B97F4A7C15L;
			long z = x;
			z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
			z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
			t[i] = z ^ (z >>> 31);
		}
		return t;
	}

	/** ASCII view of the low byte table with a-z folded onto A-Z */
	private static final long[] createFoldTable() {
		long[] t = new long[128];
		for (int c = 0; c < 128; c++) {
			t[c] = HASH_TABLE[(c >= 'a' && c <= 'z') ? c - 32 : c];
		}
		return t;
	}

	/**
	 * case insensitive, same value as createHash64UC
	 */
	public static final long createHash64(CharSequence cs) {
		return createHash64UC(cs.toString());
	}

	/**
	 * one multiply per char over the original-case key, ASCII case folded by the table, so no
	 * toUpperCase. Any char of 0x80 or above upper-cases (ROOT), and if that lands in ASCII (dotless
	 * i, long s, sharp s) it goes back through the fast loop, so equality still follows toUpperCase
	 */
	public static final long createHash64UC(String key) {
		long h = foldLoop(key);
		if (h != 0) return h;
		String uc = key.toUpperCase(Locale.ROOT);
		h = foldLoop(uc);
		return h != 0 ? h : hashChars(uc, HSTART);
	}

	/**
	 * case sensitive, the exact spelling: zac and ZAC differ. Used to intern literal keys without
	 * String.hashCode or equals
	 */
	public static final long createHash64CS(String key) {
		return hashChars(key, CSTART);
	}

	/** 0 means not pure ASCII; a real result of 0 is remapped to 1 */
	private static long foldLoop(String s) {
		long h = HSTART;
		final long m = HMULT;
		final long[] t = FOLD_TABLE;
		final int len = s.length();
		int or = 0;
		for (int i = 0; i < len; i++) {
			int c = s.charAt(i);
			or |= c;
			h = (h * m) ^ t[c & 0x7F];
		}
		if (or >= 0x80) return 0;
		return h == 0 ? 1 : h;
	}

	private static long hashChars(String s, long start) {
		long h = start;
		final long m = HMULT;
		final long[] t = HASH_TABLE;
		final int len = s.length();
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			h = (h * m) ^ (t[c & 0xff] ^ t[256 + (c >>> 8)]);
		}
		return h;
	}

	@Override
	public int wangJenkinsHash() {
		if (wjh == 0) {
			int h = hashCode();
			h += (h << 15) ^ 0xffffcd7d;
			h ^= (h >>> 10);
			h += (h << 3);
			h ^= (h >>> 6);
			h += (h << 2) + (h << 14);
			wjh = h ^ (h >>> 16);
		}
		return wjh;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(key);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		key = (String) in.readObject();
		h64 = createHash64UC(key);
	}

	/**
	 * only used in KeyConstants
	 *
	 * @param key
	 * @return
	 */
	public static Collection.Key _const(Map<String, Key> keys, String key) {
		Key k = new KeyImpl(key);
		keys.put(key, k);
		return k;
	}

	/**
	 * literal values set in source code
	 *
	 * @param key
	 * @return
	 */
	public static Collection.Key getInstance(String key) {
		return initKeys(key);
	}

	// this method is only used by old lucee archives byte code loading their keys via this method
	public static Collection.Key intern(String key) {
		// log(key);
		return new KeyImpl(key);
	}

	/**
	 *
	 * used to create the keys for the method initKeys()
	 */
	public static Collection.Key initKeys(String key) {
		return intern(key, createHash64UC(key), createHash64CS(key));
	}

	/**
	 * literal keys with both hashes baked in by the compiler, interned on the exact spelling. A class
	 * compiled under another HASH_VERSION has hashes from another function, so they get recomputed
	 */
	public static Collection.Key initKeys(String key, long h64, long cs64, int version) {
		if (version != HASH_VERSION) return initKeys(key);
		return intern(key, h64, cs64);
	}

	private static Collection.Key intern(String key, long h64, long cs64) {
		KeyImpl k = literals.get(cs64);
		if (k != null) return k;
		return literals.put(cs64, new KeyImpl(key, h64));
	}

	/**
	 * for dynamic loading of key objects
	 *
	 * @param string
	 * @return
	 */
	public static Collection.Key init(String key) {
		return source(key);
	}

	/**
	 *
	 * used to inside the rest of the source created, can be dynamic values, so a lot
	 */
	public static Collection.Key source(String key) {
		// a compiled name (literal, KeyConstants, declared function) gets the compiled instance; the table
		// never admits a runtime name, so it can't fill with junk
		KeyImpl k = literals.get(createHash64CS(key));
		if (k != null) return k;
		return sourceRuntime(key);
	}

	private static Collection.Key sourceRuntime(String key) {
		if (MAX == 0) return new KeyImpl(key);
		Key k = keys.get(key);
		if (k == null) {
			if (keys.size() > MAX) return new KeyImpl(key);
			keys.put(key, k = new KeyImpl(key));
		}
		return k;
	}

	@Override
	public char charAt(int index) {
		return key.charAt(index);
	}

	@Override
	public char lowerCharAt(int index) {
		return getLowerString().charAt(index);
	}

	@Override
	public char upperCharAt(int index) {
		return getUpperString().charAt(index);
	}

	@Override
	public String getLowerString() {
		if (lcKey == null) lcKey = StringUtil.toLowerCase(key);
		return lcKey;
	}

	@Override
	public String getUpperString() {
		if (ucKey == null) ucKey = key.toUpperCase();
		return ucKey;
	}

	@Override
	public String toString() {
		return key;
	}

	@Override
	public String getString() {
		return key;
	}

	// kept under MaxInlineSize (35 bytes): the JDK maps call equals behind their == check, a cold site
	@Override
	public boolean equals(Object other) {
		if (other instanceof KeyImpl) {
			return h64 == ((KeyImpl) other).h64;
		}
		return equalsNonKeyImpl(other);
	}

	private boolean equalsNonKeyImpl(Object other) {
		if (other instanceof String) {
			return key.equalsIgnoreCase((String) other);
		}
		if (other instanceof Key) {
			// Both strings are guaranteed to be upper case
			return getUpperString().equals(((Key) other).getUpperString());
		}
		return false;
	}

	@Override
	public boolean equalsIgnoreCase(Key other) {
		if (other instanceof KeyImpl) {
			return h64 == ((KeyImpl) other).h64;// return lcKey.equals((((KeyImpl)other).lcKey));
		}
		return equalsIgnoreCaseNonKeyImpl(other);
	}

	private boolean equalsIgnoreCaseNonKeyImpl(Key other) {
		return getUpperString().equalsIgnoreCase(other.getLowerString());
	}

	@Override
	public int hashCode() {
		return (int) (h64 ^ (h64 >>> 32));
	}

	@Override
	public long hash() {
		return h64;
	}

	@Override
	public boolean castToBooleanValue() throws PageException {
		return Caster.toBooleanValue(key);
	}

	@Override
	public Boolean castToBoolean(Boolean defaultValue) {
		return Caster.toBoolean(key, defaultValue);
	}

	@Override
	public DateTime castToDateTime() throws PageException {
		return Caster.toDatetime(key, null);
	}

	@Override
	public DateTime castToDateTime(DateTime defaultValue) {
		return DateCaster.toDateAdvanced(key, DateCaster.CONVERTING_TYPE_OFFSET, null, defaultValue);
	}

	@Override
	public double castToDoubleValue() throws PageException {
		return Caster.toDoubleValue(key);
	}

	@Override
	public double castToDoubleValue(double defaultValue) {
		return Caster.toDoubleValue(key, defaultValue);
	}

	@Override
	public String castToString() throws PageException {
		return key;
	}

	@Override
	public String castToString(String defaultValue) {
		return key;
	}

	@Override
	public int compareTo(boolean b) throws PageException {
		return OpUtil.compare(null, key, b ? Boolean.TRUE : Boolean.FALSE);
	}

	@Override
	public int compareTo(DateTime dt) throws PageException {
		return OpUtil.compare(null, key, (Date) dt);
	}

	@Override
	public int compareTo(double d) throws PageException {
		return OpUtil.compare(null, key, Double.valueOf(d));
	}

	@Override
	public int compareTo(String str) throws PageException {
		return OpUtil.compare(null, key, str);
	}

	@Override
	public int compareTo(Object o) {
		try {
			return OpUtil.compare(null, key, o);
		}
		catch (PageException e) {
			ClassCastException cce = new ClassCastException(e.getMessage());
			cce.setStackTrace(e.getStackTrace());
			throw cce;

		}
	}

	public static Array toUpperCaseArray(Key[] keys) {
		ArrayImpl arr = new ArrayImpl();
		for (int i = 0; i < keys.length; i++) {
			arr.appendEL(((KeyImpl) keys[i]).getUpperString());
		}
		return arr;
	}

	public static Array toLowerCaseArray(Key[] keys) {
		ArrayImpl arr = new ArrayImpl();
		for (int i = 0; i < keys.length; i++) {
			arr.appendEL(((KeyImpl) keys[i]).getLowerString());
		}
		return arr;
	}

	public static Array toArray(Key[] keys) {
		ArrayImpl arr = new ArrayImpl();
		for (int i = 0; i < keys.length; i++) {
			arr.appendEL(((KeyImpl) keys[i]).getString());
		}
		return arr;
	}

	public static String toUpperCaseList(Key[] array, String delimiter) {
		if (array.length == 0) return "";
		StringBuilder sb = new StringBuilder(((KeyImpl) array[0]).getUpperString());

		if (delimiter.length() == 1) {
			char c = delimiter.charAt(0);
			for (int i = 1; i < array.length; i++) {
				sb.append(c);
				sb.append(((KeyImpl) array[i]).getUpperString());
			}
		}
		else {
			for (int i = 1; i < array.length; i++) {
				sb.append(delimiter);
				sb.append(((KeyImpl) array[i]).getUpperString());
			}
		}
		return sb.toString();
	}

	public static String toList(Key[] array, String delimiter) {
		if (array.length == 0) return "";
		StringBuilder sb = new StringBuilder(((KeyImpl) array[0]).getString());

		if (delimiter.length() == 1) {
			char c = delimiter.charAt(0);
			for (int i = 1; i < array.length; i++) {
				sb.append(c);
				sb.append((array[i]).getString());
			}
		}
		else {
			for (int i = 1; i < array.length; i++) {
				sb.append(delimiter);
				sb.append((array[i]).getString());
			}
		}
		return sb.toString();
	}

	public static String toLowerCaseList(Key[] array, String delimiter) {
		if (array.length == 0) return "";
		StringBuilder sb = new StringBuilder(((KeyImpl) array[0]).getLowerString());

		if (delimiter.length() == 1) {
			char c = delimiter.charAt(0);
			for (int i = 1; i < array.length; i++) {
				sb.append(c);
				sb.append(((KeyImpl) array[i]).getLowerString());
			}
		}
		else {
			for (int i = 1; i < array.length; i++) {
				sb.append(delimiter);
				sb.append(((KeyImpl) array[i]).getLowerString());
			}
		}
		return sb.toString();
	}

	public static Collection.Key toKey(Object obj, Collection.Key defaultValue) {
		if (obj instanceof Collection.Key) return (Collection.Key) obj;
		String str = Caster.toString(obj, null);
		if (str == null) return defaultValue;
		return init(str);
	}

	public static Collection.Key toKey(Object obj) throws CasterException {
		if (obj instanceof Collection.Key) return (Collection.Key) obj;
		String str = Caster.toString(obj, null);
		if (str == null) throw new CasterException(obj, Collection.Key.class);
		return init(str);
	}

	public static Collection.Key toKey(int i) {
		return init(Caster.toString(i));
	}

	@Override
	public int length() {
		return key.length();
	}

	public static Key[] toKeyArray(String[] arr) {
		if (arr == null) return null;

		Key[] keys = new Key[arr.length];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = init(arr[i]);
		}
		return keys;
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return getString().subSequence(start, end);
	}

	/*
	 * public static void main(String[] args) throws Exception { // KeyConstants._percentage
	 *
	 * modify(ResourcesImpl.getFileResourceProvider().getResource(
	 * "/Users/mic/Projects/Lucee/Lucee6/core/src/main/java/lucee"));
	 *
	 * }
	 *
	 * private static void modify(Resource resource) throws IOException { boolean stop = false; for
	 * (Resource r: resource.listResources()) { if (r.isDirectory()) modify(r); if
	 * (r.getAbsolutePath().endsWith(".java")) {
	 *
	 * String content = IOUtil.toString(r, "UTF-8"); String result = null; int start = -1, end; while
	 * ((start = content.indexOf("KeyImpl.getInstance(\"", start + 1)) != -1) { end =
	 * content.indexOf("\")", start + 22); if (end > start) { String k = content.substring(start + 21,
	 * end); if (KeyConstants.getFieldName(k) == null) print.e("public static final Key _" + k +
	 * " = KeyImpl._const(\"" + k + "\");"); result = content = content.substring(0, start) +
	 * "KeyConstants._" + k + content.substring(end + 2);
	 *
	 * stop = true;
	 *
	 * // print.e(content);
	 *
	 * start = end; } else break;
	 *
	 * } if (result != null) IOUtil.write(r, result, "UTF-8", false); // if (stop) throw new
	 * IOException("www"); } } }
	 */

}
