The layer suite: one test per vector of the pinned kit, named by the vector's id, and the coverage test that fails on a vector no test names. What the suite witnesses and why it is the strong check behind the kit's `layers` check is [the conformance module](../../../../../../../modules/conformance.md).

<!-- generated: source-children -->
- [DescriptionVectorsTest.java](../../../../../../../../src/test/java/com/weivel/conformance/DescriptionVectorsTest.java) - one test per `description` vector, comparing the whole `then`
- [DriverStrictnessTest.java](../../../../../../../../src/test/java/com/weivel/conformance/DriverStrictnessTest.java) - a vector part the driver does not drive as written fails the run
- [KitFixture.java](../../../../../../../../src/test/java/com/weivel/conformance/KitFixture.java) - the pinned kit's layer vectors and a driver over the real core and standard
- [LayerCoverageTest.java](../../../../../../../../src/test/java/com/weivel/conformance/LayerCoverageTest.java) - every layer vector of the pinned kit is the display name of a test here
- [LayerVectorsTest.java](../../../../../../../../src/test/java/com/weivel/conformance/LayerVectorsTest.java) - one test per `consume`, `library`, `lifecycle` and `want` vector, through the host on the real core
<!-- end generated -->
