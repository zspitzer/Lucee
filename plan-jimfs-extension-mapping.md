
# Plan: Generic ResourceProvider Mapping for Lucee Server (Jimfs and Beyond)

## 1. Review Current Provider Mapping in `CFMLEngineFactory`

- Examine how all directories under `lucee-server` (as returned by `getResourceRoot()`) are mapped to `ResourceProvider` instances in `CFMLEngineFactory.java`.
- Identify the location and mechanism for injecting or overriding the provider for any subdirectory (not just `extensions/installed`).
- Goal: Allow any resource-backed storage (config, extensions, logs, temp, etc.) to be mapped to Jimfs or any custom provider.

## 2. Draft JimfsResourceProvider Implementation Plan

- Define requirements for a `JimfsResourceProvider` that exposes a Jimfs `FileSystem` as a Lucee `ResourceProvider`.
- Ensure the provider implements all necessary methods for compatibility with `Resource` and `ResourceUtil` APIs (e.g., file creation, deletion, move, copy, directory listing, streams).
- Plan for initialization, shutdown, and cleanup of the Jimfs filesystem.
- Design for generic use: provider should be able to serve any directory under `lucee-server`, not just extensions.

## 3. Describe Configuration for Jimfs Mapping

- Document how to configure Lucee (in `CFMLEngineFactory` or elsewhere) to map any directory under `lucee-server` (from `getResourceRoot()`) to the `JimfsResourceProvider`.
- Provide example code or configuration snippet for registering the provider and mapping arbitrary subdirectories.
- Note any system properties or environment variables that may be used for dynamic configuration.

## 4. Summarize Integration with Resource-backed Logic

- Explain that all logic using the `Resource` abstraction (including but not limited to `moveToInstalled` in `RHExtension`) will work transparently with Jimfs or any provider once mapped.
- Confirm that no changes are needed in core logic; all file operations are routed through the configured provider.
- Optionally, describe how to log or inspect the contents of the Jimfs filesystem for debugging or audit purposes.

---

This plan ensures that all resource-backed storage and logic in Lucee (not just extensions) can be routed through the configured `ResourceProvider`, enabling in-memory operation with Jimfs or any other backend, with no changes required in the core logic.
