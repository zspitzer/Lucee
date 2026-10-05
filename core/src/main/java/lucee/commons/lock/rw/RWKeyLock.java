/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
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
 **/
package lucee.commons.lock.rw;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import lucee.commons.lang.ExceptionUtil;
import lucee.commons.lock.Lock;
import lucee.commons.lock.LockException;
import lucee.commons.lock.LockInterruptedException;
import lucee.runtime.exp.PageRuntimeException;
import lucee.runtime.op.Caster;

public final class RWKeyLock<K> {

	// per-key atomicity comes from compute / computeIfPresent: a name must not be removed between one
	// thread's lookup and its inc, and different names must not contend
	private final ConcurrentHashMap<K, RWLock<K>> locks = new ConcurrentHashMap<K, RWLock<K>>();

	public Lock lock(K token, long timeout, boolean readOnly) throws LockException, LockInterruptedException {
		if (timeout <= 0) throw new LockException("timeout must be a positive number");

		RWLock<K> lock = locks.compute(token, (k, l) -> {
			if (l == null) l = new RWLock<K>(k);
			l.inc();
			return l;
		});
		RWWrap<K> wrap = new RWWrap<K>(lock, readOnly);
		try {
			wrap.lock(timeout);
		}
		catch (LockException e) {
			release(lock);
			throw e;
		}
		catch (LockInterruptedException e) {
			release(lock);
			throw e;
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			release(lock);
			throw new PageRuntimeException(Caster.toPageException(t));
		}
		return wrap;
	}

	public void unlock(Lock lock) {
		if (!(lock instanceof RWWrap)) {
			return;
		}

		lock.unlock();
		release(((RWWrap<K>) lock).getLock());
	}

	private void release(RWLock<K> lock) {
		locks.computeIfPresent(lock.getLabel(), (k, l) -> {
			if (l != lock) return l;
			l.dec();
			return l.getQueueLength() == 0 ? null : l;
		});
	}

	public List<K> getOpenLockNames() {
		Iterator<Entry<K, RWLock<K>>> it = locks.entrySet().iterator();
		Entry<K, RWLock<K>> entry;
		List<K> list = new ArrayList<K>();
		while (it.hasNext()) {
			entry = it.next();
			if (entry.getValue().getQueueLength() > 0) list.add(entry.getKey());
		}
		return list;
	}

	/**
	 * Queries if the write lock is held by any thread on given lock token, returns null when lock with
	 * this token does not exists
	 * 
	 * @param token name of the lock to check
	 * @return
	 */
	public Boolean isWriteLocked(K token) {
		RWLock<K> lock = locks.get(token);
		if (lock == null) return null;
		return lock.isWriteLocked();
	}

	/**
	 * Queries if one or more read lock is held by any thread on given lock token, returns null when
	 * lock with this token does not exists
	 * 
	 * @param token name of the lock to check
	 * @return
	 */
	public Boolean isReadLocked(K token) {
		RWLock<K> lock = locks.get(token);
		if (lock == null) return null;
		return lock.isReadLocked();
	}

	public void clean() {
		Iterator<Entry<K, RWLock<K>>> it = locks.entrySet().iterator();
		Entry<K, RWLock<K>> entry;

		while (it.hasNext()) {
			entry = it.next();
			if (entry.getValue().getQueueLength() == 0) {
				locks.computeIfPresent(entry.getKey(), (k, l) -> l.getQueueLength() == 0 ? null : l);
			}
		}
	}
}

class RWWrap<L> implements Lock {

	private RWLock<L> lock;
	private boolean readOnly;

	public RWWrap(RWLock<L> lock, boolean readOnly) {
		this.lock = lock;
		this.readOnly = readOnly;
	}

	@Override
	public void lock(long timeout) throws LockException, LockInterruptedException {
		lock.lock(timeout, readOnly);
	}

	@Override
	public void unlock() {
		lock.unlock(readOnly);
	}

	@Override
	public int getQueueLength() {
		return lock.getQueueLength();
	}

	public L getLabel() {
		return lock.getLabel();
	}

	public RWLock<L> getLock() {
		return lock;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

}