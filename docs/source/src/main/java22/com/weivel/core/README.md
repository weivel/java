The foreign function backend of the native seam. On Java 22 and later the jar's `META-INF/versions/22` holds this folder's `NativeBackends`, which replaces the base class of the same name and selects `FfmAccess`, so the Java that runs the jar picks the backend with no flag. Everything above the seam is the base package's, in [the base tree](../../../../java/com/weivel/core/README.md); why the seam has two backends is [the architecture](../../../../../../../README.md).

<!-- generated: source-children -->
- [FfmAccess.java](../../../../../../../../src/main/java22/com/weivel/core/FfmAccess.java) - the native seam on the foreign function and memory API: a shared arena, downcall handles, memory by address
- [NativeBackends.java](../../../../../../../../src/main/java22/com/weivel/core/NativeBackends.java) - selects the foreign function backend, replacing the base class of the same name
<!-- end generated -->
