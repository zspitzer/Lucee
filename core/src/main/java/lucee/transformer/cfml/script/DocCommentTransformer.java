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
	 * Range-based transform. Reads the doc comment directly from the {@code SourceCode} char buffer
	 * starting at {@code start} (the position of the leading {@code /} in {@code /**}).
	 * Zero String/ParserString/char[] intermediate allocs; walks the range with charClass hops
	 * via {@link SourceCode#nextNewline}, {@link SourceCode#nextWhitespace}, and
	 * {@link SourceCode#skipWhitespace}. End of comment is recovered from the one-write annotation
	 * that {@code multiLineComment} + {@code annotateComment} left at {@code charClass[start]}.
	 */
	public DocComment transform(Factory f, SourceCode sc, int start) {
		// Recover end via annotation: charClass[start] = -(end - start). One hop past the whole range.
		int savedPos = sc.getPos();
		sc.setPos(start);
		sc.removeSpace();
		int end = sc.getPos();
		sc.setPos(savedPos);

		try {
			this.sc = sc;
			this.endPos = end - 2;                                 // stop before */
			this.pos = sc.skipWhitespace(start + 3, this.endPos);  // skip /** and any leading ws
			while (this.endPos > this.pos && sc.isWhiteSpaceAt(this.endPos - 1)) this.endPos--;

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
			pos = sc.skipWhitespace(pos, endPos);
			if (sc.isCharAt(pos, '@')) {
				pos++;
				dc.addParam(param(factory));
			}
			else {
				int lineEnd = sc.nextNewline(pos, endPos);
				dc.addHint(sc, pos, lineEnd);
				dc.addHint('\n');
				pos = lineEnd < endPos ? lineEnd + 1 : endPos;
			}
			pos = sc.skipWhitespace(pos, endPos);
		}
	}

	private Attribute param(Factory factory) {
		String name = paramName();
		if (name == null) return new Attribute(true, "@", factory.TRUE(), "boolean");

		// whitespace with early-exit on newline: bare `@name\n` means boolean true
		int wsEnd = sc.skipWhitespace(pos, endPos);
		int nl = sc.nextNewline(pos, wsEnd);
		if (nl < wsEnd) {
			pos = nl;
			return new Attribute(true, name, factory.TRUE(), "boolean");
		}
		pos = wsEnd;
		Expression value = paramValue(factory);
		return new Attribute(true, name, value, value instanceof LitBoolean ? "boolean" : "string");
	}

	private String paramName() {
		int start = pos;
		pos = sc.nextWhitespace(start, endPos);
		if (pos == start) return null;
		return sc.substring(start, pos - start);
	}

	private Expression paramValue(Factory factory) {
		int start = pos;
		pos = sc.nextNewline(start, endPos);
		if (pos == start) return factory.TRUE();
		return factory.createLitString(StringUtil.unwrap(sc.substring(start, pos - start)));
	}

	private void asterix() {
		while (pos < endPos) {
			pos = sc.skipWhitespace(pos, endPos);
			if (!sc.isCharAt(pos, '*')) break;
			pos++;
		}
	}

}
