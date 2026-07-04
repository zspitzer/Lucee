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
package lucee.transformer.cfml;

import lucee.runtime.config.Config;
import lucee.transformer.Body;
import lucee.transformer.Factory;
import lucee.transformer.Page;
import lucee.transformer.cfml.evaluator.EvaluatorPool;
import lucee.transformer.cfml.expression.SimpleExprTransformer;
import lucee.transformer.cfml.script.DocComment;
import lucee.transformer.library.function.FunctionLib;
import lucee.transformer.library.tag.TagLib;
import lucee.transformer.library.tag.TagLibTag;
import lucee.transformer.library.tag.TagLibTagScript;
import lucee.transformer.util.SourceCode;

public final class Data {

	public final SourceCode srcCode;
	public final TransfomerSettings settings;
	public final TagLib[][] tlibs;
	public final FunctionLib flibs;
	public final Page page;
	public final TagLibTag[] scriptTags;
	public final EvaluatorPool ep;
	public final Factory factory;
	public final Config config;
	public boolean allowLowerThan;
	public boolean parseExpression;

	private SimpleExprTransformer set;

	public short mode = 0;
	public boolean insideFunction;
	public String tagName;
	public boolean isCFC;
	public boolean isInterface;
	public short context = TagLibTagScript.CTX_NONE;
	public DocComment docComment;
	private Body parent;

	public void clearDocComment() {
		docComment = null;
	}

	public ExprTransformer transformer;

	public boolean insideTenaryMiddle = false;
	public boolean insideCase = false;
	public boolean hasWriteLog;
	public boolean hasUpper;
	public boolean hasCharset;
	public boolean ast;

	// Namespace fast-path cache — populated at construction, invalidated by
	// CFMLTransformer.executeEvaluator on cfimport-driven tlibs[TAG_LIB_PAGE] mutation.
	// See tag-parsing-modernisation.md and CFMLTransformer.nameSpace.
	public boolean nsFast;
	public TagLib nsFastLib;
	public char nsFastC0;
	public char nsFastC1;

	public Data(Factory factory, Config config, Page page, SourceCode srcCode, EvaluatorPool ep, TransfomerSettings settings, TagLib[][] tlibs, FunctionLib flibs,
			TagLibTag[] scriptTags, boolean allowLowerThan, boolean hasWriteLog, boolean hasUpper, boolean hasCharset, boolean ast) {
		this.page = page;
		this.srcCode = srcCode;
		this.settings = settings;
		this.tlibs = tlibs;
		this.flibs = flibs;
		this.scriptTags = scriptTags;
		this.ep = ep;
		this.factory = factory;
		this.config = config;
		this.allowLowerThan = allowLowerThan;
		this.hasWriteLog = hasWriteLog;
		this.hasUpper = hasUpper;
		this.hasCharset = hasCharset;
		this.ast = ast;
		initNsFast();
	}

	// 99% of CFML files: no cfimport, single global "cf"-prefix taglib, 2-char match.
	// If the initial tlibs shape fits that mould, cache the two chars + taglib ref so
	// CFMLTransformer.nameSpace can dispatch via one forwardIfExact(char, char) call.
	// Any cfimport during compile calls executeEvaluator, which clears nsFast (one-way).
	private void initNsFast() {
		if (tlibs == null || tlibs.length < 2) return;
		TagLib[] page = tlibs[1];   // TAG_LIB_PAGE
		TagLib[] global = tlibs[0]; // TAG_LIB_GLOBAL
		if (page == null || page.length != 0) return;
		if (global == null || global.length != 1) return;
		TagLib lib = global[0];
		char[] c = lib.getNameSpaceAndSeperatorAsCharArray();
		if (c.length != 2) return;
		nsFast = true;
		nsFastLib = lib;
		nsFastC0 = c[0];
		nsFastC1 = c[1];
	}

	public SimpleExprTransformer getSimpleExprTransformer() {
		return set;
	}

	public void setSimpleExprTransformer(SimpleExprTransformer set) {
		this.set = set;
	}

	public Body setParent(Body parent) {
		Body tmp = this.parent;
		this.parent = parent;
		return tmp;
	}

	public Body getParent() {
		return parent;
	}
}