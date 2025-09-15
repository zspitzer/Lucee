package lucee.commons.io.res.type.jimfs;

import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.ResourceProviderPro;
import lucee.commons.io.res.Resources;
import lucee.commons.io.res.util.ResourceLockImpl;
import lucee.commons.io.res.util.ResourceUtil;
import lucee.runtime.op.Caster;

/**
 * ResourceProvider for Jimfs (in-memory NIO filesystem)
 */
public class JimfsResourceProvider implements ResourceProviderPro {
    private String scheme = "jimfs";
    private FileSystem fs;
    private boolean caseSensitive = true;
    private long lockTimeout = 1000;
    private ResourceLockImpl lock = new ResourceLockImpl(lockTimeout, caseSensitive);
    private Map arguments;
    private Resources resources;

    @Override
    public JimfsResourceProvider init(String scheme, Map arguments) {
        if (scheme != null && !scheme.isEmpty()) this.scheme = scheme;
        this.arguments = arguments;
        if (arguments != null) {
            Object oCaseSensitive = arguments.get("case-sensitive");
            if (oCaseSensitive != null) caseSensitive = Caster.toBooleanValue(oCaseSensitive, true);
            Object oTimeout = arguments.get("lock-timeout");
            if (oTimeout != null) lockTimeout = Caster.toLongValue(oTimeout, lockTimeout);
        }
        lock.setLockTimeout(lockTimeout);
        lock.setCaseSensitive(caseSensitive);
        // Create a new Jimfs filesystem (Unix-like)
        this.fs = Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());
        return this;
    }

    @Override
    public Resource getResource(String path) {
        path = ResourceUtil.removeScheme(scheme, path);
        return new JimfsResource(this, path);
    }

    FileSystem getFileSystem() {
        return fs;
    }

    Path toPath(String path) {
        return fs.getPath(path);
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public void setResources(Resources resources) {
        this.resources = resources;
    }

    @Override
    public void lock(Resource res) throws IOException {
        lock.lock(res);
    }

    @Override
    public void unlock(Resource res) {
        lock.unlock(res);
    }

    @Override
    public void read(Resource res) throws IOException {
        lock.read(res);
    }

    @Override
    public boolean isAttributesSupported() {
        return true;
    }

    @Override
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    @Override
    public boolean isModeSupported() {
        return true;
    }

    @Override
    public Map getArguments() {
        return arguments;
    }

    @Override
    public char getSeparator() {
        return '/';
    }

    @Override
    public boolean allowMatching() {
        return false;
    }
}
