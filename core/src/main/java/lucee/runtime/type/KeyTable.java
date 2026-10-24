package lucee.runtime.type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Literal key intern table keyed on the exact-spelling hash (cs64), so a hit is one long compare,
 * no String.hashCode or equals. Open addressing, linear probing. Readers are lock-free, writers
 * lock and copy on resize. A key is published with release / read with acquire, so a reader never
 * sees a half-built KeyImpl
 */
final class KeyTable {

	private static final VarHandle KEYS = MethodHandles.arrayElementVarHandle(KeyImpl[].class);
	// a probe sequence longer than this gives up and the caller uses an uncached key
	private static final int MAX_PROBES = 8;

	private static final class Tab {
		final long[] hashes;
		final KeyImpl[] keys;

		Tab(int capacity) {
			hashes = new long[capacity];
			keys = new KeyImpl[capacity];
		}
	}

	private volatile Tab tab = new Tab(4096);
	private int size;

	KeyImpl get(long cs64) {
		if (cs64 == 0) cs64 = 1;
		Tab t = tab;
		int mask = t.hashes.length - 1;
		int slot = spread(cs64) & mask;
		for (int p = 0; p < MAX_PROBES; p++) {
			long h = t.hashes[slot];
			if (h == 0) return null;
			// hash seen before the key was published reads as a miss, the writer path rechecks
			if (h == cs64) return (KeyImpl) KEYS.getAcquire(t.keys, slot);
			slot = (slot + 1) & mask;
		}
		return null;
	}

	/** returns the key already in the table for cs64, or k once inserted; k uncached when probes run out */
	synchronized KeyImpl put(long cs64, KeyImpl k) {
		if (cs64 == 0) cs64 = 1;
		if ((size + 1) * 2 > tab.hashes.length) tab = resize(tab);
		Tab t = tab;
		int mask = t.hashes.length - 1;
		int slot = spread(cs64) & mask;
		for (int p = 0; p < MAX_PROBES; p++) {
			long h = t.hashes[slot];
			if (h == cs64) return t.keys[slot];
			if (h == 0) {
				KEYS.setRelease(t.keys, slot, k);
				t.hashes[slot] = cs64;
				size++;
				return k;
			}
			slot = (slot + 1) & mask;
		}
		return k;
	}

	private static Tab resize(Tab old) {
		Tab t = new Tab(old.hashes.length << 1);
		int mask = t.hashes.length - 1;
		for (int i = 0; i < old.hashes.length; i++) {
			long h = old.hashes[i];
			if (h == 0) continue;
			int slot = spread(h) & mask;
			while (t.hashes[slot] != 0)
				slot = (slot + 1) & mask;
			t.keys[slot] = old.keys[i];
			t.hashes[slot] = h;
		}
		return t;
	}

	private static int spread(long h) {
		return (int) (h ^ (h >>> 32));
	}
}
