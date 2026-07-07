/**
 * Copyright (c) 2015, Lucee Association Switzerland. All rights reserved.
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
package lucee.transformer.bytecode.expression.var;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import lucee.runtime.type.scope.Scope;
import lucee.transformer.TransformerException;
import lucee.transformer.bytecode.BytecodeContext;
import lucee.transformer.bytecode.util.ASMConstants;
import lucee.transformer.bytecode.util.TypeScope;
import lucee.transformer.bytecode.util.Types;
import lucee.transformer.expression.ExprString;
import lucee.transformer.expression.Expression;
import lucee.transformer.expression.literal.LitString;
import lucee.transformer.expression.var.DataMember;
import lucee.transformer.expression.var.Variable;

public final class DataMemberImpl implements DataMember {
	private ExprString name;
	private Variable parent;
	private boolean safeNavigated;
	private Expression safeNavigatedValue;
	private final byte reservedProp;

	public DataMemberImpl(ExprString name) {
		this.name = name;
		this.reservedProp = classifyReservedProp(name);
	}

	private static byte classifyReservedProp(ExprString name) {
		if (!(name instanceof LitString)) return QP_NONE;
		LitString ls = (LitString) name;
		if (ls.fromBracket()) return QP_NONE;
		int len = ls.getString().length();
		if (len == 10) {
			if (ls.equalsLowerAscii("currentrow")) return QP_CURRENTROW;
			if (ls.equalsLowerAscii("columnlist")) return QP_COLUMNLIST;
		}
		else if (len == 11 && ls.equalsLowerAscii("recordcount")) return QP_RECORDCOUNT;
		return QP_NONE;
	}

	@Override
	public byte getReservedProp() {
		return reservedProp;
	}

	public void setParent(Variable parent) {
		this.parent = parent;
	}

	public Variable getParent() {
		return parent;
	}

	@Override
	public ExprString getName() {
		return name;
	}

	@Override
	public void setSafeNavigated(boolean safeNavigated) {
		this.safeNavigated = safeNavigated;
	}

	@Override
	public boolean getSafeNavigated() {
		return this.safeNavigated;
	}

	@Override
	public void setSafeNavigatedValue(Expression safeNavigatedValue) {
		this.safeNavigatedValue = safeNavigatedValue;
	}

	@Override
	public Expression getSafeNavigatedValue() {
		return safeNavigatedValue;
	}

	@Override
	public Type emit(Variable v, BytecodeContext bc, int i, int count, int mode, Boolean asCollection) throws TransformerException {
		int scope = v.getScope();
		GeneratorAdapter adapter = bc.getAdapter();

		// Solo (count==1)
		if (count == 1) {
			if (safeNavigated || reservedProp != QP_NONE) return null;
			// Compact solo (UNDEFINED / VARIABLES / LOCAL) — single fused method call.
			if (scope == Scope.SCOPE_UNDEFINED || scope == Scope.SCOPE_VARIABLES || scope == Scope.SCOPE_LOCAL) {
				adapter.loadArg(0);
				adapter.checkCast(Types.PAGE_CONTEXT_IMPL);
				v.getFactory().registerKey(bc, name, false);
				adapter.invokeVirtual(Types.PAGE_CONTEXT_IMPL, VariableImpl.GET_KEYS[scope][0]);
				return Types.OBJECT;
			}
			// Non-compact solo — scope-resolve then interface get.
			if (scope < 0 || scope >= TypeScope.SCOPES.length || TypeScope.SCOPES[scope] == null) return null;
			adapter.loadArg(0);
			TypeScope.invokeScope(adapter, scope);
			v.getFactory().registerKey(bc, name, false);
			adapter.invokeInterface(TypeScope.SCOPES[scope], VariableImpl.METHOD_SCOPE_GET_KEY);
			return Types.OBJECT;
		}

		// Multi-member position — writer preloaded PCs and called us in a loop.
		boolean last = (i + 1) == count;
		if (last && reservedProp != QP_NONE) return null;

		if (i == 0) {
			// Head: safe-nav can't be set on the first token (parser only marks it via preceding `?.`).
			if (safeNavigated) return null;
			boolean doOnlyScope = scope == Scope.SCOPE_LOCAL;
			adapter.loadArg(0);
			TypeScope.invokeScope(adapter, scope);
			v.getFactory().registerKey(bc, name, false);
			if (doOnlyScope) {
				adapter.invokeVirtual(Types.PAGE_CONTEXT, VariableImpl.asCollection(asCollection, last) ? VariableImpl.GET_COLLECTION[VariableImpl.TWO] : VariableImpl.GET[VariableImpl.TWO]);
			}
			else {
				boolean _last = scope == Scope.SCOPE_UNDEFINED;   // n>=2 → this member is never truly last
				adapter.invokeInterface(TypeScope.SCOPES[scope], _last ? VariableImpl.METHOD_SCOPE_GET_COLLECTION_KEY : VariableImpl.METHOD_SCOPE_GET_KEY);
			}
			return Types.OBJECT;
		}

		// Continuation (i > 0) — supports safe-nav.
		v.getFactory().registerKey(bc, name, false);
		if (safeNavigated) {
			Expression val = safeNavigatedValue;
			if (val == null) ASMConstants.NULL(adapter);
			else val.writeOut(bc, Expression.MODE_REF);
			adapter.invokeVirtual(Types.PAGE_CONTEXT, VariableImpl.asCollection(asCollection, last) ? VariableImpl.GET_COLLECTION[VariableImpl.THREE] : VariableImpl.GET[VariableImpl.THREE]);
		}
		else {
			adapter.invokeVirtual(Types.PAGE_CONTEXT, VariableImpl.asCollection(asCollection, last) ? VariableImpl.GET_COLLECTION[VariableImpl.TWO] : VariableImpl.GET[VariableImpl.TWO]);
		}
		return Types.OBJECT;
	}
}