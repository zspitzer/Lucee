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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import lucee.commons.digest.HashUtil;
import lucee.commons.io.IOUtil;
import lucee.commons.lang.ClassUtil;
import lucee.runtime.PageSource;

public final class PageSourceCode extends SourceCode {

	private final Charset charset;
	private final PageSource ps;

	public PageSourceCode(PageSource ps, Charset charset, boolean writeLog) throws IOException {
		this(ps, readCharArray(ps, charset), charset, writeLog);
	}

	private PageSourceCode(PageSource ps, IOUtil.CharArrayResult content, Charset charset, boolean writeLog) {
		super(null, content.buf, content.len, writeLog);
		this.charset = charset;
		this.ps = ps;
	}

	public PageSourceCode(PageSource ps, String text, Charset charset, boolean writeLog) {
		super(null, text, writeLog);
		this.charset = charset;
		this.ps = ps;
	}

	private static IOUtil.CharArrayResult readCharArray(PageSource ps, Charset charset) throws IOException {
		InputStream is = null;
		try {
			is = IOUtil.toBufferedInputStream(ps.getPhyscalFile().getInputStream());
			if (ClassUtil.isBytecode(is)) throw new AlreadyClassException(ps.getPhyscalFile(), false);
			if (ClassUtil.isEncryptedBytecode(is)) throw new AlreadyClassException(ps.getPhyscalFile(), true);
			// byte length is a strong upper bound on char count for ASCII/Latin-1 CFML — one alloc, no growth
			long bytes = ps.getPhyscalFile().length();
			int hint = bytes > 0 && bytes <= Integer.MAX_VALUE ? (int) bytes : 512;
			return IOUtil.toCharArray(is, charset, hint);
		}
		finally {
			IOUtil.close(is);
		}
	}

	public static String toString(PageSource ps, Charset charset) throws IOException {
		String content;
		InputStream is = null;
		try {
			is = IOUtil.toBufferedInputStream(ps.getPhyscalFile().getInputStream());
			if (ClassUtil.isBytecode(is)) throw new AlreadyClassException(ps.getPhyscalFile(), false);
			if (ClassUtil.isEncryptedBytecode(is)) throw new AlreadyClassException(ps.getPhyscalFile(), true);
			content = IOUtil.toString(is, charset);
		}
		finally {
			IOUtil.close(is);
		}
		return content;
	}

	@Override
	public String id() {
		return HashUtil.create64BitHashAsString(getPageSource().getDisplayPath());
	}

	/**
	 * Gibt die Quelle aus dem der CFML Code stammt als File Objekt zurueck, falls dies nicht aud einem
	 * File stammt wird null zurueck gegeben.
	 * 
	 * @return source Quelle des CFML Code.
	 */
	public PageSource getPageSource() {
		return ps;
	}

	public Charset getCharset() {
		return charset;
	}

	@Override
	public SourceCode subCFMLString( int start, int count ) {
		return new PageSourceCode( ps, String.valueOf( text, start, count ), charset, getWriteLog() );
	}
}