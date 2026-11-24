The tests of `com.weivel.host`, against the real core, the core's echo library and the standard's described library, with gates that force each interleaving of threads the contract names. What they hold the host to is [the host module](../../../../../../../modules/host/README.md).

<!-- generated: source-children -->
- [DefinitionsTest.java](../../../../../../../../src/test/java/com/weivel/host/DefinitionsTest.java) - definition services: what is registered, when the binary closes, how a holder lets go
- [DescribedLibrariesTest.java](../../../../../../../../src/test/java/com/weivel/host/DescribedLibrariesTest.java) - described definitions through the host, beyond what the kit's vectors witness
- [DestroyTest.java](../../../../../../../../src/test/java/com/weivel/host/DestroyTest.java) - the host's destroy and free, step by step
- [EntriesTest.java](../../../../../../../../src/test/java/com/weivel/host/EntriesTest.java) - a plugin's entry, loaded by class name from the caller's loader or from a JAR
- [FailedOpenTest.java](../../../../../../../../src/test/java/com/weivel/host/FailedOpenTest.java) - an open that fails for a reason other than the core's refusal
- [Gates.java](../../../../../../../../src/test/java/com/weivel/host/Gates.java) - gates that hold a thread at a point of the host's steps
- [HostFixture.java](../../../../../../../../src/test/java/com/weivel/host/HostFixture.java) - a host on the real core with every failure, log line and trace record kept
- [HostRacesTest.java](../../../../../../../../src/test/java/com/weivel/host/HostRacesTest.java) - calls landing inside one of the host's steps on another thread
- [HostTest.java](../../../../../../../../src/test/java/com/weivel/host/HostTest.java) - the host section of the contract, step by step
- [HostThreadsTest.java](../../../../../../../../src/test/java/com/weivel/host/HostThreadsTest.java) - the host's steps another thread can interleave with, forced by gates
- [LibrariesTest.java](../../../../../../../../src/test/java/com/weivel/host/LibrariesTest.java) - C libraries as host services, against the echo library
- [LibraryPauses.java](../../../../../../../../src/test/java/com/weivel/host/LibraryPauses.java) - the host's library steps as a test chooses them
- [LibraryRacesTest.java](../../../../../../../../src/test/java/com/weivel/host/LibraryRacesTest.java) - a library's load, calls and unload interleaved with registrations and the destroy
- [TestEntry.java](../../../../../../../../src/test/java/com/weivel/host/TestEntry.java) - an entry whose activate and deactivate the test chooses, each traced
<!-- end generated -->
