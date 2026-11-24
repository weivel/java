`com.weivel.conformance`: the driver for the layer suite, one run class per op of the kit's layer vectors, a host on the real core and the suite's own clock. What it drives and how the suite is held to every vector is [the conformance module](../../../../../../../modules/conformance.md); the tests that call it are [the conformance tests](../../../../../test/java/com/weivel/conformance/README.md).

<!-- generated: source-children -->
- [ConsumeRun.java](../../../../../../../../src/main/java/com/weivel/conformance/ConsumeRun.java) - drives a `consume` vector: its plugins opened, then its steps through the contexts or the host
- [DescriptionRun.java](../../../../../../../../src/main/java/com/weivel/conformance/DescriptionRun.java) - drives a `description` vector through the standard's described library and the bound definitions
- [DriverWorld.java](../../../../../../../../src/main/java/com/weivel/conformance/DriverWorld.java) - a host on the real core and the suite's own time, over plugins the driver builds
- [KitRefusals.java](../../../../../../../../src/main/java/com/weivel/conformance/KitRefusals.java) - a refusal as a vector states it, its code by the kit's name
- [LayerDriver.java](../../../../../../../../src/main/java/com/weivel/conformance/LayerDriver.java) - the driver: reads the kit's layer vectors and runs each through the host, answering a witness
- [LayerVector.java](../../../../../../../../src/main/java/com/weivel/conformance/LayerVector.java) - one layer vector, as the standard's JSON reader read it
- [LayerVectors.java](../../../../../../../../src/main/java/com/weivel/conformance/LayerVectors.java) - reads every layer vector under a directory through the standard's JSON reader
- [LayerWitness.java](../../../../../../../../src/main/java/com/weivel/conformance/LayerWitness.java) - what the driver saw of one vector's run
- [LibraryRun.java](../../../../../../../../src/main/java/com/weivel/conformance/LibraryRun.java) - drives a `library` vector through the core's echo library
- [LifecycleRun.java](../../../../../../../../src/main/java/com/weivel/conformance/LifecycleRun.java) - drives a `lifecycle` vector: each entry's actions and the host's steps in order
- [Manifests.java](../../../../../../../../src/main/java/com/weivel/conformance/Manifests.java) - a vector's plugins as manifest values, every declaration as written
- [Observations.java](../../../../../../../../src/main/java/com/weivel/conformance/Observations.java) - answers a vector's `then` field by field from what a run shows
- [Shapes.java](../../../../../../../../src/main/java/com/weivel/conformance/Shapes.java) - reads the parts of a vector a driver drives, refusing a key or shape no driver reads
- [StandardJson.java](../../../../../../../../src/main/java/com/weivel/conformance/StandardJson.java) - the standard's JSON reader, bound from its binary, reading a document into Java values
- [VirtualTime.java](../../../../../../../../src/main/java/com/weivel/conformance/VirtualTime.java) - the clock and scheduler the suite owns, where time moves only when the suite moves it
- [WantRun.java](../../../../../../../../src/main/java/com/weivel/conformance/WantRun.java) - drives a `want` vector on the suite's own time
- [package-info.java](../../../../../../../../src/main/java/com/weivel/conformance/package-info.java) - the package's summary in its Javadoc
<!-- end generated -->
