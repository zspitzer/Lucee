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
import java.util.Iterator;
import java.util.Map.Entry;

import jakarta.servlet.jsp.tagext.BodyContent;
import lucee.commons.lang.CFTypes;
import lucee.commons.lang.ExceptionUtil;
import lucee.runtime.Component;
import lucee.runtime.PageContext;
import lucee.runtime.PageContextImpl;
import lucee.runtime.PageSource;
import lucee.runtime.cache.CacheObject;
import lucee.runtime.cache.tag.CacheHandler;
import lucee.runtime.cache.tag.CacheHandlerCollectionImpl;
import lucee.runtime.cache.tag.CacheHandlerPro;
import lucee.runtime.cache.tag.CacheItem;
import lucee.runtime.cache.tag.udf.UDFCacheItem;
import lucee.runtime.component.MemberSupport;
import lucee.runtime.config.Config;
import lucee.runtime.config.NullSupportHelper;
import lucee.runtime.dump.DumpData;
import lucee.runtime.dump.DumpProperties;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.ExpressionException;
import lucee.runtime.exp.PageException;
import lucee.runtime.exp.UDFCasterException;
import lucee.runtime.listener.ApplicationContextSupport;
import lucee.runtime.op.Caster;
import lucee.runtime.op.Decision;
import lucee.runtime.op.Duplicator;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.scope.Argument;
import lucee.runtime.type.scope.ArgumentIntKey;
import lucee.runtime.type.scope.Local;
import lucee.runtime.type.scope.LocalImpl;
import lucee.runtime.type.scope.Undefined;
import lucee.runtime.type.util.ComponentUtil;
import lucee.runtime.type.util.KeyConstants;
import lucee.runtime.type.util.UDFUtil;
import lucee.runtime.writer.BodyContentUtil;

/**
 * defines an abstract class for a User defined Functions
 */
public class UDFImpl extends MemberSupport implements UDFPlus, Externalizable, CacheObject {

	private static final long serialVersionUID = -7288148349256615519L; // do not change

	protected Component ownerComponent;
	public UDFPropertiesBase properties;

	/**
	 * DO NOT USE THIS CONSTRUCTOR! this constructor is only for deserialize process
	 */
	public UDFImpl() {
		super(0);
	}

	public UDFImpl(PageContext pc, UDFProperties properties) {
		super(properties.getAccess(), properties.getModifier());
		this.properties = (UDFPropertiesBase) properties;
	}

	public UDFImpl(UDFProperties properties) {
		super(properties.getAccess(), properties.getModifier());

		this.properties = (UDFPropertiesBase) properties;
	}

	public UDFImpl(UDFProperties properties, Component owner) {
		super(properties.getAccess(), properties.getModifier());
		this.properties = (UDFPropertiesBase) properties;
		setOwnerComponent(owner);
	}

	@Override
	protected final int hash() {
		return java.util.Objects.hash(getFunctionName(), getPageSource());
	}

	public UDF duplicate(Component cfc) {
		UDFImpl udf = new UDFImpl(properties);
		udf.ownerComponent = cfc;
		udf.setAccess(getAccess());
		return udf;
	}

	@Override
	public UDF duplicate(boolean deepCopy) {
		return duplicate(ownerComponent);
	}

	@Override
	public UDF duplicate() {
		return duplicate(ownerComponent);
	}

	@Override
	public Object implementation(PageContext pageContext) throws Throwable {
		return properties.getPage(pageContext).udfCall(pageContext, this, properties.getIndex());
	}

	private final Object castToAndClone(PageContext pc, FunctionArgument arg, Object value, int index, boolean fns) throws PageException {
		if (value == null && fns) return value;

		if (!((PageContextImpl) pc).getTypeChecking() || Decision.isCastableTo(pc, arg.getType(), arg.getTypeAsString(), value))
			return arg.isPassByReference() ? value : Duplicator.duplicate(value, true);
		throw new UDFCasterException(this, arg, value, index);
	}

	private final Object castTo(PageContext pc, FunctionArgument arg, Object value, int index) throws PageException {
		if (Decision.isCastableTo(pc, arg.getType(), arg.getTypeAsString(), value)) return value;
		throw new UDFCasterException(this, arg, value, index);
	}

	private void defineArguments(PageContextImpl pc, FunctionArgument[] funcArgs, Object[] args, Argument newArgs) throws PageException {
		// define argument scope
		boolean fns = pc.getFullNullSupport();
		Object _null = NullSupportHelper.NULL(fns);

		for (int i = 0; i < funcArgs.length; i++) {
			// argument defined
			if (args.length > i && (args[i] != null || fns)) {
				newArgs.setEL(funcArgs[i].getName(), castToAndClone(pc, funcArgs[i], args[i], i + 1, fns));
			}
			// argument not defined
			else {
				Object d = getDefaultValue(pc, i, _null);
				if (d == _null) {
					if (funcArgs[i].isRequired()) {
						throw new ExpressionException("The parameter [" + funcArgs[i].getName() + "] to function [" + getFunctionName() + "] is required but was not passed in.");
					}
					if (!fns) newArgs.setEL(funcArgs[i].getName(), Argument.NULL);
				}
				else {
					newArgs.setEL(funcArgs[i].getName(), castTo(pc, funcArgs[i], d, i + 1));
				}
			}
		}
		for (int i = funcArgs.length; i < args.length; i++) {
			newArgs.setEL(ArgumentIntKey.init(i + 1), args[i]);
		}
	}

	private void defineArguments(PageContextImpl pc, FunctionArgument[] funcArgs, Struct values, Argument newArgs) throws PageException {
		// argumentCollection
		Object coll = values.removeEL(KeyConstants._argumentCollection);
		if (coll != null) {
			coll = Caster.unwrap(coll, coll);
			// pure forward f( argumentCollection = arguments ): resolve the scope directly against
			// the declared arguments, no transport copy through values
			if (coll instanceof Argument && values.size() == 0) {
				defineArgumentsZeroCopy(pc, funcArgs, (Argument) coll, newArgs);
				return;
			}
			UDFUtil.argumentCollectionMerge(values, coll, funcArgs);
		}
		// print.out(values.size());
		Object value;
		Collection.Key name;
		boolean fns = pc.getFullNullSupport();
		Object _null = NullSupportHelper.NULL(fns);

		for (int i = 0; i < funcArgs.length; i++) {
			// argument defined
			name = funcArgs[i].getName();
			value = values.remove(name, _null);
			if (value != _null) {
				newArgs.set(name, castToAndClone(pc, funcArgs[i], value, i + 1, fns));
				continue;
			}
			value = values.remove(ArgumentIntKey.init(i + 1), _null);
			if (value != _null) {
				newArgs.set(name, castToAndClone(pc, funcArgs[i], value, i + 1, fns));
				continue;
			}

			// default argument or exception
			Object defaultValue = getDefaultValue(pc, i, _null);// funcArgs[i].getDefaultValue();
			if (defaultValue == _null) {
				if (funcArgs[i].isRequired()) {
					throw new ExpressionException("The parameter [" + funcArgs[i].getName() + "] to function [" + getFunctionName() + "] is required but was not passed in.");
				}
				if (!fns) newArgs.set(name, Argument.NULL);
			}
			else newArgs.set(name, castTo(pc, funcArgs[i], defaultValue, i + 1));
		}

		Iterator<Entry<Key, Object>> it = values.entryIterator();
		Entry<Key, Object> e;
		while (it.hasNext()) {
			e = it.next();
			newArgs.set(e.getKey(), e.getValue());
		}
	}

	/**
	 * resolves an Argument collection directly against the declared arguments — the pure forward shape
	 * f( argumentCollection = arguments ) — with two read-only passes over the collection and per-call
	 * staging arrays, writing only into newArgs. replicates the merge path exactly: iteration order
	 * decides collisions (a later int key strands under its own key, a later named key is dropped), a
	 * stranded or plain-numeric key fills its slot positionally only when the name lane misses, and
	 * leftovers drain in collection order after the declared slots
	 */
	private void defineArgumentsZeroCopy(PageContextImpl pc, FunctionArgument[] funcArgs, Argument argColl, Argument newArgs) throws PageException {
		boolean fns = pc.getFullNullSupport();
		Object _null = NullSupportHelper.NULL(fns);
		int len = funcArgs.length;

		// claim pass: name lane (named keys and int keys routed to their own index) plus positional
		// lane (collision strands and plain keys holding a canonical in-range position)
		Object[] staged = new Object[len];
		Key[] claimer = new Key[len];
		Key[] posKey = null;
		Object[] posVal = null;
		Iterator<Key> it = argColl.keyIterator();
		Key k;
		int slot;
		while (it.hasNext()) {
			k = it.next();
			slot = slotOf(funcArgs, k);
			if (slot != -1) {
				if (claimer[slot] == null) {
					claimer[slot] = k;
					staged[slot] = argColl.get(k, Argument.NULL);
					continue;
				}
				// only an int key routed to its own index strands; a named key (or an out-of-range
				// int key matched by name) that lost its slot is dropped, like the merge's
				// containsKey skip
				if (!(k instanceof ArgumentIntKey) || ((ArgumentIntKey) k).getIntKey() - 1 != slot) continue;
			}
			else {
				slot = positionalIndex(k, len);
				if (slot == -1) continue; // plain leftover, drains in the leftover pass
			}
			if (posKey == null) {
				posKey = new Key[len];
				posVal = new Object[len];
			}
			posKey[slot] = k;
			posVal[slot] = argColl.get(k, Argument.NULL);
		}

		// declared-order fill, same branch structure and observable insertion order as the merge path
		Key name;
		Object value;
		for (int i = 0; i < len; i++) {
			name = funcArgs[i].getName();
			value = claimer[i] != null ? staged[i] : _null;
			if (value != _null) {
				newArgs.set(name, castToAndClone(pc, funcArgs[i], value, i + 1, fns));
				continue;
			}
			if (posKey != null && posKey[i] != null) {
				value = posVal[i];
				if (value != _null) {
					newArgs.set(name, castToAndClone(pc, funcArgs[i], value, i + 1, fns));
					continue;
				}
			}

			// default argument or exception
			Object defaultValue = getDefaultValue(pc, i, _null);
			if (defaultValue == _null) {
				if (funcArgs[i].isRequired()) {
					throw new ExpressionException("The parameter [" + funcArgs[i].getName() + "] to function [" + getFunctionName() + "] is required but was not passed in.");
				}
				if (!fns) newArgs.set(name, Argument.NULL);
			}
			else newArgs.set(name, castTo(pc, funcArgs[i], defaultValue, i + 1));
		}

		// leftover pass: iteration order again — undeclared names, out-of-range int keys, and
		// positional-lane keys whose slot was filled through the name lane (when the name lane
		// missed, the fill consumed or discarded the positional key instead)
		it = argColl.keyIterator();
		while (it.hasNext()) {
			k = it.next();
			slot = slotOf(funcArgs, k);
			if (slot != -1) {
				if (k.equals(claimer[slot])) continue;
				if (!(k instanceof ArgumentIntKey) || ((ArgumentIntKey) k).getIntKey() - 1 != slot) continue;
			}
			else {
				slot = positionalIndex(k, len);
			}
			if (slot != -1 && (claimer[slot] == null || staged[slot] == _null)) continue;
			newArgs.set(k, argColl.get(k, Argument.NULL));
		}
	}

	private static int slotOf(FunctionArgument[] funcArgs, Key k) {
		if (k instanceof ArgumentIntKey) {
			int i = ((ArgumentIntKey) k).getIntKey() - 1;
			if (i >= 0 && i < funcArgs.length) return i;
			// an out-of-range int key can still match a declared argument by name
		}
		for (int i = 0; i < funcArgs.length; i++) {
			if (k.equals(funcArgs[i].getName())) return i;
		}
		return -1;
	}

	/**
	 * declared slot a plain key fills positionally: the merge path's int-key probe
	 * (values.remove(ArgumentIntKey.init(i + 1))) matches any key by hash equality, so a plain string
	 * key holding the canonical decimal of an in-range position — e.g. arguments[ 3 ] = "x" past the
	 * scope size stores the string key "3" — fills that slot when its name lane misses
	 */
	private static int positionalIndex(Key k, int len) {
		String str = k.getString();
		int l = str.length();
		if (l == 0 || l > 9) return -1;
		char c = str.charAt(0);
		if (c < '1' || c > '9') return -1;
		int v = c - '0';
		for (int i = 1; i < l; i++) {
			c = str.charAt(i);
			if (c < '0' || c > '9') return -1;
			v = v * 10 + (c - '0');
		}
		return v <= len ? v - 1 : -1;
	}

	public static Collection.Key toKey(Object obj) {
		if (obj == null) return null;
		if (obj instanceof Collection.Key) return (Collection.Key) obj;
		String str = Caster.toString(obj, null);
		if (str == null) return KeyImpl.init(obj.toString());
		return KeyImpl.init(str);
	}

	@Override
	public Object callWithNamedValues(PageContext pc, Struct values, boolean doIncludePath) throws PageException {
		return hasCachedWithin(pc) ? _callCachedWithin(pc, null, null, values, doIncludePath) : _call(pc, null, null, values, doIncludePath, null);
	}

	@Override
	public Object callWithNamedValues(PageContext pc, Collection.Key calledName, Struct values, boolean doIncludePath) throws PageException {
		return hasCachedWithin(pc) ? _callCachedWithin(pc, calledName, null, values, doIncludePath) : _call(pc, calledName, null, values, doIncludePath, null);
	}

	@Override
	public Object call(PageContext pc, Object[] args, boolean doIncludePath) throws PageException {
		return hasCachedWithin(pc) ? _callCachedWithin(pc, null, args, null, doIncludePath) : _call(pc, null, args, null, doIncludePath, null);
	}

	@Override
	public Object call(PageContext pc, Collection.Key calledName, Object[] args, boolean doIncludePath) throws PageException {
		return hasCachedWithin(pc) ? _callCachedWithin(pc, calledName, args, null, doIncludePath) : _call(pc, calledName, args, null, doIncludePath, null);
	}

	private boolean hasCachedWithin(PageContext pc) {
		return this.properties.getCachedWithin() != null || pc.getCachedWithin(Config.CACHEDWITHIN_FUNCTION) != null;
		// Maybe better return !StringUtil.isEmpty(this.properties.cachedWithin) ||
		// !StringUtil.isEmpty(pc.getCachedWithin(Config.CACHEDWITHIN_FUNCTION));
	}

	private Object getCachedWithin(PageContext pc) {
		if (this.properties.getCachedWithin() != null) return this.properties.getCachedWithin();
		return pc.getCachedWithin(Config.CACHEDWITHIN_FUNCTION);
	}

	private Object _callCachedWithin(PageContext pc, Collection.Key calledName, Object[] args, Struct values, boolean doIncludePath) throws PageException {
		PageContextImpl pci = (PageContextImpl) pc;

		Argument newArgs = pci.getScopeFactory().getArgumentInstance();
		if (args != null) defineArguments(pci, getFunctionArguments(), args, newArgs);
		else defineArguments(pci, getFunctionArguments(), values, newArgs);

		Object cachedWithin = getCachedWithin(pc);
		String cacheId = CacheHandlerCollectionImpl.createId(this, null, newArgs);
		CacheHandler cacheHandler = pc.getConfig().getCacheHandlerCollection(Config.CACHE_TYPE_FUNCTION, null).getInstanceMatchingObject(cachedWithin, null);

		if (cacheHandler instanceof CacheHandlerPro) {
			CacheItem cacheItem = ((CacheHandlerPro) cacheHandler).get(pc, cacheId, cachedWithin);
			if (cacheItem instanceof UDFCacheItem) {
				UDFCacheItem entry = (UDFCacheItem) cacheItem;
				try {
					pc.write(entry.output);
					return entry.returnValue;
				}
				catch (IOException e) {
					throw Caster.toPageException(e);
				}
				finally {
					if (newArgs != null) pci.getScopeFactory().recycle(pci, newArgs);
				}
			}
		}
		else if (cacheHandler != null) { // TODO this else block can be removed when all cache handlers implement CacheHandlerPro
			CacheItem cacheItem = cacheHandler.get(pc, cacheId);
			if (cacheItem instanceof UDFCacheItem) {
				UDFCacheItem entry = (UDFCacheItem) cacheItem;
				// if(entry.creationdate+properties.cachedWithin>=System.currentTimeMillis()) {
				try {
					pc.write(entry.output);
					return entry.returnValue;
				}
				catch (IOException e) {
					throw Caster.toPageException(e);
				}
				finally {
					if (newArgs != null) pci.getScopeFactory().recycle(pci, newArgs);
				}
			}
		}

		// cached item not found, process and cache result if needed
		long start = System.nanoTime();

		// execute the function
		BodyContent bc = pci.pushBody();

		try {
			Object rtn = _call(pci, calledName, args, values, doIncludePath, newArgs);

			if (cacheHandler != null) {
				String out = bc.getString();
				cacheHandler.set(pc, cacheId, cachedWithin, new UDFCacheItem(out, rtn, getFunctionName(), getSource(), System.nanoTime() - start));
			}
			return rtn;
		}
		finally {
			BodyContentUtil.flushAndPop(pc, bc);
		}
	}

	private Object _call(PageContext pc, Collection.Key calledName, Object[] args, Struct values, boolean doIncludePath, Argument newArgs) throws PageException {
		PageContextImpl pci = (PageContextImpl) pc;
		boolean existingNewArgs = newArgs != null;
		if (!existingNewArgs) newArgs = pci.getScopeFactory().getArgumentInstance();
		newArgs.setFunctionArgumentNames(properties.getArgumentsSet());
		LocalImpl newLocal = pci.getScopeFactory().getLocalInstance();

		Undefined undefined = pc.undefinedScope();
		Argument oldArgs = pc.argumentsScope();
		Local oldLocal = pc.localScope();
		Collection.Key oldCalledName = pci.getActiveUDFCalledName();

		pc.setFunctionScopes(newLocal, newArgs);
		pci.setActiveUDFCalledName(calledName);

		int oldCheckArgs = undefined.setMode((properties.getLocalMode() == null ? pc.getApplicationContext().getLocalMode() : properties.getLocalMode().intValue()));

		PageSource ps = null;
		PageSource psInc = null;
		try {
			ps = properties.getPageSource();
			if (doIncludePath) {
				psInc = ps;
				if (getOwnerComponent() != null) {
					psInc = ComponentUtil.getPageSource(getOwnerComponent());
					if (psInc == pci.getCurrentTemplatePageSource()) {
						psInc = null;
					}
				}
			}
			if (ps != null) pci.addPageSource(ps, psInc);
			pci.addUDF(this);

			//////////////////////////////////////////
			BodyContent bc = null;
			Boolean wasSilent = null;
			boolean bufferOutput = getBufferOutput(pci);
			if (!getOutput()) {
				if (bufferOutput) bc = pci.pushBody();
				else wasSilent = pc.setSilent() ? Boolean.TRUE : Boolean.FALSE;
			}

			UDF parent = null;
			if (ownerComponent != null) {
				parent = pci.getActiveUDF();
				pci.setActiveUDF(this);
			}
			Object returnValue = null;

			try {
				if (!existingNewArgs) {
					if (args != null) defineArguments(pci, getFunctionArguments(), args, newArgs);
					else defineArguments(pci, getFunctionArguments(), values, newArgs);
				}

				// Debugger frame support - capture scopes for external debuggers
				// Must be AFTER defineArguments so function arguments are accessible for conditional breakpoints
				if (((PageContextImpl) pc).getExecutionLogEnabled()) {
					pci.pushDebuggerFrame(newLocal, newArgs, pc.variablesScope(), ps, getFunctionName(), properties.getStartLine());
				}

				returnValue = implementation(pci);
				if (ownerComponent != null) pci.setActiveUDF(parent);
			}
			catch (Throwable t) {
				ExceptionUtil.rethrowIfNecessary(t);
				if (ownerComponent != null) pci.setActiveUDF(parent);
				if (!getOutput()) {
					if (bufferOutput) BodyContentUtil.flushAndPop(pc, bc);
					else if (!wasSilent) pc.unsetSilent();
				}
				// BodyContentUtil.flushAndPop(pc,bc);
				// LDEV-6282: notify before finally pops the UDF frame; marker dedups nested catches.
				PageException pe = Caster.toPageException(t);
				if (pci.getExecutionLogEnabled()) pci.debuggerNotifyThrow(pe);
				throw pe;
			}
			if (!getOutput()) {
				if (bufferOutput) BodyContentUtil.clearAndPop(pc, bc);
				else if (!wasSilent) pc.unsetSilent();
			}
			// BodyContentUtil.clearAndPop(pc,bc);

			if (returnValue == null && pci.getFullNullSupport()) return returnValue;
			if (properties.getReturnType() == CFTypes.TYPE_ANY || !pci.getTypeChecking()) return returnValue;
			if (Decision.isCastableTo(pc, properties.getReturnTypeAsString(), returnValue, false, false, -1)) return returnValue;
			UDFCasterException uce = new UDFCasterException(this, properties.getReturnTypeAsString(), returnValue);
			if (pci.getExecutionLogEnabled()) pci.debuggerNotifyThrow(uce);
			throw uce;

			// REALCAST return Caster.castTo(pageContext,returnType,returnValue,false);
			//////////////////////////////////////////

		}
		finally {
			if (ps != null) pc.removeLastPageSource(psInc != null);
			// Debugger frame support - pop before removeUDF to maintain consistency
			if (pci.getExecutionLogEnabled()) {
				pci.popDebuggerFrame();
			}
			pci.removeUDF();
			pci.setFunctionScopes(oldLocal, oldArgs);
			pci.setActiveUDFCalledName(oldCalledName);
			undefined.setMode(oldCheckArgs);
			pci.getScopeFactory().recycle(pci, newArgs);
			pci.getScopeFactory().recycle(pci, newLocal);
		}
	}

	@Override
	public DumpData toDumpData(PageContext pageContext, int maxlevel, DumpProperties dp) {
		return UDFUtil.toDumpData(pageContext, maxlevel, dp, this, UDFUtil.TYPE_UDF);
	}

	@Override
	public String getDisplayName() {
		return properties.getDisplayName();
	}

	@Override
	public String getHint() {
		return properties.getHint();
	}

	/*
	 * @Override public PageSource getPageSource() { return properties.pageSource; }
	 */

	@Override
	public String getSource() {
		return properties.getPageSource() != null ? properties.getPageSource().getDisplayPath() : "";
	}

	public Struct getMeta() {
		return properties.getMeta();
	}

	@Override
	public Struct getMetaData(PageContext pc) throws PageException {
		return ComponentUtil.getMetaData(pc, this, properties, null);
		// return getMetaData(pc, this);
	}

	@Override
	public Object getValue() {
		return this;
	}

	/**
	 * @param component the componentImpl to set
	 */
	@Override
	public void setOwnerComponent(Component component) {
		this.ownerComponent = component;
	}

	@Override
	public Component getOwnerComponent() {
		return ownerComponent;// +++
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(properties.getFunctionName());
		sb.append("(");
		int optCount = 0;
		FunctionArgument[] args = properties.getFunctionArguments();
		for (int i = 0; i < args.length; i++) {
			if (i > 0) sb.append(", ");
			if (!args[i].isRequired()) {
				sb.append("[");
				optCount++;
			}
			sb.append(args[i].getTypeAsString());
			sb.append(" ");
			sb.append(args[i].getName());
		}
		for (int i = 0; i < optCount; i++) {
			sb.append("]");
		}
		sb.append(")");
		return sb.toString();
	}

	@Override
	public Boolean getSecureJson() {
		return properties.getSecureJson();
	}

	@Override
	public Boolean getVerifyClient() {
		return properties.getVerifyClient();
	}

	@Override
	public Object clone() {
		return duplicate();
	}

	@Override
	public FunctionArgument[] getFunctionArguments() {
		return properties.getFunctionArguments();
	}

	@Override
	public Object getDefaultValue(PageContext pc, int index) throws PageException {
		return getDefaultValue(pc, index, null);
	}

	@Override
	public Object getDefaultValue(PageContext pc, int index, Object defaultValue) throws PageException {
		return properties.getPage(pc).udfDefaultValue(pc, properties.getIndex(), index, defaultValue);
	}

	// public abstract Object getDefaultValue(PageContext pc,int index) throws PageException;

	@Override
	public String getFunctionName() {
		return properties.getFunctionName();
	}

	@Override
	public boolean getOutput() {
		return properties.getOutput();
	}

	public Boolean getBufferOutput() {
		return properties.getBufferOutput();
	}

	@Override
	public boolean getBufferOutput(PageContext pc) {
		if (properties.getBufferOutput() != null) return properties.getBufferOutput().booleanValue();
		return (pc.getApplicationContext()).getBufferOutput();
	}

	@Override
	public int getReturnType() {
		return properties.getReturnType();
	}

	@Override
	public String getReturnTypeAsString() {
		return properties.getReturnTypeAsString();
	}

	@Override
	public String getDescription() {
		return properties.getDescription();
	}

	@Override
	public int getReturnFormat() {
		int format = properties.getReturnFormat(null);
		if (format < 0) {
			PageContext pc = ThreadLocalPageContext.get();
			if (pc != null) {
				ApplicationContextSupport acs = (ApplicationContextSupport) pc.getApplicationContext();
				if (acs != null) return acs.getReturnFormat();
			}
			return UDF.RETURN_FORMAT_WDDX;
		}
		return format;
	}

	@Override
	public int getReturnFormat(int defaultValue) {
		int format = properties.getReturnFormat(null);
		if (format < 0) return defaultValue;
		return format;
	}

	public final String getReturnFormatAsString() {
		return UDFUtil.toReturnFormat(getReturnFormat(), "wddx");
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		// access
		setAccess(in.readInt());

		// properties
		properties = (UDFPropertiesBase) in.readObject();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		// access
		out.writeInt(getAccess());

		// properties
		out.writeObject(properties);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UDF)) return false;
		return equals(this, (UDF) obj);
	}

	public static boolean equals(UDF left, UDF right) {
		// print.e(left.getFunctionName()+":"+right.getFunctionName());
		if (!left.id().equals(right.id()) || !_eq(left.getFunctionName(), right.getFunctionName()) || left.getAccess() != right.getAccess()
				|| !_eq(left.getFunctionName(), right.getFunctionName()) || left.getOutput() != right.getOutput() || left.getReturnFormat() != right.getReturnFormat()
				|| left.getReturnType() != right.getReturnType() || !_eq(left.getReturnTypeAsString(), right.getReturnTypeAsString())
				|| !_eq(left.getSecureJson(), right.getSecureJson()) || !_eq(left.getVerifyClient(), right.getVerifyClient()))
			return false;

		// Arguments
		FunctionArgument[] largs = left.getFunctionArguments();
		FunctionArgument[] rargs = right.getFunctionArguments();
		if (largs.length != rargs.length) return false;
		for (int i = 0; i < largs.length; i++) {
			if (!largs[i].equals(rargs[i])) return false;
		}

		return true;
	}

	private static boolean _eq(Object left, Object right) {
		if (left == null) return right == null;
		return left.equals(right);
	}

	@Override
	public int getIndex() {
		return properties.getIndex();
	}

	@Override
	public String id() {
		return properties.id();
	}

	@Override
	public PageSource getPageSource() {
		return this.properties.getPageSource();
	}

	@Override
	public String getCacheId(Collection arguments, String defaultValue) {
		if (arguments instanceof Struct) {
			return CacheHandlerCollectionImpl.createId(this, null, (Struct) arguments);
		}
		if (Decision.isCastableToArray(arguments)) {
			Object[] arr = Caster.toNativeArray(arguments, null);
			if (arr != null) return CacheHandlerCollectionImpl.createId(this, arr, null);
		}
		return defaultValue;
	}

	@Override
	public int getCachetype() {
		return Config.CACHE_TYPE_FUNCTION;
	}

}
