# `com.weivel.context`

What a plugin sees of the core, as the core's `docs/contract/context.md` asks of a language's `context` module. Its
classes are listed in [its source doc](../source/src/main/java/com/weivel/context/README.md).

`PluginContext` is what a plugin sees of the core, and the only way it reaches any of it: it gets, wants, watches, lists
and registers services, provides, lists and watches capabilities, listens to what the core reports about it (a
revocation, a refusal of reach, a degraded settlement), writes to the host's log under its own name, and reads its
manifest value. A resolved service or implementation is the provider's own object; a want's settlement is a
`CompletableFuture` completed once, from a dispatch step, always normally, with the core's answer; a refusal of the core
is answered as the core gave it, never as an empty answer. A registration may offer a `CapacityAnswerer` for a
constrained want's questions, and one that offers none answers with no live attributes and that it serves everyone.
What a plugin's listener, watcher or answerer throws goes to the error sink.

`PluginContexts` is the surface a host builds them through: `make` builds a plugin's context from its handle and
manifest value no later than its first open, `open` opens it with `payloadOf` as its payload, `release` and
`quarantine` make that call and, in its hold, queue the end of its entries in the layer's tables behind the call's own
events, after which it hears nothing until `open` opens it again, `forget` forgets it in the core and detaches its
context in the same hold of the layer lock, after which every call through it answers `Detached` without reaching the
core, before any check of the caller's mistake, and ends its entries once the events drained before the forget are
dispatched, as an unregistration ends its registration's and a stopped watch its watch's, and `close` frees them once
the core is destroyed. One listener on the core routes every event to the plugin it concerns, by the handle it names
through the table, and the core's own log events to the host's log at their level. A context takes the layer lock
through the wrapper and the table's lock inside it, never the other way, and has no lock of its own.
