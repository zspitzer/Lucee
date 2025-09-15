package lucee.runtime.engine;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for resolving the OSGi/Felix bundle cache directory as a NIO Path.
 * Uses Jimfs if enabled, otherwise falls back to disk.
 */
public class BundleCachePathUtil {
    /**
     * Resolves the OSGi/Felix bundle cache directory as a NIO Path.
     * Uses Jimfs if enabled, otherwise falls back to disk.
     * @return Path to use for bundle cache, or null if not set
     */
    public static Path resolveBundleCachePath() {
    String useJimfs = System.getProperty("lucee.deploy.memory", "false");
        try {
            if ("true".equalsIgnoreCase(useJimfs)) {
                // Use Jimfs in-memory filesystem
                // (Assumes Jimfs is on the classpath)
                Class<?> jimfsClass = Class.forName("com.google.common.jimfs.Jimfs");
                FileSystem jimfs = (FileSystem) jimfsClass.getMethod("newFileSystem").invoke(null);
                Path jimfsPath = jimfs.getPath("/felix-cache");
                Files.createDirectories(jimfsPath);
                return jimfsPath;
            } else {
                // Default: use temp directory
                Path tmp = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"), "felix-cache");
                Files.createDirectories(tmp);
                return tmp;
            }
        } catch (Throwable t) {
            // Log or handle error as appropriate
            return null;
        }
    }
}
