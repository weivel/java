# `com.weivel.core`

Loads the core's binary and wraps every call into it, as the core's `docs/contract/core.md` asks of a language's
`core` module. Its classes are listed in [its source doc](../source/src/main/java/com/weivel/core/README.md), and the
native seam's foreign function backend in [the Java 22 sources' doc](../source/src/main/java22/com/weivel/core/README.md).

`CoreBinary.load` opens the core's shared binary from the path its caller gives and resolves every function the
core's header declares into one table of native functions. A path that is no library, and a library that lacks one of
the core's functions, are each answered as a value, the second naming the first function missing.
`CoreBinary.close` closes what the load opened.

`Core.create` creates a core through that table, and every call into it goes through one wrapper, which nothing else
in the layer bypasses: `CoreCalls` builds each call, `Core.call` makes it. A call is made under one `ReentrantLock`
and its events are drained under the same hold, each copied into a `CoreEvent` value and released in the core
before the next is drained; an event that cannot be copied whole is still heard, marked incomplete, with fixed
sentences in place of its texts. The events are dispatched to the listeners first in, first out, outside the lock, by
one thread at a time: a call made while a dispatch runs, from a listener or another thread, joins the end of the
queue and returns. A call's arguments are laid out in scratch memory the wrapper keeps and uses only under its lock,
grown when a call needs more, so a warm call allocates no native memory. The wrapper answers every call with an
`Answer`: `Ok`, `Absent`, `Refused` with the refusal the core queued for it (never one the layer invents), `Destroyed`,
`Defect`, which also goes to the error sink, or `Mistake`, the caller's own error found before anything reached the
core, such as a text holding a NUL character, which C would read cut short; `Detached` is answered only through a
context. What a listener or a deferred step throws goes to the error sink, never to a caller, and a sink that throws
goes to the standard error output. The one exception is a fatal runtime error, one after which the runtime itself
cannot be trusted to go on: a `VirtualMachineError` propagates, once the layer has restored its own state. The
dispatcher is free again, the events and steps still queued behind it are delivered by the next call that dispatches,
and an activate that threw one is marked returned, so a release still deactivates and releases its plugin.

`CoreInternals`, `ForeignLibrary`, `ForeignMemory` and `BareServiceGet` are public only so the layer's own packages
can reach them: Java 8 has no module system, so it has no way to hide a type its own packages share, and as the
contract asks of such a language it keeps them public and documents them as unsafe for a caller, who can
run code under the layer lock through `CoreInternals`, call into native code past the wrapper through
`BareServiceGet`, read or write any native address through `ForeignMemory`, and call any native address through
`ForeignFunction.at`.

The wrapper also holds what the `timer`, `context` and `host` packages build on, reached through `CoreInternals`:
a call with a guard and a step run under the lock, a call whose deferred step joins the queue right behind its own
events in the same hold, a deferred step on the dispatch queue, how many events have been drained, and the deadline
hooks, installed by one timer and taken off only by it: the latest deadline announcement recorded at drain time, the
timer told of it before any event of its call is dispatched, and a deadline that fell due handed to the core before
the next call.
