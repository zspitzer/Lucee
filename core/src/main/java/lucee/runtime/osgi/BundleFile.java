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
package lucee.runtime.osgi;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import org.osgi.framework.BundleException;

import lucee.commons.io.IOUtil;
import lucee.commons.io.res.Resource;

public final class BundleFile extends BundleInfo {

	private static final long serialVersionUID = -7094382262249367193L;
		private Path path;
public static BundleFile getInstance(Path path) throws IOException, BundleException {
	String absPath = path.toAbsolutePath().toString();
	SoftReference<BundleFile> tmp = files.get(absPath);
	BundleFile bi = tmp == null ? null : tmp.get();
	if (bi == null) {
		 bi = new BundleFile(path);
		 files.put(absPath, new SoftReference<BundleFile>(bi));
	}
	return bi;
}
private BundleFile(Path path) throws IOException, BundleException {
	super(path);
	this.path = path;
}
	private static Map<String, SoftReference<BundleFile>> files = new ConcurrentHashMap<String, SoftReference<BundleFile>>();
	private Map<String, SoftReference<Boolean>> classes = new ConcurrentHashMap<String, SoftReference<Boolean>>();

	   public static BundleFile getInstance(Resource file, boolean onlyValidBundles) throws IOException, BundleException {
		   BundleFile bi = getInstance(toPathResource(file));
		   if (onlyValidBundles && !bi.isBundle()) return null;
		   return bi;
	   }

	   public static BundleFile getInstance(Resource file, BundleFile defaultValue) {
		   try {
			   return getInstance(toPathResource(file));
		   }
		   catch (Exception e) {
			   return defaultValue;
		   }
	   }

	   public static BundleFile getInstance(Resource file) throws IOException, BundleException {
		   return getInstance(toPathResource(file));
	   }

	   // File-based getInstance and constructor removed; use Path or Resource instead

	   public InputStream getInputStream() throws IOException {
		   return Files.newInputStream(path);
	   }

	   public boolean hasClass(String className) throws IOException {
		   className = className.replace('.', '/') + ".class";
		   SoftReference<Boolean> tmp = classes.get(className);
		   Boolean b = tmp == null ? null : tmp.get();
		   if (b != null) return b.booleanValue();
		   JarFile jar = new JarFile(path.toFile());
		   try {
			   b = jar.getEntry(className) != null;
			   classes.put(className, new SoftReference<Boolean>(b));
			   return b.booleanValue();
		   }
		   finally {
			   IOUtil.closeEL(jar);
		   }
	   }

	   @Override
	   public String toString() {
		   return path.toString();
	   }

	   public String getAbsolutePath() {
		   return path.toAbsolutePath().toString();
	   }

	   public boolean delete() {
		   try {
			   return Files.deleteIfExists(path);
		   } catch (IOException e) {}
		   return false;
	   }

	   public void deleteOnExit() {
		   // Not supported for Path; implement if needed
	   }

	   public Path getPath() {
		   return path;
	   }
}