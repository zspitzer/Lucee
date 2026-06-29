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
		super(null, toString(ps, charset), writeLog, 0);
		this.charset = charset;
		this.ps = ps;
		initRegions();
	}

	public PageSourceCode(PageSource ps, String text, Charset charset, boolean writeLog) {
		super(null, text, writeLog, 0);
		this.charset = charset;
		this.ps = ps;
		initRegions();
	}

	public PageSourceCode(PageSource ps, String text, Charset charset, boolean writeLog, int sourceOffset) {
		super(null, text, writeLog, sourceOffset);
		this.charset = charset;
		this.ps = ps;
		initRegions();
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
		// Return base SourceCode (no region map) — sub-sources are contextual fragments; region lookup on the root PageSourceCode handles them
		return new SourceCode( this, String.valueOf( text, start, count ), getWriteLog(), getSourceOffset() + start );
	}
}