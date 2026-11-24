The tests of `com.weivel.standard`, against the standard's real binary, and the test that the host works without it. What they hold the package to is [the standard module](../../../../../../../modules/standard.md).

<!-- generated: source-children -->
- [DefaultsTest.java](../../../../../../../../src/test/java/com/weivel/standard/DefaultsTest.java) - service ids, identities, the mirror list and SHA-256, as the standard answers them
- [DiscoveryTest.java](../../../../../../../../src/test/java/com/weivel/standard/DiscoveryTest.java) - plugins found on disk, registered by their caller with an entry it loads
- [LayerIndependenceTest.java](../../../../../../../../src/test/java/com/weivel/standard/LayerIndependenceTest.java) - no class of `core`, `handles`, `timer`, `context` or `host` depends on `standard`, `conformance` or `benchmarks`, read with `jdeps`
- [LibraryDeclarationsTest.java](../../../../../../../../src/test/java/com/weivel/standard/LibraryDeclarationsTest.java) - a library declaration read and loaded through the host
- [ManifestsTest.java](../../../../../../../../src/test/java/com/weivel/standard/ManifestsTest.java) - a JSON manifest read, validated and registered, and the refusals naming each wrong field
- [ReleasesTest.java](../../../../../../../../src/test/java/com/weivel/standard/ReleasesTest.java) - every struct the standard fills is handed to its release, even when another release fails
- [ServicesTest.java](../../../../../../../../src/test/java/com/weivel/standard/ServicesTest.java) - the service interfaces carry exactly the operations the standard's header defines
- [StandardBinaryTest.java](../../../../../../../../src/test/java/com/weivel/standard/StandardBinaryTest.java) - the standard's binary loads and closes, and its layouts match the C compiler's
- [StandardFixture.java](../../../../../../../../src/test/java/com/weivel/standard/StandardFixture.java) - the standard's real binary, loaded once, and the files the build hands the tests
<!-- end generated -->
