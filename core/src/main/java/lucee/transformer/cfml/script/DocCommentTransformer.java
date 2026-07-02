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
package lucee.transformer.cfml.script;

import lucee.commons.lang.ExceptionUtil;
import lucee.commons.lang.StringUtil;
import lucee.transformer.Factory;
import lucee.transformer.expression.Expression;
import lucee.transformer.expression.literal.LitBoolean;
import lucee.transformer.statement.tag.Attribute;
import lucee.transformer.util.SourceCode;

public final class DocCommentTransformer {

	// transient state during a single transform() call — this class is single-threaded per parser Data.
	private SourceCode sc;
	private int pos;
	private int endPos;

	/**
	 * Range-based transform. Reads the doc comment directly from {@code sc.text[]} starting at {@code start}
	 * (the position of the leading {@code /} in {@code /**}) — no String/ParserString/char[] intermediate allocs.
	 * Scans forward internally for the closing {@code *&#47;} (guaranteed present by {@code multiLineComment}).
	 */
	public DocComment transform(Factory f, SourceCode sc, int start) {
		try {
			// find closing */ — guaranteed present since multiLineComment validated closure before us
			int p = start + 2;
			while (!(sc.charAt(p) == '*' && sc.charAt(p + 1) == '/')) p++;
			int end = p + 2;

			this.sc     = sc;
			this.pos    = start + 3;   // skip /**
			this.endPos = end - 2;     // stop before */
			// outer trim: leading + trailing whitespace within the /** ... */ range
			while (this.pos < this.endPos && sc.charAt(this.pos) <= ' ') this.pos++;
			while (this.endPos > this.pos && sc.charAt(this.endPos - 1) <= ' ') this.endPos--;

			DocComment dc = new DocComment();
			walk(f, dc);
			dc.getHint();// TODO do different -> make sure internal structure is valid
			return dc;
		}
		catch (Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			return null;
		}
		finally {
			this.sc = null;
		}
	}

	private void walk(Factory factory, DocComment dc) {
		while (pos < endPos) {
			asterix();
			removeSpace();
			if (pos < endPos && sc.charAt(pos) == '@') {
				pos++;
				dc.addParam(param(factory));
			}
			else {
				while (pos < endPos && sc.charAt(pos) != '\n') {
					dc.addHint(sc.charAt(pos));
					pos++;
				}
				dc.addHint('\n');
			}
			removeSpace();
		}
	}

	private Attribute param(Factory factory) {
		String name = paramName();
		if (name == null) return new Attribute(true, "@", factory.TRUE(), "boolean");

		// white space (stop early on newline — bare `@name\n` means boolean true)
		while (pos < endPos && sc.charAt(pos) <= ' ') {
			if (sc.charAt(pos) == '\n') return new Attribute(true, name, factory.TRUE(), "boolean");
			pos++;
		}
		Expression value = paramValue(factory);
		return new Attribute(true, name, value, value instanceof LitBoolean ? "boolean" : "string");
	}

	private String paramName() {
		int start = pos;
		while (pos < endPos && sc.charAt(pos) > ' ') pos++;
		if (pos == start) return null;
		return sc.substring(start, pos - start);
	}

	private Expression paramValue(Factory factory) {
		int start = pos;
		while (pos < endPos && sc.charAt(pos) != '\n') pos++;
		if (pos == start) return factory.TRUE();
		return factory.createLitString(StringUtil.unwrap(sc.substring(start, pos - start)));
	}

	private void asterix() {
		while (pos < endPos) {
			removeSpace();
			if (pos < endPos && sc.charAt(pos) == '*') pos++;
			else break;
		}
	}

	private void removeSpace() {
		while (pos < endPos && sc.charAt(pos) <= ' ') pos++;
	}

}
