# `com.weivel.benchmarks`

The Java half of the benchmarks, as the core's `docs/contract/benchmarks.md` asks of a language's `benchmarks`
module. Its classes are listed in [its source doc](../source/src/main/java/com/weivel/benchmarks/README.md), and
running them in [building.md](../building.md).

`com.weivel.benchmarks` holds the benchmark gates, judged as the kit's `docs/benchmarks.md` defines them and bounded
by the pinned kit's `benchmarks/java.txt`, a ratio gate per backend. The same-language gate is the identity check: a
consumer's get answers the provider's own object, and the timing beside it, three paths running the very same compiled
call, is judged only against the platform's noise. `control-call` is `weivel_get_service` through the wrapper, with the
core's timer armed as a host arms it, over the same function of the core's table on the same core, plugin and id
called bare through `CoreInternals.bareServiceGet`, the one route past the wrapper, which reaches that function alone
and is refused once it is closed or the core is destroyed.

`library-call` is the echo library's `echo_echo` through the host's `LibraryService` with a `byte[]`, over the same
function called bare with the same bytes copied in and its answer copied out and released as the host does. Its
definition row is the echo definition's `echo` called through the definition a consumer resolved, over the same
operation called through the pointer `echo_service_definition` answered, each path reading the operation's function
pointer out of the struct once before its timed loop, from the one address it is handed; it is judged by the
platform's noise, as the same-language pair is. It is sampled apart from that pair, in 101 rounds after a discarded
warm-up: on the foreign function backend 1 000 000 warm-up calls and batches of 500 000, on JNA 200 000 warm-up calls
and batches of 20 000, so a batch lasts some milliseconds on each; a foreign function batch of 20 000 calls left two
identical direct paths twice Linux's worst noise apart.

`described-call` is the standard's described mirror's `reflect`, a sample holding a text and a counted array of three
texts in and its reflection out, called on the `BoundOperation` a consumer's resolved `BoundDefinition` answers, over
the same operation through the mirror's own function pointers called bare on the same backend, the fixture's structs
declared by hand in the benchmark alone, for 64-bit platforms. Both write the same input every call, copy every
member the binding answers into Java values, release the reflection, and release the refusal only once it is filled,
as the binding does; a test holds both to one reflection released a call, as the library counts its own releases.
The echo library's bytes call through the host is sampled in the same rounds, and its time printed beside the pair's.
It is sampled as `library-call` is.

Paths are sampled side by side after a discarded warm-up, one batch of each a round with the first path moving on each
round, and judged on the median of the per-round ratios. A run with nothing measured fails, and so does a backend or
platform the budget gives no line: no bound is never a pass. "Next to no overhead" is judged on the foreign function
backend; JNA is the compatibility path and is bounded at what it measures.
