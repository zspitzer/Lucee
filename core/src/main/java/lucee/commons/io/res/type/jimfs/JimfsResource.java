package lucee.commons.io.res.type.jimfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.util.ResourceSupport;
import lucee.commons.io.res.util.ResourceUtil;
import lucee.commons.lang.StringUtil;

/**
 * Resource implementation for Jimfs (in-memory NIO filesystem)
 */
import lucee.commons.io.res.ResourceProvider;
public class JimfsResource extends ResourceSupport {
    private static final long serialVersionUID = 1L;
    private final JimfsResourceProvider provider;
    private final String path;

    public JimfsResource(JimfsResourceProvider provider, String path) {
        this.provider = provider;
        this.path = path;
    }

    // Only one implementation per method below. All signatures match Resource interface.
    // ...existing code...
    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean isAbsolute() {
        return path.startsWith("/");
    }

    @Override
    public Resource getRealResource(String relpath) {
        if (relpath.length() == 0 || relpath.equals(".")) return this;
        if (relpath.equals("..")) return getParentResource();
        String sep = String.valueOf(provider.getSeparator());
        String newPath = path.endsWith(sep) ? path + relpath : path + sep + relpath;
        return new JimfsResource(provider, newPath);
    }

    @Override
    public String getParent() {
        int idx = path.lastIndexOf(provider.getSeparator());
        if (idx <= 0) return null;
        return path.substring(0, idx);
    }

    @Override
    public ResourceProvider getResourceProvider() {
        return provider;
    }
    @Override
    public String getName() {
        int idx = path.lastIndexOf(provider.getSeparator());
        if (idx < 0) return path;
        return path.substring(idx + 1);
    }

    public Resource getParentResource() {
        int idx = path.lastIndexOf(provider.getSeparator());
        if (idx <= 0) return null;
        String parentPath = path.substring(0, idx);
        return new JimfsResource(provider, parentPath);
    }

    @Override
    public boolean isReadable() {
        try {
            return Files.isReadable(provider.toPath(path));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isWriteable() {
        try {
            return Files.isWritable(provider.toPath(path));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public OutputStream getOutputStream(boolean append) throws IOException {
        Path nioPath = provider.toPath(path);
        if (!Files.exists(nioPath.getParent())) {
            Files.createDirectories(nioPath.getParent());
        }
        if (append && Files.exists(nioPath)) {
            return Files.newOutputStream(nioPath, java.nio.file.StandardOpenOption.APPEND);
        } else {
            return Files.newOutputStream(nioPath);
        }
    }

    @Override
    public void remove(boolean force) throws IOException {
        Path nioPath = provider.toPath(path);
        if (Files.isDirectory(nioPath) && force) {
            Files.walk(nioPath)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        } else {
            Files.deleteIfExists(nioPath);
        }
    }

    @Override
    public void createFile(boolean createParentWhenNotExists) throws IOException {
        Path nioPath = provider.toPath(path);
        if (createParentWhenNotExists) {
            Path parent = nioPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        }
        Files.createFile(nioPath);
    }

    @Override
    public void createDirectory(boolean createParentWhenNotExists) throws IOException {
        Path nioPath = provider.toPath(path);
        if (createParentWhenNotExists) {
            Files.createDirectories(nioPath);
        } else {
            Files.createDirectory(nioPath);
        }
    }

    @Override
    public boolean setLastModified(long time) {
        try {
            Files.setLastModifiedTime(provider.toPath(path), java.nio.file.attribute.FileTime.fromMillis(time));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean setWritable(boolean writable) {
        // Jimfs supports POSIX permissions, but for minimal impl, always return true
        return true;
    }

    @Override
    public boolean setReadable(boolean readable) {
        // Jimfs supports POSIX permissions, but for minimal impl, always return true
        return true;
    }

    @Override
    public int getMode() {
        try {
            if (!exists()) return 0;
            java.nio.file.attribute.PosixFileAttributes attrs = Files.readAttributes(provider.toPath(path), java.nio.file.attribute.PosixFileAttributes.class);
            java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions = attrs.permissions();
            return lucee.commons.io.ModeUtil.toOctalMode(java.nio.file.attribute.PosixFilePermissions.toString(permissions));
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void setMode(int mode) throws IOException {
        String permissions = lucee.commons.io.ModeUtil.fromOctalMode(mode);
        Files.setPosixFilePermissions(provider.toPath(path), java.nio.file.attribute.PosixFilePermissions.fromString(permissions));
    }

    @Override
    public Resource[] listResources() {
        try {
            Path nioPath = provider.toPath(path);
            if (!Files.isDirectory(nioPath)) return null;
            java.util.List<Resource> list = new java.util.ArrayList<>();
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(nioPath)) {
                for (Path entry : stream) {
                    list.add(new JimfsResource(provider, entry.toString()));
                }
            }
            return list.toArray(new Resource[0]);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getAbsolutePath() {
        return provider.getScheme() + "://" + path;
    }

    @Override
    public boolean exists() {
        try {
            return Files.exists(provider.toPath(path));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isFile() {
        try {
            return Files.isRegularFile(provider.toPath(path));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isDirectory() {
        try {
            return Files.isDirectory(provider.toPath(path));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long length() {
        try {
            return Files.size(provider.toPath(path));
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public long lastModified() {
        try {
            BasicFileAttributes attrs = Files.readAttributes(provider.toPath(path), BasicFileAttributes.class);
            return attrs.lastModifiedTime().toMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(provider.toPath(path));
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        Path nioPath = provider.toPath(path);
        if (!Files.exists(nioPath.getParent())) {
            Files.createDirectories(nioPath.getParent());
        }
        return Files.newOutputStream(nioPath);
    }

    @Override
    public boolean delete() {
        try {
            return Files.deleteIfExists(provider.toPath(path));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean mkdirs() {
        try {
            Files.createDirectories(provider.toPath(path));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return getAbsolutePath();
    }
}
