The tests of `com.weivel.context`, against the real core. What they hold a context to is [the context module](../../../../../../../modules/context.md).

<!-- generated: source-children -->
- [AwaitingReleasesTest.java](../../../../../../../../src/test/java/com/weivel/context/AwaitingReleasesTest.java) - registrations awaiting releases: which holding a revocation lets go of, and a forget
- [CapabilitiesTest.java](../../../../../../../../src/test/java/com/weivel/context/CapabilitiesTest.java) - a plugin's capabilities: provided, listed and watched
- [CapacityTest.java](../../../../../../../../src/test/java/com/weivel/context/CapacityTest.java) - capacity answering against the real core's constrained wants
- [ContextFixture.java](../../../../../../../../src/test/java/com/weivel/context/ContextFixture.java) - a real core with its handles, its timer and the contexts made over them
- [ContextThreadsTest.java](../../../../../../../../src/test/java/com/weivel/context/ContextThreadsTest.java) - contexts under several threads, and the order their locks are taken in
- [EventsTest.java](../../../../../../../../src/test/java/com/weivel/context/EventsTest.java) - what a plugin hears, its log and its manifest, and listeners that never reach a caller
- [LifecycleTest.java](../../../../../../../../src/test/java/com/weivel/context/LifecycleTest.java) - a context's life: made, ending its entries, kept across a quarantine, detached
- [ServicesTest.java](../../../../../../../../src/test/java/com/weivel/context/ServicesTest.java) - a plugin's services, each sentence of the contract's `context` section about them
- [WantTest.java](../../../../../../../../src/test/java/com/weivel/context/WantTest.java) - a plugin's wants: each settled once, from a dispatch step, its limits handed over unchanged
<!-- end generated -->
