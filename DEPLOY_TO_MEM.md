# Deploying Lucee to Memory FS (In-Memory Filesystem)

Lucee can be deployed and tested using an in-memory filesystem (memory FS). This is useful for CI, performance testing, and scenarios where disk I/O should be avoided.

## How to Enable Memory FS Deployment

1. **Build Requirements**
   - Ensure the in-memory filesystem provider is present as a test dependency in `loader/pom.xml` (already included).
   - Build Lucee as usual with Maven/Ant.


2. **Enable Memory FS via System Property or Environment Variable**

    - Set either of the following before starting Lucee:
       - System property: `-DLUCEE_USE_JIMFS=true` or `-Dlucee.use.jimfs=true`
       - Environment variable: `LUCEE_USE_JIMFS=true` or `lucee.use.jimfs=true`
    - This can be passed via JVM args in Ant/Maven, e.g.:
       ```sh
       ant test_in_memory_deploy -DLUCEE_USE_JIMFS=true
       # or
       mvn test -DLUCEE_USE_JIMFS=true
       ```

    - **When this flag is enabled, Lucee will automatically configure all resource-backed directories under `lucee-server` (as returned by `getResourceRoot()`) to use the in-memory filesystem provider.** This includes extensions, config, bundles, temp, and any other directory managed by the Resource abstraction. No manual mapping or code changes are required—everything under the server root will use memory FS for all file operations except logging.

3. **Behavior**
   - When enabled, Lucee's loader will use a memory FS for all server, webroot, and bundle directories.
   - When not enabled, Lucee uses the default disk-based filesystem.

4. **Testing**
   - The build includes `test_in_memory_deploy` and `test_disk_deploy` targets to compare in-memory and disk-based deployments.
   - Timing and logging output is printed to the console for both.

5. **Caveats & Known Issues**
   - The memory FS provider is included in the lucee.jar and available in all builds.
   - When testing or deploying to memory FS, always compare the resulting in-memory filesystem to the disk-based deploy. The in-memory FS should be a strict subset of the disk deploy; any files or folders present on disk but missing from memory FS indicate a compatibility or fallback issue.
   - All logging (including debug, info, and fallback logs) always goes to disk, never to the in-memory filesystem. This ensures logs are persistent and accessible regardless of the deployment mode.
   - In-memory deployments are ephemeral: all data is lost when the JVM exits.
   - **Memory FS paths do not support `Path.toFile()`.** Attempting to call `toFile()` on a memory FS path will throw `UnsupportedOperationException`. All code that interacts with files in memory FS must use NIO APIs (`Path`, `Files`, etc.) and avoid legacy `File` APIs.
   - Not all Java APIs are guaranteed to work with memory FS; report issues if encountered.

## Troubleshooting

If you see an error like:

```
java.lang.UnsupportedOperationException
    at ...Path.toFile(...)
```

This means some code is calling `toFile()` on a memory FS path. Update the code to use NIO APIs directly. This is a current limitation and is being addressed.

## API and Variable Naming

**Never use the memory FS provider name (e.g., 'jimfs') in any public API, function, or variable names.**

All code and documentation should refer to this feature as 'memory FS' or 'in-memory filesystem'. The implementation (Jimfs or otherwise) is an internal detail and may change.

Examples:

- `useMemoryFs` (not `useJimfs`)
- `memoryFileSystem` (not `jimfsFileSystem`)
- `isMemoryFsEnabled()` (not `isJimfsEnabled()`)

## Developer Notes

- The loader's deployment logic checks for the memory FS flag and switches to memory FS using NIO APIs if enabled.
- See `CFMLEngineFactory.java` for implementation details.
- For questions or issues, see the main Lucee documentation or open a ticket.
