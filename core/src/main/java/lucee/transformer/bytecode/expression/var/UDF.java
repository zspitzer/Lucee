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
package lucee.transformer.bytecode.expression.var;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import lucee.runtime.type.scope.Scope;
import lucee.transformer.Page;
import lucee.transformer.TransformerException;
import lucee.transformer.bytecode.BytecodeContext;
import lucee.transformer.bytecode.util.ASMConstants;
import lucee.transformer.bytecode.util.ExpressionUtil;
import lucee.transformer.bytecode.util.TypeScope;
import lucee.transformer.bytecode.util.Types;
import lucee.transformer.expression.ExprString;
import lucee.transformer.expression.Expression;
import lucee.transformer.expression.var.Argument;
import lucee.transformer.expression.var.Variable;

public final class UDF extends FunctionMember {

	private ExprString name;

	public UDF(Expression name) {
		this.name = name.getFactory().toExprString(name);
	}

	public UDF(Page page, String name) {
		this.name = page.getFactory().createLitString(name);
	}

	/**
	 * @return the name
	 */
	public ExprString getName() {
		return name;
	}

	@Override
	public Type emit(Variable v, BytecodeContext bc, int i, int count, int mode, Boolean asCollection) throws TransformerException {
		int scope = v.getScope();
		if (scope < 0 || scope >= TypeScope.SCOPES.length || TypeScope.SCOPES[scope] == null) return null;

		GeneratorAdapter adapter = bc.getAdapter();
		boolean doOnlyScope = scope == Scope.SCOPE_LOCAL;

		if (count == 1) {
			if (getSafeNavigated()) return null;   // solo safe-nav UDF is parser-impossible; keep as guard
			// Preload 1 PC for LOCAL, 0 otherwise.
			if (doOnlyScope) adapter.loadArg(0);
			adapter.loadArg(0);
			if (!doOnlyScope) adapter.loadArg(0);
			TypeScope.invokeScope(adapter, scope);
			return emitCall(bc, adapter);
		}

		if (i == 0) {
			if (getSafeNavigated()) return null;   // head safe-nav is parser-impossible
			// Head UDF in multi-member chain — writer preloaded PCs for subsequent gets; we do scope-resolve + call.
			adapter.loadArg(0);
			if (!doOnlyScope) adapter.loadArg(0);
			TypeScope.invokeScope(adapter, scope);
			return emitCall(bc, adapter);
		}

		// Mid-chain or trailing UDF (i > 0). Writer already preloaded PC (with checkCast to PAGE_CONTEXT_IMPL if we're safe-nav).
		return emitCall(bc, adapter);
	}

	private Type emitCall(BytecodeContext bc, GeneratorAdapter adapter) throws TransformerException {
		bc.getFactory().registerKey(bc, name, false);
		Argument[] args = getArguments();
		if (args.length == 0) {
			adapter.getStatic(Types.CONSTANTS, "EMPTY_OBJECT_ARRAY", Types.OBJECT_ARRAY);
		}
		else {
			ExpressionUtil.writeOutExpressionArray(bc, Types.OBJECT, args);
		}
		if (getSafeNavigated()) {
			int type;
			Expression val = getSafeNavigatedValue();
			if (val == null) {
				ASMConstants.NULL(adapter);
				type = VariableImpl.THREE;
			}
			else {
				val.writeOut(bc, Expression.MODE_REF);
				type = VariableImpl.THREE2;
			}
			adapter.invokeVirtual(Types.PAGE_CONTEXT_IMPL, hasNamedArgs() ? VariableImpl.GET_FUNCTION_WITH_NAMED_ARGS[type] : VariableImpl.GET_FUNCTION[type]);
		}
		else {
			adapter.invokeVirtual(Types.PAGE_CONTEXT, hasNamedArgs() ? VariableImpl.GET_FUNCTION_WITH_NAMED_ARGS[VariableImpl.TWO] : VariableImpl.GET_FUNCTION[VariableImpl.TWO]);
		}
		return Types.OBJECT;
	}
}