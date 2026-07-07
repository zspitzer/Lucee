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

import lucee.runtime.db.ClassDefinition;
import lucee.transformer.Factory;
import lucee.transformer.TransformerException;
import lucee.transformer.bytecode.BytecodeContext;
import lucee.transformer.cfml.Data;
import lucee.transformer.cfml.TransfomerSettings;
import lucee.transformer.expression.ExprString;
import lucee.transformer.expression.var.Variable;
import lucee.transformer.library.function.FunctionLibFunction;

public final class BIF extends FunctionMember {

	private static String ANY = "any";

	// private ExprString nameq;
	private int argType;
	private ClassDefinition cd;
	private String returnType = ANY;
	private FunctionLibFunction flf;

	private final Factory factory;

	public final TransfomerSettings ts;

	private Data data;

	public BIF(Factory factory, TransfomerSettings ts, FunctionLibFunction flf, Data data) {
		this.ts = ts;
		// this.name=name;
		this.flf = flf;
		this.factory = factory;// name.getFactory();
		this.data = data;
	}

	public Factory getFactory() {
		return factory;
	}

	public Data getData() {
		return data;
	}

	public void setArgType(int argType) {
		this.argType = argType;
	}

	public void setClassDefinition(ClassDefinition cd) {
		this.cd = cd;
	}

	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}

	/**
	 * @return the argType
	 */
	public int getArgType() {
		return argType;
	}

	/**
	 * @return the class
	 */
	public ClassDefinition getClassDefinition() {
		return cd;
	}

	/**
	 * @return the name
	 * 
	 *         public ExprString getNameX() { return name; }
	 */

	/**
	 * @return the returnType
	 */
	public String getReturnType() {
		return returnType;
	}

	/**
	 * @return the flf
	 */
	public FunctionLibFunction getFlf() {
		return flf;
	}

	/**
	 * @param flf the flf to set
	 */
	public void setFlf(FunctionLibFunction flf) {
		this.flf = flf;
	}

	@Override
	public ExprString getName() {
		return factory.createLitString(flf.getName());
	}

	@Override
	public Type emit(Variable v, BytecodeContext bc, int i, int count, int mode, Boolean asCollection) throws TransformerException {
		if (getSafeNavigated()) return null;
		if (count == 1) {
			return VariableImpl._writeOutFirstBIF(bc, this, mode, true, ((VariableImpl) v).getStart());
		}
		if (i == 0) {
			// Head BIF in multi-member chain — writer preloaded PCs, we delegate to the legacy head emitter with last=false.
			return VariableImpl._writeOutFirstBIF(bc, this, mode, false, ((VariableImpl) v).getStart());
		}
		return null;   // mid/tail BIF stays on emitGeneral fallback (genuinely rare)
	}
}