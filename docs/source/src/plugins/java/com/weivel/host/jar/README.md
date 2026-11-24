The classes packed into the test plugins' JAR: an entry the host loads by its class name through `Host.loadEntry`, which the standard's discovery tests also find on disk, and two classes that show when the host's class loader loads and closes. How the host loads an entry from a JAR is [the host module](../../../../../../../../modules/host/README.md).

<!-- generated: source-children -->
- [GreetingEntry.java](../../../../../../../../../src/plugins/java/com/weivel/host/jar/GreetingEntry.java) - an entry that registers a greeting under its own namespace
- [Unloaded.java](../../../../../../../../../src/plugins/java/com/weivel/host/jar/Unloaded.java) - a class nothing loads before the host is closed, so loading it then proves the loader closed
- [Untouched.java](../../../../../../../../../src/plugins/java/com/weivel/host/jar/Untouched.java) - a class loaded only once its plugin's destroy has finished, so the loader is still open then
<!-- end generated -->
