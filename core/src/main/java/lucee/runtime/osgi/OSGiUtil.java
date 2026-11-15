package lucee.runtime.osgi;

import java.io.File;
import java.io.IOException;

import lucee.commons.io.res.Resource;
import lucee.commons.io.res.util.ResourceUtil;

/**
 * Access to OSGi for code that was written for Lucee 8 and earlier, where the OSGi implementation was
 * part of the core. OSGi moved into the OSGi extension (LDEV-6627), and everything this class does is
 * available from {@link OSGiService} ({@code CFMLEngine.getOSGiService()}), which is what new code
 * should use.
 * <p>
 * Only the entry points that CFML applications actually call are left. No OSGi type is part of the
 * signature, so the class can be used without an OSGi implementation on the class path: when the OSGi
 * extension is not installed, every call throws {@link OSGiNotAvailableException} with the same
 * message the rest of the core produces, instead of a {@link ClassNotFoundException} for this class.
 *
 * @deprecated use {@code CFMLEngine.getOSGiService()} and the OSGi extension instead
 */
@Deprecated
public final class OSGiUtil {

	private OSGiUtil() {}

	/**
	 * @return true when the OSGi extension is installed and provides the OSGi implementation
	 */
	public static boolean isAvailable() {
		return OSGi.isAvailable();
	}

	/**
	 * Hand a jar over to OSGi, the way {@code OSGiUtil.installBundle(bundleContext, resource, start)}
	 * did before Lucee 9.
	 *
	 * @param bundleContext the context of the engine, only accepted for compatibility, may be null
	 * @param start start the bundle right away (its activator runs), otherwise it is started when it is
	 *            used for the first time
	 * @return true when the jar is a bundle and is now available
	 * @throws OSGiNotAvailableException when the OSGi extension is not installed
	 * @throws IOException when the jar is a bundle but cannot be installed
	 * @deprecated use {@link OSGiService#install(File, boolean)}
	 */
	@Deprecated
	public static boolean installBundle(Object bundleContext, Resource res, boolean start) throws IOException {
		File jar = ResourceUtil.toFile(res);
		if (!OSGi.isAvailable()) throw new OSGiNotAvailableException(OSGi.notInstalledMessage("the bundle [" + jar.getName() + "]"));
		// the third argument is "start", not the "checkExistence" of Lucee 8 and earlier: this installs and starts
		// the bundle, it does not look for an already installed one with the same symbolic name and version
		return OSGi.get().install(jar, true);
	}

	/**
	 * @return the OSGi implementation, never null, {@link #isAvailable()} tells whether the extension is installed
	 * @deprecated use {@code CFMLEngine.getOSGiService()}
	 */
	@Deprecated
	public static OSGiService getOSGiService() {
		return OSGi.get();
	}
}
