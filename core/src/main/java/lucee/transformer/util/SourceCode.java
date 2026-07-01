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
	private static final short CC_COMMENT     = 8; // reserved for lazy comment annotation

	// charClass encoding: short[], (runLength << CC_RUN_SHIFT) | kind
	// kind occupies bits 0-3; run-length occupies bits 4-14.
	// bit 15 is the sign bit: negative = whitespace or annotated comment, 0 = operator, positive = var-char/quote.
	private static final int  CC_RUN_SHIFT   = 4;
	private static final int  CC_MAX_RUN     = Short.MAX_VALUE >> CC_RUN_SHIFT; // 2047 — effectively uncapped
	private static final int  CC_SPACE_MAX   = Short.MAX_VALUE; // 32767 — one hop covers any whitespace/comment run

	protected int pos = 0;
	protected int currentLine = 1; // Track current line number (1-based)

	protected final char[] text;
	protected final char[] lcText;
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
	 *               bits 0-3: kind — CC_LETTER(1), CC_DIGIT(2), CC_QUOTE(4), CC_COMMENT(8)
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
	private int sourceOffset;

	// Position cache - reduces allocations during parsing
	private Position cachedPosition;
	private int cachedPos = -1;

	public SourceCode(SourceCode parent, String strText, boolean writeLog) {
		this(parent, strText, writeLog, 0);
	}

	/**
	 * Constructor of the class
	 * 
	 * @param parent
	 * @param strText
	 * @param writeLog
	 */
	public SourceCode(SourceCode parent, String strText, boolean writeLog, int sourceOffset) {
		this.parent = parent;
		this.text = strText.toCharArray();
		this.len = this.text.length;
		this.hash = strText.hashCode();
		this.sourceOffset = sourceOffset;
		lcText = new char[len];
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
	public SourceCode(SourceCode parent, char[] textBuf, int validLen, boolean writeLog, int sourceOffset) {
		this.parent = parent;
		this.text = textBuf.length == validLen ? textBuf : Arrays.copyOf(textBuf, validLen);
		this.len = validLen;
		this.hash = hashCharArray(this.text, validLen);
		this.sourceOffset = sourceOffset;
		lcText = new char[validLen];
		charClass = new short[validLen];
		this.lines = buildLcTextAndCharClass();
		this.writeLog = writeLog;
	}

	private static int hashCharArray(char[] a, int len) {
		int h = 0;
		for (int i = 0; i < len; i++) h = 31 * h + a[i];
		return h;
	}

	private int[] buildLcTextAndCharClass() {
		// single backward pass: builds lcText[], charClass[], and lines[] together.
		// lines[] comes out in descending order and is reversed at the end.
		// only A-Z need lowercasing (ASCII source) — bit-flip avoids Character.toLowerCase().
		int[] arr = new int[32];
		int count = 0;
		int nextNonSpace = len;
		int nextNonVar   = len;

		for (int i = len - 1; i >= 0; i--) {
			char raw = text[i];
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
			lcText[i] = lc;

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
		return lcText[pos];
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
		return lcText[pos];
	}

	public boolean isPrevious(char c) {
		if (!hasPrevious()) return false;
		return lcText[pos - 1] == c;
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
		return lcText[pos + 1] == c;
	}

	public boolean isNext(char a, char b) {
		if (!hasNextNext()) return false;
		return lcText[pos + 1] == a && lcText[pos + 2] == b;
	}

	/**
	 * is the character at the current position (internal pointer) in the range of the given input
	 * characters?
	 * 
	 * @param left lower value.
	 * @param right upper value.
	 */
	public boolean isCurrentBetween(char left, char right) {
		return pos < len && lcText[pos] >= left && lcText[pos] <= right;
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
		return pos < len && lcText[pos] == '#';
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
		return lcText[pos] == c;
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
			if (str.charAt(i) != lcText[pos + i]) return false;
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
		return pos > 0 && lcText[pos - 1] == ' ';
	}

	public boolean hasNLBefore() {
		int index = 0;
		while (pos - (++index) >= 0) {
			if (text[pos - index] == '\n') return true;
			if (text[pos - index] == '\r') return true;
			if (lcText[pos - index] != ' ') return false;
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
	 * Retroactively marks a consumed comment range as skippable whitespace.
	 * Uses the same negative-distance encoding as removeSpace(), so any subsequent
	 * removeSpace() call at or within [start, end) jumps past the whole range in one load.
	 */
	public void annotateComment(int start, int end) {
		for (int i = end - 1; i >= start; i--) {
			int dist = end - i;
			charClass[i] = (short)(dist > CC_SPACE_MAX ? -CC_SPACE_MAX : -dist);
		}
	}

	public void revertRemoveSpace() {
		while (hasSpaceBefore()) {
			previous();
		}
	}

	public String removeAndGetSpace() {
		int start = pos;
		while (pos < len && lcText[pos] == ' ') {
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
		return String.valueOf(lcText, start, count);
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
				char c = lcText[pos];
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
		// Update current line cache when position changes
		if (pos != this.pos) {
			// If moving forward and staying on same line, no update needed
			if (pos > this.pos && pos <= lines[currentLine - 1]) {
				// Still on same line, no update needed
			}
			// Moving forward past current line
			else if (pos > this.pos) {
				// Scan forward to find new line
				while (currentLine < lines.length && pos > lines[currentLine - 1]) {
					currentLine++;
				}
			}
			// Moving backward - binary search
			else {
				currentLine = getLine(pos);
			}
		}
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
				// Moved backward (rare) - binary search
				line = getLine(pos);
				currentLine = line;  // Update cache
			}
		}
		else {
			// No hint available - binary search
			line = getLine(pos);
		}

		int posAtStart = (line > 1) ? lines[line - 2] : 0;
		int column = pos - posAtStart;
		Position position = new Position(line, column, pos, getSourceOffset());
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
		for (int i = pos; i < len; i++) {
			if (lcText[i] == c) return i;
		}
		return -1;
	}

	public int indexOfNext(String str) {
		char[] carr = str.toCharArray();
		outer: for (int i = pos; i < len; i++) {
			if (lcText[i] == carr[0]) {
				// print.e("- "+lcText[i]);
				for (int y = 1; y < carr.length; y++) {
					// print.e("-- "+y);
					if (len <= i + y || lcText[i + y] != carr[y]) {
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
		while (pos - size > 0 && lcText[pos - size] == ' ') {
			size++;
		}
		while (pos - size > 0 && lcText[pos - size] != ' ' && lcText[pos - size] != ';') {
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

	public int getSourceOffset() {
		return sourceOffset;
	}
}