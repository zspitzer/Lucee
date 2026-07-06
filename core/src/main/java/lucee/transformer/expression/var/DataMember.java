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

public interface DataMember extends NamedMember {

	// Trailing dot-key classification: set once at construction from the name.
	// Non-LitString names, bracket-access names, and names that don't match one of the three Query
	// polymorphic props all resolve to QP_NONE. VariableImpl only cares about the trailing member's
	// value — this field is computed for every DataMember so no member-position check is needed.
	byte QP_NONE = 0;
	byte QP_RECORDCOUNT = 1;
	byte QP_CURRENTROW = 2;
	byte QP_COLUMNLIST = 3;

	byte getReservedProp();
}