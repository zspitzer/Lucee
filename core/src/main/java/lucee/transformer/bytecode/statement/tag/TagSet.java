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
package lucee.transformer.bytecode.statement.tag;

import lucee.transformer.Factory;
import lucee.transformer.Position;
import lucee.transformer.TransformerException;
import lucee.transformer.bytecode.BytecodeContext;
import lucee.transformer.bytecode.util.ASMUtil;
import lucee.transformer.expression.Expression;

import org.objectweb.asm.Type;

public final class TagSet extends TagBaseNoFinal  {

	public TagSet(Factory f, Position start,Position end) {
		super(f,start,end);
	}

	@Override
	public void _writeOut(BytecodeContext bc) throws TransformerException {
		Type rtn = getAttribute("noname").getValue().writeOut(bc, Expression.MODE_VALUE);
		// TODO sollte nicht auch long geprueft werden? id:470 gh:480 ic:gh
		ASMUtil.pop(bc.getAdapter(), rtn);
		//if(rtn.equals(Types.DOUBLE_VALUE))bc.getAdapter().pop2();
		//else bc.getAdapter().pop();
	}

}