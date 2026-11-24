`com.weivel.host`: the host that registers, activates, verifies, releases and destroys plugins written in Java, offers services of its own, and loads C libraries as host services. What the host does is [the host module](../../../../../../../modules/host/README.md), and loading a C library [C libraries as host services](../../../../../../../modules/host/libraries.md).

<!-- generated: source-children -->
- [Activation.java](../../../../../../../../src/main/java/com/weivel/host/Activation.java) - what `Host.activate` answers: the plan, and each plugin's outcome as a stage
- [ActivationState.java](../../../../../../../../src/main/java/com/weivel/host/ActivationState.java) - where one plugin's activation ended
- [Deactivating.java](../../../../../../../../src/main/java/com/weivel/host/Deactivating.java) - the deactivate an entry may offer, called while its plugin is still whole
- [DefinitionHold.java](../../../../../../../../src/main/java/com/weivel/host/DefinitionHold.java) - the hold one definition service keeps on its library's binary until nothing holds it
- [DestroyResult.java](../../../../../../../../src/main/java/com/weivel/host/DestroyResult.java) - what a host's destroy did, once it has finished
- [Entries.java](../../../../../../../../src/main/java/com/weivel/host/Entries.java) - loads a plugin's entry by its class name, never by a name the layer assumes
- [Entry.java](../../../../../../../../src/main/java/com/weivel/host/Entry.java) - a plugin's entry in Java: what the host calls to activate it
- [Host.java](../../../../../../../../src/main/java/com/weivel/host/Host.java) - the host: registration, activation, verification, release, host services, C libraries and destroy
- [HostOptions.java](../../../../../../../../src/main/java/com/weivel/host/HostOptions.java) - how a host is made, each part replaced by a copy
- [HostService.java](../../../../../../../../src/main/java/com/weivel/host/HostService.java) - a service the host registered under `WEIVEL_HOST`
- [LibraryAnswer.java](../../../../../../../../src/main/java/com/weivel/host/LibraryAnswer.java) - what one call through a `LibraryService` answered
- [LibraryChecks.java](../../../../../../../../src/main/java/com/weivel/host/LibraryChecks.java) - what of a library declaration is the caller's mistake, found before anything is opened
- [LibraryDeclaration.java](../../../../../../../../src/main/java/com/weivel/host/LibraryDeclaration.java) - a C library's declaration as a Java value: its name, version, release symbol and services
- [LibraryLifetime.java](../../../../../../../../src/main/java/com/weivel/host/LibraryLifetime.java) - who holds a C library's binary open, and when it closes
- [LibraryService.java](../../../../../../../../src/main/java/com/weivel/host/LibraryService.java) - the object a bytes service is registered with: one function per declared function, called by name
- [LoadedLibrary.java](../../../../../../../../src/main/java/com/weivel/host/LoadedLibrary.java) - a C library the host loaded as host services, kept until it is unloaded
- [Opening.java](../../../../../../../../src/main/java/com/weivel/host/Opening.java) - how the host opens a plugin, the seam a test fails an open through
- [Outcome.java](../../../../../../../../src/main/java/com/weivel/host/Outcome.java) - where one plugin's activation ended, and the core's refusal that decided it
- [Pauses.java](../../../../../../../../src/main/java/com/weivel/host/Pauses.java) - the points in the host's steps where a test holds a thread
- [PluginRecord.java](../../../../../../../../src/main/java/com/weivel/host/PluginRecord.java) - what the host keeps of one plugin, from its registration until it is forgotten
- [package-info.java](../../../../../../../../src/main/java/com/weivel/host/package-info.java) - the package's summary in its Javadoc
<!-- end generated -->
