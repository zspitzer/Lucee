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
package lucee.runtime.tag;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.jsp.tagext.Tag;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.config.Identification;
import lucee.runtime.exp.PageException;
import lucee.runtime.op.Caster;
import lucee.runtime.tag.javax.proxy.TagProxy;
import lucee.transformer.library.ClassDefinitionImpl;

// TODO kann man nicht auf context ebene

/**
 * Pool to Handle Tags
 *
 * tags are no longer pooled, a fresh handler per use stays young. Regular tags are created by the
 * compiled page, this creates extension tags and tags of classes compiled before that
 */
public final class TagHandlerPool {

	private ConcurrentHashMap<String, Constructor<?>> constructors = new ConcurrentHashMap<String, Constructor<?>>();
	private ConfigWeb config;

	public TagHandlerPool(ConfigWeb config) {
		this.config = config;
	}

	/**
	 * return a tag to use from a class
	 *
	 * @param className
	 * @param tagBundleName
	 * @param tagBundleVersion
	 * @param id
	 * @return Tag
	 * @throws PageException
	 */
	public Tag use(String className, String maven) throws PageException {
		String tagId = toId(className, maven);
		Constructor<?> c = constructors.get(tagId);
		if (c == null) c = cacheConstructor(tagId, loadClass(className, maven));
		return newTag(c);
	}

	public Tag use(String className, String tagBundleName, String tagBundleVersion, Identification id) throws PageException {
		String tagId = toId(className, tagBundleName, tagBundleVersion);
		Constructor<?> c = constructors.get(tagId);
		if (c == null) c = cacheConstructor(tagId, loadClass(className, tagBundleName, tagBundleVersion, id));
		return newTag(c);
	}

	/**
	 * free a tag for reusing
	 *
	 * @param tag
	 */
	public void reuse(Tag tag) {
		tag.release();
	}

	public void reuse(Tag tag, String maven) {
		tag.release();
	}

	public void reuse(Tag tag, String bundleName, String bundleVersion) {
		tag.release();
	}

	private String toId(String className, String tagBundleName, String tagBundleVersion) {
		if (tagBundleName == null && tagBundleVersion == null) return className;
		if (tagBundleVersion == null) return className + ":" + tagBundleName;
		return className + ":" + tagBundleName + ":" + tagBundleVersion;
	}

	private String toId(String className, String maven) {
		if (maven == null) return className;
		return className + ":" + maven;
	}

	private Class<?> loadClass(String className, String maven) throws PageException {
		try {
			return new ClassDefinitionImpl(className, maven).setVersionOnlyMattersWhenDownloading(true).getClazz();
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
	}

	private Class<?> loadClass(String className, String tagBundleName, String tagBundleVersion, Identification id) throws PageException {
		try {
			return new ClassDefinitionImpl(className, tagBundleName, tagBundleVersion, id).setVersionOnlyMattersWhenDownloading(true).getClazz();
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
	}

	private Constructor<?> cacheConstructor(String tagId, Class<?> clazz) throws PageException {
		try {
			Constructor<?> c = clazz.getConstructor();
			constructors.put(tagId, c);
			return c;
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
	}

	private Tag newTag(Constructor<?> c) throws PageException {
		try {
			return TagProxy.getInstance(c.newInstance());
		}
		catch (Exception e) {
			throw Caster.toPageException(e);
		}
	}

	public void reset() {
		constructors.clear();
	}
}
