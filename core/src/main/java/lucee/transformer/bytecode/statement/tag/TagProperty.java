/**
 * Copyright (c) 2025, Lucee Association Switzerland. All rights reserved.
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

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import lucee.transformer.Factory;
import lucee.transformer.Position;
import lucee.transformer.TransformerException;
import lucee.transformer.bytecode.BytecodeContext;
import lucee.transformer.bytecode.statement.FlowControlFinal;
import lucee.transformer.bytecode.util.Types;
import lucee.transformer.expression.Expression;
import lucee.transformer.statement.tag.Attribute;
import lucee.transformer.statement.tag.Tag;

/**
 * Optimized bytecode generation for cfproperty tags. Bypasses tag lifecycle overhead by calling
 * ComponentUtil.registerProperty directly.
 */
public final class TagProperty extends TagBase {

	public TagProperty(Factory f, Position start, Position end) {
		super(f, start, end);
	}

	@Override
	public FlowControlFinal getFlowControlFinal() {
		return null;
	}

	@Override
	public void _writeOut(BytecodeContext bc) throws TransformerException {
		// Property metadata registration now happens in static initializer (<clinit>)
		// See PageImpl.writeOutStatic() for static property registration
		// However, complex default values (arrays, structs, expressions) need per-instance evaluation
		Tag tag = this;
		bc.visitLine(tag.getStart());

		Attribute defaultAttr = tag.getAttribute("default");
		Attribute nameAttr = tag.getAttribute("name");

		// Only handle complex defaults - simple literals are already handled in CLINIT
		if (defaultAttr != null && nameAttr != null && defaultAttr.getValue() != null) {
			Expression defaultExpr = defaultAttr.getValue();
			String propName = getLiteralString(nameAttr);

			// Check if it's a complex expression (not a simple literal)
			boolean isComplex = !isSimpleLiteral(defaultExpr);

			if (isComplex && propName != null) {
				final GeneratorAdapter adapter = bc.getAdapter();

				// Evaluate the default expression with the current PageContext
				defaultExpr.writeOut(bc, Expression.MODE_REF);
				int defaultLocal = adapter.newLocal(Types.OBJECT);
				adapter.storeLocal(defaultLocal);

				// Get PageContext from arg0 and set the value in variables scope
				// pc.variablesScope().setEL(KeyImpl.init(propName), defaultValue)
				adapter.loadArg(0); // Load PageContext pc
				adapter.invokeVirtual(Types.PAGE_CONTEXT, new Method("variablesScope", Types.VARIABLES, new Type[] {}));
				adapter.push(propName);
				adapter.invokeStatic(Type.getType("Llucee/runtime/type/KeyImpl;"), new Method("init", Types.COLLECTION_KEY, new Type[] { Types.STRING }));
				adapter.loadLocal(defaultLocal);
				adapter.invokeInterface(Types.SCOPE, new Method("setEL", Types.OBJECT, new Type[] { Types.COLLECTION_KEY, Types.OBJECT }));
				adapter.pop(); // Pop return value
			}
		}

		bc.visitLine(tag.getEnd());
	}

	private boolean isSimpleLiteral(Expression expr) {
		String className = expr.getClass().getSimpleName();
		return className.equals("LitStringImpl") || className.equals("LitNumberImpl") ||
		       className.equals("LitBooleanImpl") || className.equals("LitIntegerImpl") ||
		       className.equals("LitLongImpl");
	}

	private String getLiteralString(Attribute attr) {
		try {
			if (attr != null && attr.getValue() != null) {
				Expression expr = attr.getValue();
				// Check if it's a LitStringImpl and extract the string value
				if (expr.getClass().getSimpleName().equals("LitStringImpl")) {
					// Use reflection to call getString() since we're using simple name matching
					java.lang.reflect.Method method = expr.getClass().getMethod("getString");
					return (String) method.invoke(expr);
				}
				// Fall back to toString for other types (may not be accurate)
				return expr.toString();
			}
		}
		catch (Exception e) {
			// Fall back to null if we can't get literal value
		}
		return null;
	}

}
