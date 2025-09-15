
# Plan: Jimfs-Only Felix Bundle Cache for Lucee

## Objective
Ensure the Felix (OSGi) bundle cache is fully in-memory when Jimfs is enabled, with no disk writes except for logs.

## Steps

1. **Centralize Jimfs Path Management**
   - Store the Jimfs `FileSystem` instance in a static or singleton field (already in `CFMLEngineFactory`).
   - Expose a method to get the Jimfs `Path` for the Felix cache (e.g., `getFelixCachePath()`).

2. **Update Felix Configuration**
   - When Jimfs is enabled, set `org.osgi.framework.storage` to the Jimfs `Path`.
   - Ensure all Felix and OSGi APIs use NIO `Path` and not `File` for cache operations.

3. **Audit All Disk Writes**
    - **Audit Results:**
       - The following code paths in `core/src/main/java` use disk-write APIs (`File`, `FileOutputnoperate on abstractions (`Resource`, `Path`, or `Files`) and let the resource provider/factory handle whether the storage is in-memory (Jimfs) or on disk. Disk fallback should only occur for logs or unavoidable third-party requirements, with clear logging.
   - Refactor all core classes to use only NIO/Resource APIs for file operations, delegating storage backend (Jimfs or disk) to the resource abstraction. Never add Jimfs or disk logic to core logic or utility classes themselves.

4. **Disk Fallback Only for Logs**
   - Allow disk fallback only for logs or if a third-party library absolutely requires a disk file.
   - Log a warning whenever a disk fallback occurs, including the reason.

5. **Testing and Validation**
   - Run with Jimfs enabled and verify that no files (except logs) appear in `D:\work\lucee7\temp\in_memory`.
   - Add tests to ensure the cache is fully in-memory and persists for the JVM lifetime.

6. **Documentation**
   - Document the Jimfs-only cache behavior and fallback policy in the project README or developer docs.

---

## Notes
- Jimfs is only intended for testing/development, not production.
- All bundle cache operations must use NIO `Path` APIs and the Jimfs filesystem when enabled.
- Provide clear logging for any disk fallback, and document all exceptions.
