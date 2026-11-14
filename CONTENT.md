## What this is

The Java language implementation of weivel, for a host written in Java that runs plugins written in Java. It is one
of the language implementations the core's `CONTRACT.md` describes, held to that contract and to the vectors in
`weivel/conformance`, and it decides nothing itself: compatibility, order, reach, waiting and revocation are the
core's answers.

## What it is not

It is not the core, and it carries no C. It reaches the core's binary through Java's foreign function and memory API
alone, loading it from the path its caller gives, so one build of the layer runs against whichever core binary its
caller loads. It forces no path, file name, variable or format on its caller.

## Modules

`src/main/java/com/weivel/` holds the modules every language implementation of weivel holds, as the core's `MODULES`
list names them, one package each: `com.weivel.core`, `com.weivel.handles`, `com.weivel.timer`, `com.weivel.context`,
`com.weivel.host`, `com.weivel.standard`, `com.weivel.conformance` and `com.weivel.benchmarks`. Each package's tests
are in `src/test/java/com/weivel/<module>/`.

## What is built

`CoreBinary.load` opens the core's shared binary from the path its caller gives and resolves every function the
core's header declares into one table of downcall handles. A path that is no library, and a library that lacks one of
the core's functions, are each answered as a value, the second naming the first function missing.
`CoreBinary.close` closes what the load opened.

`Core.create` creates a core through that table, and every call into it goes through one wrapper, which nothing else
in the layer bypasses: `CoreCalls` builds each call, `Core.call` makes it. A call is made under one `ReentrantLock`
and its events are drained under the same hold, each copied into a `CoreEvent` record and released in the core
before the next is drained; an event that cannot be copied whole is still heard, marked incomplete, with fixed
sentences in place of its texts. The events are dispatched to the listeners first in, first out, outside the lock, by
one thread at a time: a call made while a dispatch runs, from a listener or another thread, joins the end of the
queue and returns. The wrapper answers every call with an `Answer`: `Ok`, `Absent`, `Refused` with the refusal the
core queued for it (never one the layer invents), `Destroyed`, `Defect`, which also goes to the error sink, or
`Mistake`, the caller's own error found before anything reached the core, such as a text holding a NUL character,
which C would read cut short; `Detached` is answered only through a context. What a listener or a deferred step
throws goes to the error sink, never to a caller, and a sink that throws goes to the standard error output. A
`VirtualMachineError` is rethrown instead, once the dispatcher is free again, and the events and steps still queued
behind it are delivered by the next call that dispatches.

The wrapper also holds what the `timer`, `context` and `host` packages build on, reached through `CoreInternals`:
a call with a guard and a step run under the lock, a deferred step on the dispatch queue, and the deadline hooks, the
latest deadline announcement recorded at drain time, the timer told of it before any event of its call is dispatched,
and a deadline that fell due handed to the core before the next call.

`Handles` is the table from the core's handles to the layer's objects: an entry per plugin, service registration,
capability implementation, watch and want, made in room a reservation took before the call, from the step the wrapper
runs before any event of that call is dispatched, and ended by its caller when what the handle stands for ends; and the
holdings, what each plugin was handed for another's registration or implementation, kept by the pair of the two handles
and dropped by the revocation that names them, even for a plugin already forgotten. A handle the table does not hold
answers nothing. Each entry carries a token a plugin is opened with as its payload, which leads back to the entry
without a search. A lookup costs one probe of a hash table keyed by primitive `long`s and allocates nothing; the table
has one lock of its own, always the innermost.

`CoreTimer` arms the core's next deadline on the caller's `Scheduler`, which owns the clock: it follows the latest
deadline announcement drained and nothing else, hands the core the time through the wrapper when its task runs, waits
in steps on a scheduler that cannot wait as long, and gives the wrapper the clock for its check before each call.
`Schedulers.system()` is one shared daemon thread reading `System.nanoTime()`; `Scheduler.none()` arms nothing and
reads no clock.

The other packages hold only their purpose so far.

## Using it

The layer needs Java 25 and the core's shared binary. Java refuses, or warns about, native access a launch did not
grant, so a process using the layer from the class path runs with `--enable-native-access=ALL-UNNAMED`. Any thread
may call; a call into the core is a short downcall, during which a virtual thread pins its carrier.

## Building

`./gradlew check` builds and tests everything. The wrapper fetches its pinned Gradle, and Gradle fetches a Java 25
toolchain when none is installed; the native build needs CMake 3.15 or later and a C compiler on the `PATH`. The
build reads the core, the standard and the conformance kit from `siblings/core`, `siblings/standard` and
`siblings/conformance` at the commits `.weivel/repo.json` pins: CI checks them out there, and locally they are links
or clones of those checkouts. Gradle builds the core's shared library, its echo test library and its `layouts`
program, and the standard's shared library and `layouts` program, with CMake in Release, and the tests run against
those binaries. Each native build declares every sibling's sources it compiles as its inputs, the core's among the
standard's, so a change to either rebuilds it.

Gradle's file system watching may not see a change made behind a link: a sibling linked in as a junction or a
symbolic link can be edited without the next build noticing, which then reports the native tasks up to date. Run
with `--no-watch-fs` to have Gradle read the files again, or `--rerun-tasks` to rebuild everything.
