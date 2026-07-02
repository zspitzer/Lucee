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
package lucee.transformer.util;

import java.util.Arrays;

import lucee.commons.digest.HashUtil;
import lucee.commons.io.SystemUtil;
import lucee.runtime.exp.TemplateException;
import lucee.transformer.Position;

/**
 * this class is a Parser String optimized for the transfomer (CFML Parser)
 */
public class SourceCode {

	public static final short AT_LEAST_ONE_SPACE = 0;
	public static final short ZERO_OR_MORE_SPACE = 1;

	private static final short CC_LETTER      = 1; // a-z, A-Z, _, $, €, £
	private static final short CC_DIGIT       = 2; // 0-9
	private static final short CC_QUOTE       = 4; // " or '
	/** Backward comment-end marker: written at {@code commentEnd - 1} by {@link #annotateBlockComment}.
	 *  Enables lazy reverse scans for doc comments — {@link #findPrecedingComment} walks backward from
	 *  a consumer position past whitespace, and if it lands on a position with this kind bit, the run
	 *  bits (bits 4-14) carry {@code reverseOffset - 1} back to {@code commentStart}. Capped at
	 *  {@link #CC_MAX_RUN}; long-comment overflow falls back to scanning charClass for the forward marker. */
	private static final short CC_COMMENT_END = 8;

	// charClass encoding: short[], (runLength << CC_RUN_SHIFT) | kind
	// kind occupies bits 0-3; run-length occupies bits 4-14.
	// bit 15 is the sign bit: negative = whitespace or annotated comment, 0 = operator, positive = var-char/quote.
	private static final int  CC_RUN_SHIFT   = 4;
	private static final int  CC_MAX_RUN     = Short.MAX_VALUE >> CC_RUN_SHIFT; // 2047 — effectively uncapped
	private static final int  CC_SPACE_MAX   = Short.MAX_VALUE; // 32767 — one hop covers any whitespace/comment run

	protected int pos = 0;
	protected int currentLine = 1; // Track current line number (1-based)

	protected final char[] text;
	/** Lowercased Latin-1 projection of text[0..len). Storing as byte[] halves memory vs char[].
	 *  Reads must widen via {@code lcText[pos] & 0xFF} so chars 128-255 come back as 0-255 (not sign-extended).
	 *  Source chars > 255 (e.g. €) get truncated on write; safe because the parser only compares lcText
	 *  against ASCII chars, and identifier bodies are extracted from text[] (never lcText[]). */
	protected final byte[] lcText;
	/** valid length across text/lcText/charClass. May be less than text.length when the
	 *  buffer was decoded directly from an oversized IO read. */
	protected final int len;

	/**
	 * Per-character classification array, built once in the constructor alongside lcText[].
	 *
	 * Each byte encodes the character class of the corresponding lcText position:
	 *
	 *   negative  — whitespace or annotated comment: value is -(distance to end), capped at -CC_SPACE_MAX.
	 *               removeSpace() reads this and jumps pos forward in one array load.
	 *               Runs longer than CC_SPACE_MAX encode as -CC_SPACE_MAX; removeSpace() falls back to a loop.
	 *               annotateComment() writes the same encoding so removeSpace() skips comment ranges too.
	 *
	 *   0         — operator / punctuation: non-space, non-var, unclassified char.
	 *
	 *   positive  — var-char or quote, packed as (runLength << CC_RUN_SHIFT) | kind:
	 *               bits 0-3: kind — CC_LETTER(1), CC_DIGIT(2), CC_QUOTE(4), CC_COMMENT_END(8)
	 *                         CC_LETTER covers a-z, A-Z, _, $, €, £
	 *               bits 4-6: contiguous var-char run length, capped at CC_MAX_RUN (bit 7 must stay 0).
	 *                         forwardVarCharRun() extracts with >> CC_RUN_SHIFT and adds to pos,
	 *                         skipping an entire identifier body in one array load + add.
	 *                         CC_QUOTE entries always have run-length 0.
	 *
	 * Why this rocks: the common parser operations — skip whitespace, check char class,
	 * hop over identifiers — all become a single array load plus a mask or add.
	 * No per-character branching on lcText[], no Character.toLowerCase() call per char,
	 * no inner loops inside removeSpace() or forwardVarCharRun() for the common case.
	 */
	protected final short[] charClass;
	protected final int[] lines;
	private final boolean writeLog;
	private int hash;
	private SourceCode parent;
	private boolean scriptMode;


	// Position cache - reduces allocations during parsing
	private Position cachedPosition;
	private int cachedPos = -1;

	public SourceCode(SourceCode parent, String strText, boolean writeLog) {
		this.parent = parent;
		this.text = strText.toCharArray();
		this.len = this.text.length;
		lcText = new byte[len];
		charClass = new short[len];
		this.lines = buildLcTextAndCharClass();
		this.writeLog = writeLog;
	}

	/**
	 * char[]-direct constructor: skips the {@code String -> char[]} copy that {@link String#toCharArray()}
	 * incurs. {@code textBuf} may be oversized (e.g. from a growable IO read); if so, it is trimmed to
	 * {@code validLen} so the class invariant {@code text.length == valid content length} always holds
	 * (the transformer relies on that everywhere).
	 */
	public SourceCode(SourceCode parent, char[] textBuf, int validLen, boolean writeLog) {
		this.parent = parent;
		this.text = textBuf.length == validLen ? textBuf : Arrays.copyOf(textBuf, validLen);
		this.len = validLen;
		lcText = new byte[validLen];
		charClass = new short[validLen];
		this.lines = buildLcTextAndCharClass();
		this.writeLog = writeLog;
	}

	private int[] buildLcTextAndCharClass() {
		// single backward pass: builds lcText[], charClass[], lines[], and hash together.
		// lines[] comes out in descending order and is reversed at the end.
		// hash is Horner-from-the-right: h += text[i] * 31^(n-1-i) — algebraically
		// identical to String.hashCode()'s forward 31*h + a[i] under Java int overflow.
		// only A-Z need lowercasing (ASCII source) — bit-flip avoids Character.toLowerCase().
		int[] arr = new int[32];
		int count = 0;
		int nextNonSpace = len;
		int nextNonVar   = len;
		int h = 0;
		int pow = 1;

		for (int i = len - 1; i >= 0; i--) {
			char raw = text[i];
			h += raw * pow;
			pow *= 31;
			char lc;
			if (raw == '\n') {
				lc = ' ';
				if (count == arr.length) arr = Arrays.copyOf(arr, arr.length * 2);
				arr[count++] = i;
			}
			else if (raw == '\r') {
				lc = ' ';
				// only record lone \r — \r\n pairs are already recorded via the \n
				if (i + 1 >= len || text[i + 1] != '\n') {
					if (count == arr.length) arr = Arrays.copyOf(arr, arr.length * 2);
					arr[count++] = i;
				}
			}
			else if (raw == '\t') {
				lc = ' ';
			}
			else if (raw >= 'A' && raw <= 'Z') {
				lc = (char)(raw | 0x20);
			}
			else {
				lc = raw;
			}
			lcText[i] = (byte) lc;

			if (lc == ' ') {
				int dist = nextNonSpace - i;
				charClass[i] = (short)(dist > CC_SPACE_MAX ? -CC_SPACE_MAX : -dist);
				nextNonVar = i;
			}
			else {
				nextNonSpace = i;
				byte kind;
				if      (lc >= 'a' && lc <= 'z')                                                                           kind = CC_LETTER;
				else if (lc >= '0' && lc <= '9')                                                                           kind = CC_DIGIT;
				else if (lc == '_' || lc == '$' || lc == SystemUtil.CHAR_EURO || lc == SystemUtil.CHAR_POUND) kind = CC_LETTER;
				else if (lc == '"' || lc == '\'')                                                                          kind = CC_QUOTE;
				else                                                                                                       kind = 0;
				if (kind == CC_LETTER || kind == CC_DIGIT) {
					int runLen = nextNonVar - i;
					charClass[i] = (short)(((runLen > CC_MAX_RUN ? CC_MAX_RUN : runLen) << CC_RUN_SHIFT) | kind);
				}
				else {
					charClass[i] = kind;
					nextNonVar = i;
				}
			}
		}

		this.hash = h;

		// reverse the descending line positions to ascending, then append sentinel
		for (int l = 0, r = count - 1; l < r; l++, r--) {
			int tmp = arr[l]; arr[l] = arr[r]; arr[r] = tmp;
		}
		if (count == arr.length) arr = Arrays.copyOf(arr, arr.length + 1);
		arr[count++] = len;
		return Arrays.copyOf(arr, count);
	}

	public SourceCode getParent() {
		return parent;
	}

	public boolean hasPrevious() {
		return pos > 0;
	}

	/**
	 * returns if the internal pointer is not on the last positions
	 */
	public boolean hasNext() {
		return pos + 1 < len;
	}

	public boolean hasNextNext() {
		return pos + 2 < len;
	}

	/**
	 * moves the internal pointer to the next position, no check if the next position is still valid
	 */
	public void next() {
		pos++;
	}

	/**
	 * moves the internal pointer to the previous position, no check if the next position is still valid
	 */
	public void previous() {
		pos--;
	}

	/**
	 * returns the character of the current position of the internal pointer
	 */
	public char getCurrent() {
		return text[pos];
	}

	/**
	 * returns the lower case representation of the character of the current position
	 */
	public char getCurrentLower() {
		return (char)(lcText[pos] & 0xFF);
	}

	/**
	 * returns the character at the given position
	 */
	public char charAt(int pos) {
		return text[pos];
	}

	/**
	 * returns the character at the given position as lower case representation
	 */
	public char charAtLower(int pos) {
		return (char)(lcText[pos] & 0xFF);
	}

	public boolean isPrevious(char c) {
		if (!hasPrevious()) return false;
		return (lcText[pos - 1] & 0xFF) == c;
	}

	public boolean isPreviousIgnoreSpace(char c) {
		int start = pos;
		try {
			while (isPrevious(' ')) {
				pos--;
			}
			if (!hasPrevious()) return false;
			return charAt(getPos() - 1) == c;
		}
		finally {
			pos = start;
		}
	}

	/**
	 * is the character at the next position the same as the character provided by the input parameter
	 */
	public boolean isNext(char c) {
		if (!hasNext()) return false;
		return (lcText[pos + 1] & 0xFF) == c;
	}

	public boolean isNext(char a, char b) {
		if (!hasNextNext()) return false;
		return (lcText[pos + 1] & 0xFF) == a && (lcText[pos + 2] & 0xFF) == b;
	}

	/**
	 * is the character at the current position (internal pointer) in the range of the given input
	 * characters?
	 * 
	 * @param left lower value.
	 * @param right upper value.
	 */
	public boolean isCurrentBetween(char left, char right) {
		if (pos >= len) return false;
		int c = lcText[pos] & 0xFF;
		return c >= left && c <= right;
	}

	/**
	 * returns if the character at the current position (internal pointer) is a valid variable character
	 */
	public boolean isCurrentVariableCharacter() {
		if (pos >= len) return false;
		short v = charClass[pos];
		return v > 0 && (v & 3) != 0;
	}

	/**
	 * returns if the current character is a letter (a-z,A-Z)
	 *
	 * @return is a letter
	 */
	public boolean isCurrentLetter() {
		if (pos >= len) return false;
		short v = charClass[pos];
		return v > 0 && (v & 0xF) == CC_LETTER;
	}

	/**
	 * returns if the current character is a number (0-9)
	 *
	 * @return is a number
	 */
	public boolean isCurrentNumber() {
		if (pos >= len) return false;
		short v = charClass[pos];
		return v > 0 && (v & 0xF) == CC_DIGIT;
	}

	public boolean isCurrentQuote() {
		if (pos >= len) return false;
		short v = charClass[pos];
		return v > 0 && (v & 0xF) == CC_QUOTE;
	}

	public boolean isCurrentHash() {
		return pos < len && (lcText[pos] & 0xFF) == '#';
	}

	public boolean isCurrentOperatorChar() {
		return pos < len && charClass[pos] == 0;
	}

	/**
	 * Advances pos past the contiguous var-char run starting at the current position.
	 * Safe to call only when isCurrentLetter() is true.
	 */
	public void forwardVarCharRun() {
		while (pos < len) {
			int hop = charClass[pos] >> CC_RUN_SHIFT;
			if (hop <= 0) break;
			pos += hop;
		}
	}

	/**
	 * Hops the var-char run at the current position, or advances by one if not on a var-char.
	 * Use as the inner-loop body when scanning for a sentinel that cannot appear in identifier runs.
	 */
	public void forwardVarCharRunOrNext() {
		int hop = charClass[pos] >> CC_RUN_SHIFT;
		if (hop > 0) pos += hop;
		else pos++;
	}

	/**
	 * is the current character (internal pointer) the same as the given
	 */
	public boolean isCurrent(char c) {
		if (!isValidIndex()) return false;
		return (lcText[pos] & 0xFF) == c;
	}

	/**
	 * forward the internal pointer plus one if the next character is the same as the given input
	 */
	public boolean forwardIfCurrent(char c) {
		if (isCurrent(c)) {
			pos++;
			return true;
		}
		return false;
	}

	/**
	 * returns if the current character (internal pointer) and the following are the same as the given
	 * input
	 */
	public boolean isCurrent(String str) {
		if (pos + str.length() > len) return false;
		for (int i = str.length() - 1; i >= 0; i--) {
			if (str.charAt(i) != (lcText[pos + i] & 0xFF)) return false;
		}
		return true;
	}

	/**
	 * forwards if the current character (internal pointer) and the following are the same as the given
	 * input
	 */
	public boolean forwardIfCurrent(String str) {
		boolean is = isCurrent(str);
		if (is) pos += str.length();
		return is;
	}

	public boolean isCurrentKeyword(String str) {
		return isCurrent(str);
	}

	public boolean forwardIfCurrentKeyword(String str) {
		if (!isCurrentKeyword(str)) return false;
		pos += str.length();
		return true;
	}

	/**
	 * @param str string to check against current position
	 * @param startWithSpace if true there must be whitespace at the current position
	 * @return does the criteria match?
	 */
	public boolean forwardIfCurrent(String str, boolean startWithSpace) {
		if (!startWithSpace) return forwardIfCurrent(str);

		int start = pos;
		if (!removeSpace()) return false;

		if (!forwardIfCurrent(str)) {
			pos = start;
			return false;
		}
		return true;
	}

	/**
	 * @param str string to check against current position
	 * @param startWithSpace if true there must be whitespace at the current position
	 * @param followedByNoVariableCharacter the character following the string must be a none variable
	 *            character (!a-z,A-Z,0-9,_$) (not eaten)
	 * @return does the criteria match?
	 */
	public boolean forwardIfCurrent(String str, boolean startWithSpace, boolean followedByNoVariableCharacter) {

		int start = pos;
		if (startWithSpace && !removeSpace()) return false;

		// run-length pre-check: if a word boundary is required, the var-char run at pos must be
		// exactly str.length() — longer means the identifier continues past str (word-boundary fails),
		// shorter or negative means str can't fully match. One array load replaces both the string
		// compare and the followedByNoVariableCharacter check for the common non-match case.
		if (followedByNoVariableCharacter && (pos >= len || (charClass[pos] >> CC_RUN_SHIFT) != str.length())) {
			pos = start;
			return false;
		}

		if (!forwardIfCurrentKeyword(str)) {
			pos = start;
			return false;
		}
		if (followedByNoVariableCharacter && isCurrentVariableCharacter()) {
			pos = start;
			return false;
		}
		return true;
	}

	/**
	 * forwards if the current character (internal pointer) and the following are the same as the given
	 * input, followed by a none word character
	 */
	public boolean forwardIfCurrentAndNoWordAfter(String str) {
		if (pos >= len || (charClass[pos] >> CC_RUN_SHIFT) != str.length()) return false;
		int c = pos;
		if (forwardIfCurrentKeyword(str)) {
			if (!isCurrentLetter() && !isCurrent('_')) return true;
		}
		pos = c;
		return false;
	}

	/**
	 * forwards if the current character (internal pointer) and the following are the same as the given
	 * input, followed by a none word character or a number
	 */
	public boolean forwardIfCurrentAndNoVarExt(String str) {
		int c = pos;
		if (forwardIfCurrentKeyword(str)) {
			if (!isCurrentVariableCharacter()) return true;
		}
		pos = c;
		return false;
	}

	/**
	 * Gibt zurueck ob first den folgenden Zeichen entspricht, gefolgt von Leerzeichen und second.
	 * 
	 * @param first Erste Zeichen zum Vergleich (Vor den Leerzeichen).
	 * @param second Zweite Zeichen zum Vergleich (Nach den Leerzeichen).
	 * @return Gibt zurueck ob die eingegebenen Werte dem Inhalt beim aktuellen Stand des Zeigers
	 *         entsprechen.
	 */
	public boolean isCurrent(String first, char second) {
		int start = pos;
		if (!forwardIfCurrent(first)) return false;
		removeSpace();
		boolean rtn = isCurrent(second);
		pos = start;
		return rtn;
	}

	/**
	 * Gibt zurueck ob first den folgenden Zeichen entspricht, gefolgt von Leerzeichen und second.
	 * 
	 * @param first Erstes Zeichen zum Vergleich (Vor den Leerzeichen).
	 * @param second Zweites Zeichen zum Vergleich (Nach den Leerzeichen).
	 * @return Gibt zurueck ob die eingegebenen Werte dem Inhalt beim aktuellen Stand des Zeigers
	 *         entsprechen.
	 */
	public boolean isCurrent(char first, char second) {
		int start = pos;
		if (!forwardIfCurrent(first)) return false;
		removeSpace();
		boolean rtn = isCurrent(second);
		pos = start;
		return rtn;
	}

	/**
	 * Gibt zurueck ob first den folgenden Zeichen entspricht, gefolgt von Leerzeichen und second, wenn
	 * ja wird der Zeiger um die Laenge der uebereinstimmung nach vorne gestellt.
	 * 
	 * @param first Erste Zeichen zum Vergleich (Vor den Leerzeichen).
	 * @param second Zweite Zeichen zum Vergleich (Nach den Leerzeichen).
	 * @return Gibt zurueck ob der Zeiger vorwaerts geschoben wurde oder nicht.
	 */
	public boolean forwardIfCurrent(String first, char second) {
		int start = pos;
		if (!forwardIfCurrentKeyword(first)) return false;
		removeSpace();
		boolean rtn = forwardIfCurrent(second);
		if (!rtn) pos = start;
		return rtn;
	}

	/**
	 * Gibt zurueck ob ein Wert folgt und vor und hinterher Leerzeichen folgen.
	 * 
	 * @param before Definition der Leerzeichen vorher.
	 * @param val Gefolgter Wert der erartet wird.
	 * @param after Definition der Leerzeichen nach dem Wert.
	 * @return Gibt zurueck ob der Zeiger vorwaerts geschoben wurde oder nicht.
	 */
	public boolean forwardIfCurrent(short before, String val, short after) {
		int start = pos;
		// space before
		if (before == AT_LEAST_ONE_SPACE) {
			if (!removeSpace()) return false;
		}
		else removeSpace();

		// value
		if (!forwardIfCurrent(val)) {
			setPos(start);
			return false;
		}

		// space after
		if (after == AT_LEAST_ONE_SPACE) {
			if (!removeSpace()) {
				setPos(start);
				return false;
			}
		}
		else removeSpace();
		return true;
	}

	/**
	 * Gibt zurueck ob first den folgenden Zeichen entspricht, gefolgt von Leerzeichen und second, wenn
	 * ja wird der Zeiger um die Laenge der uebereinstimmung nach vorne gestellt.
	 * 
	 * @param first Erste Zeichen zum Vergleich (Vor den Leerzeichen).
	 * @param second Zweite Zeichen zum Vergleich (Nach den Leerzeichen).
	 * @return Gibt zurueck ob der Zeiger vorwaerts geschoben wurde oder nicht.
	 */
	public boolean forwardIfCurrent(char first, char second) {
		int start = pos;
		if (!forwardIfCurrent(first)) return false;
		removeSpace();
		boolean rtn = forwardIfCurrent(second);
		if (!rtn) pos = start;
		return rtn;
	}

	/**
	 * Gibt zurueck ob first den folgenden Zeichen entspricht, gefolgt von Leerzeichen und second.
	 * 
	 * @param first Erste Zeichen zum Vergleich (Vor den Leerzeichen).
	 * @param second Zweite Zeichen zum Vergleich (Nach den Leerzeichen).
	 * @return Gibt zurueck ob die eingegebenen Werte dem Inhalt beim aktuellen Stand des Zeigers
	 *         entsprechen.
	 */
	public boolean isCurrent(String first, String second) {
		int start = pos;
		if (!forwardIfCurrent(first)) return false;
		removeSpace();
		boolean rtn = isCurrent(second);
		pos = start;
		return rtn;
	}

	/**
	 * Gibt zurueck ob first den folgenden Zeichen entspricht, gefolgt von Leerzeichen und second, wenn
	 * ja wird der Zeiger um die Laenge der uebereinstimmung nach vorne gestellt.
	 * 
	 * @param first Erste Zeichen zum Vergleich (Vor den Leerzeichen).
	 * @param second Zweite Zeichen zum Vergleich (Nach den Leerzeichen).
	 * @return Gibt zurueck ob der Zeiger vorwaerts geschoben wurde oder nicht.
	 */
	public boolean forwardIfCurrent(String first, String second) {
		int start = pos;
		if (!forwardIfCurrent(first)) return false;
		if (!removeSpace()) {
			pos = start;
			return false;
		}
		boolean rtn = forwardIfCurrent(second);
		if (!rtn) pos = start;
		return rtn;
	}

	public boolean forwardIfCurrent(String first, String second, String third) {
		int start = pos;
		if (!forwardIfCurrent(first)) return false;

		if (!removeSpace()) {
			pos = start;
			return false;
		}

		if (!forwardIfCurrent(second)) {
			pos = start;
			return false;
		}

		if (!removeSpace()) {
			pos = start;
			return false;
		}

		boolean rtn = forwardIfCurrent(third);
		if (!rtn) pos = start;
		return rtn;
	}

	public boolean forwardIfCurrent(String first, String second, String third, boolean startWithSpace) {
		if (!startWithSpace) return forwardIfCurrent(first, second, third);
		int start = pos;

		if (!removeSpace()) return false;

		if (!forwardIfCurrent(first, second, third)) {
			pos = start;
			return false;
		}
		return true;
	}

	public boolean forwardIfCurrent(String first, String second, String third, boolean startWithSpace, boolean followedByNoVariableCharacter) {
		int start = pos;

		if (startWithSpace && !removeSpace()) return false;

		if (!forwardIfCurrent(first, second, third)) {
			pos = start;
			return false;
		}
		if (followedByNoVariableCharacter && isCurrentVariableCharacter()) {
			pos = start;
			return false;
		}
		return true;
	}

	public boolean forwardIfCurrent(String first, String second, boolean startWithSpace, boolean followedByNoVariableCharacter) {
		int start = pos;

		if (startWithSpace && !removeSpace()) return false;

		if (!forwardIfCurrent(first, second)) {
			pos = start;
			return false;
		}
		if (followedByNoVariableCharacter && isCurrentVariableCharacter()) {
			pos = start;
			return false;
		}
		return true;
	}

	public boolean forwardIfCurrent(String first, String second, String third, String forth) {
		int start = pos;
		if (!forwardIfCurrent(first)) return false;

		if (!removeSpace()) {
			pos = start;
			return false;
		}

		if (!forwardIfCurrent(second)) {
			pos = start;
			return false;
		}

		if (!removeSpace()) {
			pos = start;
			return false;
		}

		if (!forwardIfCurrent(third)) {
			pos = start;
			return false;
		}

		if (!removeSpace()) {
			pos = start;
			return false;
		}

		boolean rtn = forwardIfCurrent(forth);
		if (!rtn) pos = start;
		return rtn;

	}

	/**
	 * Gibt zurueck ob sich vor dem aktuellen Zeichen Leerzeichen befinden.
	 * 
	 * @return Gibt zurueck ob sich vor dem aktuellen Zeichen Leerzeichen befinden.
	 */
	public boolean hasSpaceBefore() {
		return pos > 0 && (lcText[pos - 1] & 0xFF) == ' ';
	}

	public boolean hasNLBefore() {
		int index = 0;
		while (pos - (++index) >= 0) {
			if (text[pos - index] == '\n') return true;
			if (text[pos - index] == '\r') return true;
			if ((lcText[pos - index] & 0xFF) != ' ') return false;
		}
		return false;
	}

	/**
	 * Stellt den Zeiger nach vorne, wenn er sich innerhalb von Leerzeichen befindet, bis die
	 * Leerzeichen fertig sind.
	 *
	 * @return Gibt zurueck ob der Zeiger innerhalb von Leerzeichen war oder nicht.
	 */
	public boolean removeSpace() {
		if (pos >= len) return false;
		short v = charClass[pos];
		if (v >= 0) return false;
		pos -= v;
		return true;
	}

	/**
	 * Positional variant of {@link #nextLine()}: find the next literal {@code '\n'} in
	 * {@code [from, end)}. Var-char runs (identifier bodies) provably contain no newline —
	 * hopped in one array load via the charClass encoding. Returns {@code end} if not found.
	 * Does not mutate {@code pos}.
	 */
	public int nextNewline(int from, int end) {
		int p = from;
		while (p < end) {
			int hop = charClass[p] >> CC_RUN_SHIFT;
			if (hop > 0) { p += hop; continue; }
			if (text[p] == '\n') return p;
			p++;
		}
		return end;
	}

	/**
	 * Find first whitespace-or-annotated position ({@code charClass < 0}) in {@code [from, end)}.
	 * Var-char runs are hopped; operator/punctuation chars step by 1. Returns {@code end} if the
	 * range contains no whitespace. Does not mutate {@code pos}.
	 */
	public int nextWhitespace(int from, int end) {
		int p = from;
		while (p < end) {
			short cc = charClass[p];
			if (cc < 0) return p;
			if (cc > 0) { p += cc >> CC_RUN_SHIFT; continue; }
			p++;
		}
		return end;
	}

	/**
	 * Positional variant of {@link #removeSpace()}: advance past whitespace via the negative-distance
	 * hop encoding. Returns the first non-whitespace position in {@code [from, end)}, or {@code end}
	 * if the range is entirely whitespace. Does not mutate {@code pos}.
	 */
	public int skipWhitespace(int from, int end) {
		int p = from;
		while (p < end) {
			short cc = charClass[p];
			if (cc >= 0) return p;
			p -= cc;
		}
		return end;
	}

	/** Positional whitespace predicate — {@code true} when {@code charClass[p] < 0}. */
	public boolean isWhiteSpaceAt(int p) {
		return p < len && charClass[p] < 0;
	}

	/**
	 * Positional char predicate — case-sensitive, reads raw {@code text[]}. Matches the
	 * semantic of {@link #charAt(int)}. If a case-insensitive positional peek is ever needed,
	 * add {@code isCharAtLower} as its own thing rather than overloading this one.
	 */
	public boolean isCharAt(int p, char c) {
		return p < len && text[p] == c;
	}

	/**
	 * Skip whitespace and return the lcText byte at the resulting pos, or -1 at EOF.
	 * Combines removeSpace() with a peek so callers doing "skip ws, then decide on next char"
	 * avoid a second bounds check + array load. Multiple whitespace runs (e.g. across
	 * annotated comment ranges) are all consumed in one call.
	 */
	public int skipSpaceReturnCurrent() {
		while (pos < len) {
			short v = charClass[pos];
			if (v >= 0) return lcText[pos] & 0xFF;
			pos -= v;
		}
		return -1;
	}


	/**
	 * Retroactively marks a consumed comment range as skippable whitespace via a single
	 * charClass write at {@code start}. Interior positions are left untouched — the parser
	 * never lands inside a consumed comment (forwardIfCurrent advances past the opener on
	 * success; on failure pos stays before it), so only the pre-comment hop entry point
	 * needs the negative distance-to-end. removeSpace() reads at pos and jumps past the
	 * whole range in one load; the interior's natural var-char / operator / whitespace
	 * encoding is preserved for anyone (e.g. DocCommentTransformer) walking the range later.
	 */
	public void annotateComment(int start, int end) {
		int dist = end - start;
		charClass[start] = (short)(dist > CC_SPACE_MAX ? -CC_SPACE_MAX : -dist);
	}

	/**
	 * Same forward marker as {@link #annotateComment}, plus a backward marker at {@code end - 1}
	 * so {@link #findPrecedingComment} can locate the comment's start from a consumer position
	 * downstream. Called by {@link #multiLineComment} to enable lazy doc-comment discovery.
	 * The backward marker stashes {@code reverseOffset - 1} (= {@code end - 2 - start}) in the
	 * run bits under {@link #CC_COMMENT_END}, capped at {@link #CC_MAX_RUN}.
	 */
	public void annotateBlockComment(int start, int end) {
		int dist = end - start;
		charClass[start] = (short)(dist > CC_SPACE_MAX ? -CC_SPACE_MAX : -dist);
		int rev = dist - 2;   // reverseOffset - 1 = (end - 1 - start) - 1 = end - start - 2
		int stashed = rev > CC_MAX_RUN ? CC_MAX_RUN : rev;
		charClass[end - 1] = (short)((stashed << CC_RUN_SHIFT) | CC_COMMENT_END);
	}

	/**
	 * Consume {@code //} through the end of line and annotate the range as skippable
	 * whitespace. <b>Caller contract:</b> {@code pos} sits on the opening {@code //} — verified
	 * by {@link #skipSpaceAndComments} via b2 dispatch. No self-verification here.
	 */
	public void singleLineComment() {
		int start = pos;
		pos += 2;
		nextLine();
		annotateComment(start, pos);
	}

	/**
	 * Consume {@code /*} through the closing {@code *&#47;} and annotate the range with
	 * forward + backward markers via {@link #annotateBlockComment}. <b>Caller contract:</b>
	 * {@code pos} sits on the opening {@code /*} — verified by {@link #skipSpaceAndComments}
	 * via b2 dispatch. No self-verification here. Throws {@link TemplateException} on
	 * unclosed block comment.
	 *
	 * <p>Doc-comment ({@code /** *&#47;}) discovery is now a consumer-side concern:
	 * {@code materializeDocComment} calls {@link #findPrecedingComment} which walks backward
	 * from the consumer position, uses the {@link #CC_COMMENT_END} marker to locate
	 * {@code commentStart}, and checks for the third {@code *}.
	 */
	public void multiLineComment() throws TemplateException {
		int commentStart = pos;
		int p = pos + 2;
		while (true) {
			p = indexOfNext(p, '*');
			if (p < 0 || p + 1 >= len) {
				pos = commentStart + 2;
				throw new TemplateException(this, "block comment is not closed");
			}
			if (text[p + 1] == '/') {
				pos = p + 2;
				annotateBlockComment(commentStart, pos);
				return;
			}
			p++;
		}
	}

	/**
	 * Consume a CFML tag comment {@code <!--- ... --->} at the current position. Nested
	 * {@code <!---}/{@code --->} pairs are tracked via a counter. On successful consumption,
	 * greedily eats any immediately-following tag comment (recursive) and annotates the
	 * entire consumed range so future {@link #removeSpace()} calls hop past it in one load.
	 *
	 * @return true if a tag comment was consumed, false if the current position is not '<!---'
	 */
	public boolean tagComment() throws TemplateException {
		int commentStart = pos;
		if (!forwardIfCurrent("<!---")) return false;

		int start = pos;
		short counter = 1;
		while (true) {
			if (isAfterLast()) {
				setPos(start);
				throw new TemplateException(this, "no end comment found");
			}
			else if (forwardIfCurrent("<!---")) {
				counter++;
			}
			else if (forwardIfCurrent("--->")) {
				if (--counter == 0) {
					tagComment();
					annotateComment(commentStart, pos);
					return true;
				}
			}
			else {
				forwardVarCharRunOrNext();
			}
		}
	}

	/**
	 * Walks backward from {@code fromPos - 1} past whitespace looking for the immediately-preceding
	 * block comment. Uses the {@link #CC_COMMENT_END} marker written by {@link #annotateBlockComment}.
	 *
	 * <p>Fast path: reads the marker's stashed reverse-offset and recovers {@code commentStart},
	 * cross-checked against the forward whitespace marker at that position.
	 *
	 * <p>Slow path (block comments longer than {@code CC_MAX_RUN + 1} chars, where the reverse-offset
	 * overflows the run bits): scans {@code charClass} backward looking for the forward marker whose
	 * negative-distance matches {@code commentEnd - i}. Natural whitespace annotations point to
	 * {@code commentStart} (not {@code commentEnd}), so the forward marker is uniquely identifiable.
	 *
	 * @return position of the {@code /} opener of the preceding block comment, or {@code -1} if
	 *         nothing immediately before {@code fromPos} (skipping whitespace) is a block-comment end.
	 */
	public int findPrecedingComment(int fromPos) {
		int p = fromPos - 1;
		while (p >= 0 && (lcText[p] & 0xFF) == ' ') p--;
		if (p < 0) return -1;
		short cc = charClass[p];
		if (cc <= 0 || (cc & 0xF) != CC_COMMENT_END) return -1;

		int stashed = cc >> CC_RUN_SHIFT;
		int commentEnd = p + 1;
		int putative = p - (stashed + 1);
		if (putative >= 0 && charClass[putative] < 0 && -charClass[putative] == commentEnd - putative) {
			return putative;
		}

		// slow path: reverse-offset overflowed CC_MAX_RUN — scan backward for the forward marker
		for (int i = p - 1; i >= 0; i--) {
			short v = charClass[i];
			if (v < 0 && -v == commentEnd - i) return i;
		}
		return -1;
	}

	/**
	 * Fast-path token-boundary primitive: skip whitespace, then consume any run of comments
	 * ({@code //}, {@code /*}, {@code <!--- --->}) interleaved with more whitespace. Direct
	 * field access on {@code charClass}/{@code lcText} — no {@code data.srcCode.*} indirection
	 * for the 160 transformer call sites this replaces.
	 *
	 * <p>Two-char opener dispatch: peeks {@code b} (first non-ws char) and {@code b2}
	 * ({@code lcText[pos+1]}) up front. '/' dispatches to single/multi by {@code b2}; '<' pre-gates
	 * on {@code b2 == '!'} so the 99% of '<' sightings in tag-mode CFML that open a real tag
	 * exit in three byte compares without calling {@link #tagComment()}. Single/multi callees no
	 * longer verify their opener — caller has done so.
	 */
	public void skipSpaceAndComments() throws TemplateException {
		while (true) {
			int b = skipSpaceReturnCurrent();
			if (b != '/' && b != '<') return;
			if (pos + 1 >= len) return;
			int b2 = lcText[pos + 1] & 0xFF;
			if (b == '/') {
				if (b2 == '/') { singleLineComment(); continue; }
				if (b2 == '*') { multiLineComment(); continue; }
				return;
			}
			// b == '<' — b2 == '!' is the rare positive; most '<' in tag-mode CFML are tag opens
			if (b2 == '!' && tagComment()) continue;
			return;
		}
	}

	public void revertRemoveSpace() {
		while (hasSpaceBefore()) {
			previous();
		}
	}

	public String removeAndGetSpace() {
		int start = pos;
		while (pos < len && (lcText[pos] & 0xFF) == ' ') {
			pos++;
		}
		return substring(start, pos - start);
	}

	/**
	 * Stellt den internen Zeiger an den Anfang der naechsten Zeile, gibt zurueck ob eine weitere Zeile
	 * existiert oder ob es bereits die letzte Zeile war.
	 * 
	 * @return Existiert eine weitere Zeile.
	 */
	public boolean nextLine() {
		while (pos < len) {
			int hop = charClass[pos] >> CC_RUN_SHIFT;
			if (hop > 0) { pos += hop; continue; }
			if (text[pos] == '\n' || text[pos] == '\r') break;
			pos++;
		}
		if (!isValidIndex()) return false;

		if (text[pos] == '\n') {
			next();
			return isValidIndex();
		}
		if (text[pos] == '\r') {
			next();
			if (isValidIndex() && text[pos] == '\n') {
				next();
			}
			return isValidIndex();
		}
		return false;
	}

	/**
	 * Gibt eine Untermenge des CFMLString als Zeichenkette zurueck, ausgehend von start bis zum Ende
	 * des CFMLString.
	 * 
	 * @param start Von wo aus die Untermege ausgegeben werden soll.
	 * @return Untermenge als Zeichenkette
	 */
	public String substring(int start) {
		return substring(start, len - start);
	}

	/**
	 * Gibt eine Untermenge des CFMLString als Zeichenkette zurueck, ausgehend von start mit einer
	 * maximalen Laenge count.
	 * 
	 * @param start Von wo aus die Untermenge ausgegeben werden soll.
	 * @param count Wie lange die zurueckgegebene Zeichenkette maximal sein darf.
	 * @return Untermenge als Zeichenkette.
	 */
	public String substring(int start, int count) {
		return String.valueOf(text, start, count);
	}

	/**
	 * Gibt eine Untermenge des CFMLString als Zeichenkette in Kleinbuchstaben zurueck, ausgehend von
	 * start bis zum Ende des CFMLString.
	 * 
	 * @param start Von wo aus die Untermenge ausgegeben werden soll.
	 * @return Untermenge als Zeichenkette in Kleinbuchstaben.
	 */
	public String substringLower(int start) {
		return substringLower(start, len - start);
	}

	/**
	 * Gibt eine Untermenge des CFMLString als Zeichenkette in Kleinbuchstaben zurueck, ausgehend von
	 * start mit einer maximalen Laenge count.
	 * 
	 * @param start Von wo aus die Untermenge ausgegeben werden soll.
	 * @param count Wie lange die zurueckgegebene Zeichenkette maximal sein darf.
	 * @return Untermenge als Zeichenkette in Kleinbuchstaben.
	 */
	public String substringLower(int start, int count) {
		// ISO_8859_1 maps bytes 0-255 1:1 to chars 0-255 — the exact widening lcText needs.
		// Source chars > 255 (rare) were truncated on write; substringLower is not on the compile hot path.
		return new String(lcText, start, count, java.nio.charset.StandardCharsets.ISO_8859_1);
	}

	/**
	 * Case-insensitive equality between the source range {@code lcText[start..start+length)} and
	 * a lowercase literal. Zero-alloc alternative to {@code substring(start, length).equalsIgnoreCase(lower)}.
	 * {@code lower} must already be lowercase — this method does no case folding on it.
	 */
	public boolean equalsLowerAt(int start, int length, String lower) {
		if (length != lower.length()) return false;
		for (int i = 0; i < length; i++) {
			if ((lcText[start + i] & 0xFF) != lower.charAt(i)) return false;
		}
		return true;
	}

	/**
	 * Zero-alloc case-insensitive search for an identifier of an exact length,
	 * preceded by {@code lowerBefore} and followed by {@code lowerAfter}.
	 *
	 * The scan pivots on {@code charClass} run lengths: for each var-char run, one array load
	 * gives the run length. Runs of the wrong length are hopped in a single add — {@code lcText}
	 * is never touched for them. Only length-matching runs get the leading/trailing byte check
	 * and, if that passes, the identifier byte-compare.
	 *
	 * All three arguments must already be lowercase — this method does no case folding.
	 * {@code lowerIdent} must be composed entirely of var-chars (a-z, 0-9, _, $) — the run-length
	 * pivot only fires inside identifier runs.
	 */
	/**
	 * True if the source contains {@code <name} or {@code </name} for any of the given tag names.
	 * Used to decide whether a .cfc file needs wrapping in {@code <cfscript>} — script-syntax CFCs
	 * have no tag markers so scanning replaces two full-file case-insensitive substring searches.
	 * charClass run-length lets us skip identifier runs of the wrong length in one array load.
	 * Tag names must be lowercase and composed entirely of var-chars.
	 */
	public boolean findTag(String... lowerTagNames) {
		int n = lowerTagNames.length;
		if (n == 0) return false;
		int i = 0;
		while (i < len) {
			short cc = charClass[i];
			if (cc > 0) {
				int runLen = cc >> CC_RUN_SHIFT;
				if (runLen == 0) { i++; continue; } // CC_QUOTE
				// check preceded by `<` or `</`
				boolean beforeOK = false;
				if (i >= 1 && (lcText[i - 1] & 0xFF) == '<') beforeOK = true;
				else if (i >= 2 && (lcText[i - 1] & 0xFF) == '/' && (lcText[i - 2] & 0xFF) == '<') beforeOK = true;
				if (beforeOK) {
					for (int t = 0; t < n; t++) {
						String tag = lowerTagNames[t];
						if (tag.length() != runLen) continue;
						int y = 0;
						while (y < runLen && (lcText[i + y] & 0xFF) == tag.charAt(y)) y++;
						if (y == runLen) return true;
					}
				}
				i += runLen;
				continue;
			}
			if (cc < 0) {
				i -= cc; // -cc = distance to next non-space
				continue;
			}
			i++;
		}
		return false;
	}

	/**
	 * Gibt eine Untermenge des CFMLString als CFMLString zurueck, ausgehend von start bis zum Ende des
	 * CFMLString.
	 * 
	 * @param start Von wo aus die Untermenge ausgegeben werden soll.
	 * @return Untermenge als CFMLString
	 */
	public SourceCode subCFMLString(int start) {
		return subCFMLString(start, len - start);
	}

	/**
	 * Advances pos to the next '#', quoter, or end of input using charClass hops.
	 * Var-char runs (CC_LETTER, CC_DIGIT) provably contain no '#' or quote — hop them.
	 * Operator/punctuation chars that are not the sentinel advance by 1.
	 */
	public void scanStringSegment(char quoter) {
		while (pos < len) {
			short v = charClass[pos];
			if (v > 0 && (v & 3) != 0) {
				pos += v >> CC_RUN_SHIFT;
			}
			else {
				int c = lcText[pos] & 0xFF;
				if (c == '#' || c == quoter) break;
				pos++;
			}
		}
	}

	/**
	 * Appends text[from..to) to sb without creating an intermediate String.
	 */
	public void appendSegmentTo(StringBuilder sb, int from, int to) {
		sb.append(text, from, to - from);
	}

	/**
	 * return a subset of the current SourceCode
	 * 
	 * @param start start position of the new subset.
	 * @param count length of the new subset.
	 * @return subset of the SourceCode as new SourcCode
	 */
	public SourceCode subCFMLString(int start, int count) {
		return new SourceCode(this, String.valueOf(text, start, count), writeLog);

	}

	/**
	 * Gibt den CFMLString als String zurueck.
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return new String(this.text);
	}

	/**
	 * Gibt die aktuelle Position des Zeigers innerhalb des CFMLString zurueck.
	 * 
	 * @return Position des Zeigers
	 */
	public int getPos() {
		return pos;
	}

	/**
	 * Setzt die Position des Zeigers innerhalb des CFMLString, ein ungueltiger index wird ignoriert.
	 * 
	 * @param pos Position an die der Zeiger gestellt werde soll.
	 */
	public void setPos(int pos) {
		// Trivial: currentLine is left stale as a "last known good" hint.
		// getPosition() / getLine() reconcile lazily via linear scan from the hint in either
		// direction — cheap for the common rollback/advance patterns where the parser stays
		// within 1-3 lines. next() / previous() already skip currentLine maintenance, so this
		// is symmetric with existing behavior.
		this.pos = pos;
	}

	/**
	 * Gibt die aktuelle Zeile zurueck in der der Zeiger des CFMLString steht.
	 * 
	 * @return Zeilennummer
	 */
	public int getLine() {
		return getLine(pos);
	}

	public Position getPosition() {
		return getPosition(pos);
	}

	public Position getPosition(int pos) {
		// Check cache first
		if (pos == cachedPos && cachedPosition != null) {
			return cachedPosition;
		}

		int line;
		// Use currentLine as hint when asking for current position
		if (pos == this.pos && currentLine >= 1 && currentLine <= lines.length) {
			int lineEnd = lines[currentLine - 1];
			if (pos <= lineEnd && (currentLine == 1 || pos > lines[currentLine - 2])) {
				// currentLine is still valid
				line = currentLine;
			}
			else if (pos > lineEnd) {
				// Moved forward past current line - scan forward (common case, usually 1-2 iterations)
				line = currentLine;
				while (line < lines.length && pos > lines[line - 1]) {
					line++;
				}
				currentLine = line;  // Update cache
			}
			else {
				// Moved backward — linear scan from stale hint
				line = currentLine;
				while (line > 1 && pos <= lines[line - 2]) line--;
				currentLine = line;
			}
		}
		else {
			// No hint available - binary search
			line = getLine(pos);
		}

		int posAtStart = (line > 1) ? lines[line - 2] : 0;
		int column = pos - posAtStart;
		Position position = new Position(line, column, pos);
		// Cache this position
		cachedPos = pos;
		cachedPosition = position;
		return position;
	}

	/**
	 * Gibt zurueck in welcher Zeile die angegebene Position ist.
	 * 
	 * @param pos Position von welcher die Zeile erfragt wird
	 * @return Zeilennummer
	 */
	public int getLine(int pos) {
		// Binary search to find the line containing the position
		int left = 0;
		int right = lines.length - 1;

		while (left <= right) {
			int mid = left + (right - left) / 2;
			if (pos <= lines[mid]) {
				right = mid - 1;
			} else {
				left = mid + 1;
			}
		}
		return left + 1;
	}

	/**
	 * Gibt die Stelle in der aktuelle Zeile zurueck, in welcher der Zeiger steht.
	 * 
	 * @return Position innerhalb der Zeile.
	 */
	public int getColumn() {
		return getColumn(pos);
	}

	/**
	 * Gibt die Stelle in der Zeile auf die pos zeigt zurueck.
	 * 
	 * @param pos Position von welcher die Zeile erfragt wird
	 * @return Position innerhalb der Zeile.
	 */
	public int getColumn(int pos) {
		int line = getLine(pos) - 1;
		if (line == 0) return pos + 1;
		return pos - lines[line - 1];
	}

	/**
	 * Gibt die Zeile auf welcher der Zeiger steht als String zurueck.
	 * 
	 * @return Zeile als Zeichenkette
	 */
	public String getLineAsString() {
		return getLineAsString(getLine(pos));
	}

	/**
	 * Gibt die angegebene Zeile als String zurueck.
	 * 
	 * @param line Zeile die zurueck gegeben werden soll
	 * @return Zeile als Zeichenkette
	 */
	public String getLineAsString(int line) {
		int index = line - 1;
		if (lines.length <= index) return null;
		int max = lines[index];
		int min = 0;
		if (index != 0) min = lines[index - 1] + 1;

		if (min < max && max - 1 < len) return this.substring(min, max - min);
		return "";
	}

	/**
	 * Gibt zurueck ob der Zeiger auf dem letzten Zeichen steht.
	 * 
	 * @return Gibt zurueck ob der Zeiger auf dem letzten Zeichen steht.
	 */
	public boolean isLast() {
		return pos == len - 1;
	}

	/**
	 * Gibt zurueck ob der Zeiger nach dem letzten Zeichen steht.
	 * 
	 * @return Gibt zurueck ob der Zeiger nach dem letzten Zeichen steht.
	 */
	public boolean isAfterLast() {
		return pos >= len;
	}

	/**
	 * Gibt zurueck ob der Zeiger einen korrekten Index hat.
	 * 
	 * @return Gibt zurueck ob der Zeiger einen korrekten Index hat.
	 */
	public boolean isValidIndex() {
		return pos < len && pos > -1;
	}

	/**
	 * Gibt zurueck, ausgehend von der aktuellen Position, wann das naechste Zeichen folgt das gleich
	 * ist wie die Eingabe, falls keines folgt wird -1 zurueck gegeben. Gross- und Kleinschreibung der
	 * Zeichen werden igoriert.
	 * 
	 * @param c gesuchtes Zeichen
	 * @return Zeichen das gesucht werden soll.
	 */
	public int indexOfNext(char c) {
		return indexOfNext(pos, c);
	}

	/**
	 * Find next {@code c} in {@code [from, len)}. <b>Contract:</b> c must be an operator/punctuation
	 * char (charClass kind 0) — not a var-char, not whitespace. Hops annotated runs of both kinds via
	 * charClass, so identifier bodies and whitespace stretches skip in one array load. For whitespace
	 * targets use {@link #skipWhitespace}; for identifier scans use {@link #forwardVarCharRun}.
	 */
	public int indexOfNext(int from, char c) {
		while (from < len) {
			short cc = charClass[from];
			if (cc < 0) { from += -cc; continue; }
			int hop = cc >> CC_RUN_SHIFT;
			if (hop > 0) { from += hop; continue; }
			if ((lcText[from] & 0xFF) == c) return from;
			from++;
		}
		return -1;
	}

	public int indexOfNext(String str) {
		char[] carr = str.toCharArray();
		outer: for (int i = pos; i < len; i++) {
			if ((lcText[i] & 0xFF) == carr[0]) {
				// print.e("- "+lcText[i]);
				for (int y = 1; y < carr.length; y++) {
					// print.e("-- "+y);
					if (len <= i + y || (lcText[i + y] & 0xFF) != carr[y]) {
						// print.e("ggg");
						continue outer;
					}
				}
				return i;
			}
		}
		return -1;
	}

	/**
	 * Gibt das letzte Wort das sich vor dem aktuellen Zeigerstand befindet zurueck, falls keines
	 * existiert wird null zurueck gegeben.
	 * 
	 * @return Word vor dem aktuellen Zeigerstand.
	 */
	public String lastWord() {
		int size = 1;
		while (pos - size > 0 && (lcText[pos - size] & 0xFF) == ' ') {
			size++;
		}
		while (pos - size > 0 && (lcText[pos - size] & 0xFF) != ' ' && (lcText[pos - size] & 0xFF) != ';') {
			size++;
		}
		return this.substring((pos - size + 1), (pos - 1));
	}

	/**
	 * Gibt die Laenge des CFMLString zurueck.
	 * 
	 * @return Laenge des CFMLString.
	 */
	public int length() {
		return len;
	}

	/**
	 * Prueft ob das uebergebene Objekt diesem Objekt entspricht.
	 * 
	 * @param o Object zum vergleichen.
	 * @return Ist das uebergebene Objekt das selbe wie dieses.
	 */
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SourceCode)) return false;
		return o.toString().equals(this.toString());
	}

	public boolean getWriteLog() {
		return writeLog;
	}

	public String getText() {
		return new String(text);
	}

	public String id() {
		return HashUtil.create64BitHashAsString(getText());
	}

	@Override
	public int hashCode() {
		return hash;
	}

	/** True when the source should be parsed directly by the script transformer (bypassing the tag parser).
	 *  Set for .cfs and script-syntax .cfc after {@link #findTag} rules out tag markers. */
	public boolean isScriptMode() {
		return scriptMode;
	}

	public void setScriptMode(boolean scriptMode) {
		this.scriptMode = scriptMode;
	}
}