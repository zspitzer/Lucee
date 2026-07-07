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
package lucee.transformer.expression.var;

import lucee.transformer.TransformerException;
import lucee.transformer.bytecode.BytecodeContext;
import lucee.transformer.expression.Expression;
import org.objectweb.asm.Type;

public interface Member {

	public void setParent(Variable var);

	public Variable getParent();

	public void setSafeNavigated(boolean b);

	public boolean getSafeNavigated();

	public void setSafeNavigatedValue(Expression obj);

	public Expression getSafeNavigatedValue();

	/**
	 * Emit this member's ASM ops. Return null to signal "not migrated, fall back to emitGeneral".
	 * Position is writer-owned: {@code i} is the member's index in the chain, {@code count} is the total.
	 */
	@SuppressWarnings("unused")
	default Type emit(Variable v, BytecodeContext bc, int i, int count, int mode, Boolean asCollection) throws TransformerException {
		return null;
	}
}