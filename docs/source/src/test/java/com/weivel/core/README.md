The tests of `com.weivel.core`, and the fixtures the other packages' tests share: real cores made through the wrapper's seams, the files the build hands the tests, and the comparison of every hand layout with the one the C compiler printed. What they hold the package to is [the core module](../../../../../../../modules/core.md).

<!-- generated: source-children -->
- [ActivationCallsTest.java](../../../../../../../../src/test/java/com/weivel/core/ActivationCallsTest.java) - planning and verifying an activation, each answer read as the header says
- [CLayouts.java](../../../../../../../../src/test/java/com/weivel/core/CLayouts.java) - every struct a C `layouts` program prints, as the C compiler laid it out
- [CallPathTest.java](../../../../../../../../src/test/java/com/weivel/core/CallPathTest.java) - a place in a call's input, spelled as a refusal names it
- [CallerTextTest.java](../../../../../../../../src/test/java/com/weivel/core/CallerTextTest.java) - a caller's text holding a NUL character is the caller's mistake, refused before the core
- [ContextCallsTest.java](../../../../../../../../src/test/java/com/weivel/core/ContextCallsTest.java) - the calls a plugin's context makes beyond the wrapper's own
- [CoreBinaryTest.java](../../../../../../../../src/test/java/com/weivel/core/CoreBinaryTest.java) - loading the core's binary: a failure as a value, the first missing function named, and the close
- [CoreFunctionTest.java](../../../../../../../../src/test/java/com/weivel/core/CoreFunctionTest.java) - the table holds every function the header declares, in order, with the header's parameters
- [CoreLayoutsTest.java](../../../../../../../../src/test/java/com/weivel/core/CoreLayoutsTest.java) - every core layout declared by hand matches the C compiler's
- [CoreProbes.java](../../../../../../../../src/test/java/com/weivel/core/CoreProbes.java) - real cores for the other packages' tests, one counting every crossing
- [CountingAccess.java](../../../../../../../../src/test/java/com/weivel/core/CountingAccess.java) - the selected backend with every native allocation and free counted
- [CrossingAllocationTest.java](../../../../../../../../src/test/java/com/weivel/core/CrossingAllocationTest.java) - a warm call allocates no native memory
- [DeadlineHooksTest.java](../../../../../../../../src/test/java/com/weivel/core/DeadlineHooksTest.java) - the wrapper's deadline hooks: recorded at drain time, told before dispatch, due check
- [DescribedBindingTest.java](../../../../../../../../src/test/java/com/weivel/core/DescribedBindingTest.java) - the binder on the described library's real definition and description, or a copy changed in one place
- [DescribedFixture.java](../../../../../../../../src/test/java/com/weivel/core/DescribedFixture.java) - the standard's described test library and its declarations, read by the standard's own reader
- [DescriptionLayoutsTest.java](../../../../../../../../src/test/java/com/weivel/core/DescriptionLayoutsTest.java) - every description and value layout declared by hand matches the C compiler's
- [DispatchTest.java](../../../../../../../../src/test/java/com/weivel/core/DispatchTest.java) - dispatch on one thread: first in, first out, never re-entered, failures to the sink
- [EventCopyTest.java](../../../../../../../../src/test/java/com/weivel/core/EventCopyTest.java) - each drained event copied into Java values and released before the next
- [ForeignLibraryTest.java](../../../../../../../../src/test/java/com/weivel/core/ForeignLibraryTest.java) - a foreign library binds only the symbols it named and none of the core's
- [LayoutComparison.java](../../../../../../../../src/test/java/com/weivel/core/LayoutComparison.java) - compares the layouts a package declares by hand with the ones the C compiler printed
- [LayoutTest.java](../../../../../../../../src/test/java/com/weivel/core/LayoutTest.java) - a Java with eight-byte addresses is accepted and any other refused
- [LedgerCallsTest.java](../../../../../../../../src/test/java/com/weivel/core/LedgerCallsTest.java) - the ledger read, as the conformance driver reads it
- [LibraryFunctionTest.java](../../../../../../../../src/test/java/com/weivel/core/LibraryFunctionTest.java) - a C library's bytes functions through the native seam, against the echo library
- [ManifestShapesTest.java](../../../../../../../../src/test/java/com/weivel/core/ManifestShapesTest.java) - a manifest another library laid out is read up to the size it declares
- [NativeAccessTest.java](../../../../../../../../src/test/java/com/weivel/core/NativeAccessTest.java) - the native seam, held to the same sentences on every backend
- [NativeBackendsTest.java](../../../../../../../../src/test/java/com/weivel/core/NativeBackendsTest.java) - the backend selected is the one the build expects, and only the backends name their API
- [NativeCallAllocationTest.java](../../../../../../../../src/test/java/com/weivel/core/NativeCallAllocationTest.java) - a call through the foreign function backend allocates nothing on the heap
- [NativeFiles.java](../../../../../../../../src/test/java/com/weivel/core/NativeFiles.java) - the files the build hands the tests, by the system property Gradle sets
- [ReusableArenaTest.java](../../../../../../../../src/test/java/com/weivel/core/ReusableArenaTest.java) - the wrapper's scratch memory: reused, grown to one use, zeroed as handed out
- [RunAsTheDispatcherTest.java](../../../../../../../../src/test/java/com/weivel/core/RunAsTheDispatcherTest.java) - a step run as the dispatcher: at once on the calling thread, or refused
- [SpareBlockTest.java](../../../../../../../../src/test/java/com/weivel/core/SpareBlockTest.java) - the one spare block of a library's calls: never shared, freed with the library
- [StructValuesTest.java](../../../../../../../../src/test/java/com/weivel/core/StructValuesTest.java) - a struct's answer as the unmodifiable map a caller is handed
- [TestCores.java](../../../../../../../../src/test/java/com/weivel/core/TestCores.java) - cores for this package's tests, made through the wrapper's seams
- [ThreadsTest.java](../../../../../../../../src/test/java/com/weivel/core/ThreadsTest.java) - the wrapper under several threads, each interleaving forced by gates
- [WrapperBoundaryTest.java](../../../../../../../../src/test/java/com/weivel/core/WrapperBoundaryTest.java) - only the wrapper and the native seam reach the core's functions
- [WrapperCallTest.java](../../../../../../../../src/test/java/com/weivel/core/WrapperCallTest.java) - the wrapper's creation, drain and answers, one test per sentence of the contract
<!-- end generated -->
