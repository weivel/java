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
queue and returns. A call's arguments are laid out in scratch memory the wrapper keeps and uses only under its lock,
grown when a call needs more, so a warm call allocates no native memory. The wrapper answers every call with an `Answer`: `Ok`, `Absent`, `Refused` with the refusal the
core queued for it (never one the layer invents), `Destroyed`, `Defect`, which also goes to the error sink, or
`Mistake`, the caller's own error found before anything reached the core, such as a text holding a NUL character,
which C would read cut short; `Detached` is answered only through a context. What a listener or a deferred step
throws goes to the error sink, never to a caller, and a sink that throws goes to the standard error output. The one
exception is a fatal runtime error, one after which the runtime itself cannot be trusted to go on: a
`VirtualMachineError` propagates, once the layer has restored its own state. The dispatcher is free again, the events
and steps still queued behind it are delivered by the next call that dispatches, and an activate that threw one is
marked returned, so a release still deactivates and releases its plugin.

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
`Schedulers.system()` makes a scheduler of its own on one daemon thread reading `System.nanoTime()`, owned and closed
by whoever made it, its thread running the listeners of the core it fires for. `CoreTimer.start` refuses a scheduler
that can wait no time. `Scheduler.none()` arms nothing and reads no clock.

`PluginContext` is what a plugin sees of the core, and the only way it reaches any of it: it gets, wants, watches, lists
and registers services, provides, lists and watches capabilities, listens to what the core reports about it (a
revocation, a refusal of reach, a degraded settlement), writes to the host's log under its own name, and reads its
manifest value. A resolved service or implementation is the provider's own object; a want's settlement is a
`CompletableFuture` completed once, from a dispatch step, always normally, with the core's answer; a refusal of the core
is answered as the core gave it, never as an empty answer. A registration may offer a `CapacityAnswerer` for a
constrained want's questions, and one that offers none answers with no live attributes and that it serves everyone.
What a plugin's listener, watcher or answerer throws goes to the error sink. `PluginContexts` is the surface a host
builds them through: `make` builds a plugin's context from its handle and manifest value no later than its first open,
`open` opens it with `payloadOf` as its payload, `release` and `quarantine` make that call and, in its hold, queue the
end of its entries in the layer's tables behind the call's own events, after which it hears nothing until `open` opens
it again, `forget`
forgets it in the core and detaches its context in the same hold of the layer lock, after which every call through it
answers `Detached` without reaching the core, before any check of the caller's mistake, and ends its entries once the
events drained before the forget are dispatched, as an unregistration ends its registration's and a stopped watch its watch's, and `close` frees them
once the core is destroyed. One listener on the core routes every event to the plugin it concerns, by the handle it
names through the table, and the core's own log events to the host's log at their level. A context takes the layer
lock through the wrapper and the table's lock inside it, never the other way, and has no lock of its own.

`Host` registers plugins written in Java from a manifest value and an `Entry`, the plugin's
`CompletionStage<?> activate(PluginContext)`, which may also implement `Deactivating`; a throw, a `null` stage or a
stage completing exceptionally is a failure. `Entries.load` makes an entry by its class name from the caller's class
loader, and `Host.loadEntry` from a JAR in a class loader of the host's own, whose parent is the loader of `Entry`;
the host never assumes a name. `activate` asks the core for the plan and starts each plugin of its order in that order
on the calling thread, never waiting for an activate, and answers the plan at once with each plugin's outcome as a
stage: active, quarantined, refused, released or failed. An outcome carries the core's refusal that decided it: a
refused one the refusal of its open, and one quarantined because the core refused its verification that refusal; a
quarantine for a reason of the host's, a failed activate among them, carries none. Where no dispatch runs it starts
them as one step of a dispatch, so what an activate's own calls queue is dispatched once every activate of the plan
has been called; where another thread is dispatching, it starts them as calls made while that dispatch runs, and
their events join the end of that thread's queue. Either way an activate that settles before it returns is verified,
and quarantined if the core refuses, in the same hold of the layer lock, before the next plugin is started; a failed
one is quarantined with a reason saying so, any NUL character in the failure's text written as `\0`, since the
reason is the host's own text and not a caller's mistake. `verify` verifies a plugin as a settled activate is verified
and answers its outcome by the same rule, a quarantine the core's refusal caused carrying that refusal. An activate runs as the dispatcher, so one that blocks on an event another
thread causes deadlocks: an activate that must wait answers a stage and completes it later. `release` calls
the deactivate while the plugin is whole, releases it in the core, and ends its entries and forgets it only once that
release's events are dispatched, as a step behind them inside a dispatch. `registerHostService` offers a service of
the host's own under `WEIVEL_HOST`, which an activation plan counts as provided. `destroy` answers one
`CompletionStage<DestroyResult>`: it releases every plugin opened, the last opened first, waits for the opens and
settlings under way, then disarms the timer and destroys the core, and every other call answers `Destroyed` from the
moment it is called. `ledger` answers the core's ledger, where a plugin quarantined for a refused verification shows
the core's refusal, and `HostOptions.withTimerObserver` hears each move of the host's timer, armed or disarmed.
`close` frees what the host holds, the class loaders it opened among them, and is refused until the destroy has
finished. A host its caller gives no scheduler arms the language's own timer rather than none: it makes one with
`Schedulers.system()` and closes it when it is closed, so a want that needs the time is never the caller's mistake.

`loadLibrary` loads a C library from a `LibraryDeclaration` the caller builds or the standard reads, and the library's
path, and registers each declared service under `WEIVEL_HOST`: a service of bytes functions as a `LibraryService`,
whose functions a consumer calls by name as plain Java calls, and a definition service as the language's own object
carrying the very address its definition's symbol answered, called once at the load: a `MemorySegment` of the
struct's `size` bytes on the foreign function backend, a JNA `Pointer` on JNA. A definition answering NULL, or a
struct whose `size` is below `2 * sizeof(void *)`, refuses the load as the caller's mistake before anything is
registered. A definition is registered awaiting releases: `unloadLibrary` unregisters it at once, each holder hears a
`PluginEvent.Revoked` that `awaitsRelease`, and lets go by handing that revocation to
`PluginContext.releaseHolding` once it no longer uses the object, however it came to hold it, by a get, a want or a
watch; the revocation names the registration, so an object registered under several is let go of under that one
alone. A holder released, quarantined or forgotten has let go with it, and a call through a definition after its
holder let go is undefined. The binary closes once the core's `WEIVEL_EVENT_HOLDINGS_RELEASED` of every definition
service has been dispatched and no bytes call runs in it, or, for a definition the destroy finds still registered,
once the destroy has destroyed the core, since a collected language has no free of the host to wait for; every
address a definition answered is invalid from then on. `LoadedLibrary.definitions()` hands those addresses to a
caller that holds no registration of them, so they die with the binary. A plugin registers awaiting releases with
`PluginContext.registerAwaitingReleases`, and hears `PluginEvent.HoldingsReleased` once nothing holds what it
registered, as it hears any event about it: one its own release or quarantine queued still reaches it, one queued
after those reaches no one. A direct `ByteBuffer` input is lent to the library for the call; a `byte[]` is copied
once into native memory, because lending the heap would hold off garbage collection for the whole C call. The copy and
the cells the answer comes back in share one block, and each library keeps one such block of 256 bytes spare for
whichever call takes it first. A call allocates no native memory when it finds the spare free and needs no more than
it: the 16 bytes of answer cells, plus the copy of a heap input, so a `byte[]` or heap buffer of up to 240 bytes, or
any direct buffer. A larger heap input, or a call that finds the spare taken, allocates a block of its own and frees
it. The body
the library answers is copied once into a Java array and released before the call answers, so no bytes it answered
are ever held past a call. `unloadLibrary`, and the destroy, close a library to calls at once.

`com.weivel.standard` is optional, and no class of the packages above depends on it, which a test reads off the
built jar with `jdeps`. `StandardBinary.load` opens the standard's binary from the caller's path through the same
seam, through `ForeignLibrary`, which binds only the symbols its caller names and never one of the core's, and fails
naming the first function missing. `Standard` answers what the standard answers, each as a `StandardAnswer`: a JSON
manifest validated and read into a `Manifest`, plugins discovered on disk as `DiscoveredPlugin`s, a library declaration read into a
`LibraryDeclaration` for `loadLibrary`, identities, the mirror list, SHA-256, and the three service ids, read from the
standard's functions. Every struct the standard fills is copied into Java values and released through the standard's
own release before the call answers, every release of a call running even when another fails. `readManifest`
reads a manifest without validating it, and `validate` validates one without reading it.
A `DiscoveredPlugin` loads no code: the manifest calls its entry opaque and no module convention is a default the
layer may assume, so its caller registers `manifest()` with an entry it makes itself, from `entryPath()` through
`Host.loadEntry` with a class name the caller gives, or any other way.
`ForeignMemory.manifest` reads a manifest another library laid out up to the size it declares, as the core does. `Config`, `Secrets` and `Paths` are the standard's service definitions as Java
interfaces, a refusal thrown as a `ServiceRefusal`.

`com.weivel.conformance` is the driver for the layer suite. `LayerDriver` reads the pinned kit's layer vectors
through the standard's JSON reader, bound through `ForeignLibrary`, and writes no file; it builds each vector's plugins
as manifest values and entries, drives them through the host's public surface on the real core, a `want` vector on a
clock and scheduler of the suite's own and a `library` vector with the core's echo library, and answers a `LayerWitness` in the shape of the vector's `then`. The suite
has one test per vector, its display name the vector's id, and a coverage test that discovers those names through
the JUnit Platform Launcher and fails on a vector none names.

`com.weivel.benchmarks` holds the benchmark gates, judged as the kit's `RUNNER.md` defines them and bounded by the
pinned kit's `benchmarks/java.txt`, a ratio gate per backend. The same-language gate is the identity check: a consumer's
get answers the provider's own object, and the timing beside it, three paths running the very same compiled call, is
judged only against the platform's noise. `control-call` is `weivel_get_service` through the wrapper, with the core's
timer armed as a host arms it, over the same function of the core's table on the same core, plugin and id called bare
through `CoreInternals.bareServiceGet`, the one route past the wrapper, which reaches that function alone and is
refused once it is closed or the core is destroyed.
`library-call` is the echo library's `echo_echo` through the host's `LibraryService` with a `byte[]`, over the same
function called bare with the same bytes copied in and its answer copied out and released as the host does. Its
definition row is the echo definition's `echo` called through the definition a consumer resolved, over the same
operation called through the pointer `echo_service_definition` answered, each path reading the operation's function
pointer out of the struct once before its timed loop, from the one address it is handed; it is judged by the
platform's noise, as the same-language pair is. It is sampled apart from that pair, in 101 rounds after a discarded
warm-up: on the foreign function backend 1 000 000 warm-up calls and batches of 500 000, on JNA 200 000 warm-up calls
and batches of 20 000, so a batch lasts some milliseconds on each; a foreign function batch of 20 000 calls left two
identical direct paths twice Linux's worst noise apart. Paths are
sampled side by side after a discarded warm-up, one batch of each a round with the first path moving on each round,
and judged on the median of the per-round ratios. A run with nothing measured fails, and so does a backend or platform
the budget gives no line: no bound is never a pass. "Next to no overhead" is judged on the foreign function backend;
JNA is the compatibility path and is bounded at what it measures.

## Using it

The layer needs Java 8 or later, JNA on the class path below Java 22, and the core's shared binary. Java 22 and later
refuse, or warn about, native access a launch did not grant, so a process using the layer from the class path there
runs with `--enable-native-access=ALL-UNNAMED`. Any thread may call; a call into the core is a short downcall, during
which a virtual thread pins its carrier.

## Building

`./gradlew check` builds and tests everything. The wrapper fetches its pinned Gradle, and Gradle fetches the Java 8 and
Java 25 toolchains when they are not installed; the native build needs CMake 3.15 or later and a C compiler on the
`PATH`. The build reads the core, the standard and the conformance kit from `siblings/core`, `siblings/standard` and
`siblings/conformance` at the commits `repo.json` pins: CI checks them out there, and locally they are links or
clones of those checkouts. Gradle builds the core's shared library, its echo test library and its `layouts` program, and
the standard's shared library and `layouts` program, with CMake in Release, and the tests run against those binaries.
Each native build declares every sibling's sources it compiles as its inputs, the core's among the standard's, so a
change to either rebuilds it.

Every Java 8 toolchain is Amazon Corretto, whose Java 8 also ships for macOS on arm64, where the core is built natively.

Java 8 compiles every source but the foreign function backend, which Java 25 compiles at release 22 into the jar's
`META-INF/versions/22`. `check` runs the whole suite twice against the built jar, never against the class directories,
so the multi-release choice is the one a caller gets: `test` on Java 25 through the foreign function API, and
`testJava8` on Java 8 through JNA, each asserting which backend it ran on. Javadoc runs on Java 25, at release 8 for the
jar's base and at release 22 for the Java 22 sources.

`./gradlew benchmark` runs the benchmark gates on both backends, `benchmarkFfm` on Java 25 and `benchmarkJna` on
Java 8, and is never part of `check`: CI runs it as a job of its own on every runner. By default it judges one run of
each gate against `java.txt` of the pinned kit; `-Pweivel.benchmark.mode=measure` judges no timing and prints one
`weivel-benchmark` line per gate for each of `-Pweivel.benchmark.repetitions` runs (10 by default), the samples a
bound is set from. No repository sets its own bound, so the gate reads no budget but the pinned kit's.

Gradle's file system watching may not see a change made behind a link: a sibling linked in as a junction or a
symbolic link can be edited without the next build noticing, which then reports the native tasks up to date. Run
with `--no-watch-fs` to have Gradle read the files again, or `--rerun-tasks` to rebuild everything.
