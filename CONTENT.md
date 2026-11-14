## What this is

The Java language implementation of weivel, for a host written in Java that runs plugins written in Java. It is one
of the language implementations the core's `CONTRACT.md` describes, held to that contract and to the vectors in
`weivel/conformance`, and it decides nothing itself: compatibility, order, reach, waiting and revocation are the
core's answers.

## What it is not

It is not the core, and it carries no C. It reaches the core's binary through one native seam in `com.weivel.core`,
loading it from the path its caller gives, so one build of the layer runs against whichever core binary its caller
loads. The jar is multi-release: on Java 22 and later the seam is Java's foreign function and memory API, on Java 8 to
21 it is JNA, chosen by the Java that runs it with no flag. Both read every struct through one table of offsets written
by hand, which the tests compare field by field with what the C compiler laid out. It forces no path, file name,
variable or format on its caller.

## Modules

`src/main/java/com/weivel/` holds the modules every language implementation of weivel holds, as the core's `MODULES`
list names them, one package each: `com.weivel.core`, `com.weivel.handles`, `com.weivel.timer`, `com.weivel.context`,
`com.weivel.host`, `com.weivel.standard`, `com.weivel.conformance` and `com.weivel.benchmarks`. Each package's tests
are in `src/test/java/com/weivel/<module>/`.

## What is built

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
queue and returns. The wrapper answers every call with an `Answer`: `Ok`, `Absent`, `Refused` with the refusal the
core queued for it (never one the layer invents), `Destroyed`, `Defect`, which also goes to the error sink, or
`Mistake`, the caller's own error found before anything reached the core, such as a text holding a NUL character,
which C would read cut short; `Detached` is answered only through a context. What a listener or a deferred step
throws goes to the error sink, never to a caller, and a sink that throws goes to the standard error output. A
`VirtualMachineError` is rethrown instead, once the dispatcher is free again, and the events and steps still queued
behind it are delivered by the next call that dispatches.

The wrapper also holds what the `timer`, `context` and `host` packages build on, reached through `CoreInternals`:
a call with a guard and a step run under the lock, a call whose deferred step joins the queue right behind its own
events in the same hold, a deferred step on the dispatch queue, how many events have been drained, and the deadline
hooks, installed by one timer and taken off only by it: the latest deadline announcement recorded at drain time, the
timer told of it before any event of its call is dispatched, and a deadline that fell due handed to the core before
the next call.

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
One timer follows one core: a second started while one follows is the caller's mistake, and a timer's destroy takes
off its own hooks and nothing else.
`Schedulers.system()` is one shared daemon thread reading `System.nanoTime()`; `Scheduler.none()` arms nothing and
reads no clock.

`PluginContext` is what a plugin sees of the core, and the only way it reaches any of it: it gets, wants, watches, lists
and registers services, provides, lists and watches capabilities, listens to what the core reports about it (a
revocation, a refusal of reach, a degraded settlement), writes to the host's log under its own name, and reads its
manifest value. A resolved service or implementation is the provider's own object; a want's settlement is a
`CompletableFuture` completed once, from a dispatch step, always normally, with the core's answer; a refusal of the core
is answered as the core gave it, never as an empty answer. A registration may offer a `CapacityAnswerer` for a
constrained want's questions, and one that offers none answers with no live attributes and that it serves everyone.
What a plugin's listener, watcher or answerer throws goes to the error sink. `PluginContexts` is the surface a host
builds them through: `make` builds a plugin's context from its handle and manifest value no later than its first open,
`payloadOf` is what the host opens it with, `endEntries` ends its entries in the layer's tables once its release's or
quarantine's own events are dispatched, `forget` forgets it in the core and detaches its context in the same hold of the
layer lock, after which every call through it answers `Detached` without reaching the core, and `close` frees them
once the core is destroyed. One listener on the core routes every event to the plugin it concerns, by the handle it
names through the table, and the core's own log events to the host's log at their level. A context takes the layer
lock through the wrapper and the table's lock inside it, never the other way, and has no lock of its own.

The other packages hold only their purpose so far.

## Using it

The layer needs Java 8 or later, JNA on the class path below Java 22, and the core's shared binary. Java 22 and later
refuse, or warn about, native access a launch did not grant, so a process using the layer from the class path there
runs with `--enable-native-access=ALL-UNNAMED`. Any thread may call; a call into the core is a short downcall, during
which a virtual thread pins its carrier.

## Building

`./gradlew check` builds and tests everything. The wrapper fetches its pinned Gradle, and Gradle fetches the Java 8 and
Java 25 toolchains when they are not installed; the native build needs CMake 3.15 or later and a C compiler on the
`PATH`. The build reads the core, the standard and the conformance kit from `siblings/core`, `siblings/standard` and
`siblings/conformance` at the commits `.weivel/repo.json` pins: CI checks them out there, and locally they are links or
clones of those checkouts. Gradle builds the core's shared library, its echo test library and its `layouts` program, and
the standard's shared library and `layouts` program, with CMake in Release, and the tests run against those binaries.
Each native build declares every sibling's sources it compiles as its inputs, the core's among the standard's, so a
change to either rebuilds it.

Java 8 compiles every source but the foreign function backend, which Java 25 compiles at release 22 into the jar's
`META-INF/versions/22`. `check` runs the whole suite twice against the built jar, never against the class
directories, so the multi-release choice is the one a caller gets: `test` on Java 25 through the foreign function
API, and `testJava8` on Java 8 through JNA, each asserting which backend it ran on. Javadoc runs on Java 25, at
release 8 for the jar's base and at release 22 for the Java 22 sources.

Gradle's file system watching may not see a change made behind a link: a sibling linked in as a junction or a
symbolic link can be edited without the next build noticing, which then reports the native tasks up to date. Run
with `--no-watch-fs` to have Gradle read the files again, or `--rerun-tasks` to rebuild everything.
