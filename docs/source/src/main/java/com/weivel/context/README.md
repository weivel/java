`com.weivel.context`: what a plugin sees of the core, and the surface a host makes, opens, ends and frees its contexts through. What a context does is [the context module](../../../../../../../modules/context.md). The public classes are what a plugin and a host use; the package-private ones keep a context's records and route the core's events.

<!-- generated: source-children -->
- [CapabilityCalls.java](../../../../../../../../src/main/java/com/weivel/context/CapabilityCalls.java) - a context's calls about capabilities, each taking the room it needs first
- [CapabilityWatcher.java](../../../../../../../../src/main/java/com/weivel/context/CapabilityWatcher.java) - hears a watched capability: each verified implementation that joins and leaves
- [CapacityAnswerer.java](../../../../../../../../src/main/java/com/weivel/context/CapacityAnswerer.java) - answers a constrained want's questions about one registration
- [CapacityAnswering.java](../../../../../../../../src/main/java/com/weivel/context/CapacityAnswering.java) - puts a constrained want's question to the registration it names and reports the answer
- [ContextState.java](../../../../../../../../src/main/java/com/weivel/context/ContextState.java) - what every context of one host shares: the core, the table, the timer, the log and the contexts
- [EventRouting.java](../../../../../../../../src/main/java/com/weivel/context/EventRouting.java) - the one listener that routes each event to the plugin it concerns, and the core's log to the host's
- [HostLog.java](../../../../../../../../src/main/java/com/weivel/context/HostLog.java) - where a host directs the log of its plugins and of the core
- [Listening.java](../../../../../../../../src/main/java/com/weivel/context/Listening.java) - a plugin's listener, until it stops
- [LogRecord.java](../../../../../../../../src/main/java/com/weivel/context/LogRecord.java) - one line of the host's log
- [PendingWant.java](../../../../../../../../src/main/java/com/weivel/context/PendingWant.java) - a want of a plugin's, and its settlement
- [PluginContext.java](../../../../../../../../src/main/java/com/weivel/context/PluginContext.java) - what a plugin sees of the core, and the only way it reaches any of it
- [PluginContexts.java](../../../../../../../../src/main/java/com/weivel/context/PluginContexts.java) - the contexts of every plugin of one host, and the surface a host builds and ends them through
- [PluginEvent.java](../../../../../../../../src/main/java/com/weivel/context/PluginEvent.java) - what the core reports about a plugin, as its listeners hear it
- [PluginEventListener.java](../../../../../../../../src/main/java/com/weivel/context/PluginEventListener.java) - hears what the core reports about one plugin
- [PluginLog.java](../../../../../../../../src/main/java/com/weivel/context/PluginLog.java) - a plugin's log, written to the host's log with the plugin named
- [Records.java](../../../../../../../../src/main/java/com/weivel/context/Records.java) - the layer's objects a context keeps in the table under its plugin's handles
- [Releases.java](../../../../../../../../src/main/java/com/weivel/context/Releases.java) - the registrations made awaiting releases, and each revoked holding until its holder lets go
- [ServiceCalls.java](../../../../../../../../src/main/java/com/weivel/context/ServiceCalls.java) - a context's calls about services, each taking the room it needs first
- [ServiceRegistration.java](../../../../../../../../src/main/java/com/weivel/context/ServiceRegistration.java) - a service a plugin registered, until it unregisters it or stops
- [ServiceWatcher.java](../../../../../../../../src/main/java/com/weivel/context/ServiceWatcher.java) - hears a watched service: each later registration and each unregistration
- [WantLimits.java](../../../../../../../../src/main/java/com/weivel/context/WantLimits.java) - how long a plugin's want may wait, each limit handed to the core unchanged
- [Watching.java](../../../../../../../../src/main/java/com/weivel/context/Watching.java) - a watch of a service or a capability, until it stops
- [package-info.java](../../../../../../../../src/main/java/com/weivel/context/package-info.java) - the package's summary in its Javadoc
<!-- end generated -->
