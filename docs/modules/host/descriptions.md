# Described definitions

A definition whose service declares a `description` symbol is registered as a `BoundDefinition`, bound once at the
load from the standard's `weivel_standard_description` its library answers, so a Java plugin calls a C library that
ships no Java source. What a description is, and the check that holds it, are the standard's `docs/descriptions.md`;
the binding's classes are in `com.weivel.core`, listed in
[its source doc](../../source/src/main/java/com/weivel/core/README.md). When the binary closes and how a holder lets go
is the same for a bound definition as for any other, in [libraries.md](libraries.md).

- **The check is the caller's.** The host never loads the standard, so it runs the check its caller hands it through
  `HostOptions.withDescriptionCheck`, as `Standard.descriptionCheck()` answers it, before it reads anything else of a
  description. That check is the standard's two: the description is well formed, and its `answered_by` answers the
  very definition the service's definition symbol answered, so a description of another definition fails the load
  even when both definitions share one struct. A host given none refuses a described library as the caller's mistake,
  and so does a description either check refuses, naming both symbols, a description symbol missing or answering
  NULL, and a definition ending before its first shape. A check that runs out of memory fails the load as
  `Answer.Refused` of `OUT_OF_MEMORY`, never as the caller's mistake.
  The kinds each role takes, the shape of a refusal and a release, and where the context lies are the check's rules,
  which this binding does not repeat.
- **The layout is the platform's.** Every described struct, embedded structs and value nodes included, is laid out by
  the native backend's own rules, and every member's offset and size and every struct's size must be what the
  description states, or the load is refused naming the member. So is a kind above the last this binding knows, an
  element a pointer or array cannot reach, and an operation of more than 7 parameters. The foreign function backend
  lays a struct out with `MemoryLayout`s, and JNA with its `Structure`'s own field alignment rule, so both bind every
  description and refuse the same ones.
- **A call follows plans built at the load.** Each operation's native function, its signature from its parameters,
  and the plan of every parameter and member are made once; the context and every function pointer are read once from
  the definition's struct. An operation past the `size` its definition answered, or NULL there, is not offered, and
  calling it throws `UnsupportedOperationException`. A call takes one arena its library keeps, writes every input
  straight into it, makes the one native call, copies every answer out and releases each answer and the refusal
  through the release its description names once it holds anything, before it answers; an arena that has grown to
  hold a call allocates nothing.
- **Values are Java's.** `BoundOperation.call` takes a `Map` keyed by input name. Every integer kind is a `Long`: a
  U32 from 0 to 2^32 - 1, an I64 as itself, a U64 or size as its 64 bits, so one at or above 2^63 reads negative and
  is read with `Long.toUnsignedString`. An F64 is a `Double`, a text a `String` or null, texts a `List`, bytes a
  `byte[]`, a struct an unmodifiable `Map` in its members' order, an array a `List`, and a value tree the natural Java
  value. A struct is a map rather than a generated accessor because no library ships source for its structs, so no
  plugin could name an accessor's type anyway. A leading `size`, every count, the context and storage are the
  binding's: writing one, an unknown name, a value outside its kind or a text holding a NUL throws
  `IllegalArgumentException` before anything is called.
- **A definition an answer carries is bound too.** A DEFINITION member or answer is a `BoundDefinition`, bound from
  the description nested in its carrier's, and `BoundDefinition.bindDefinition` binds a definition with no library
  behind it, on the foreign function backend and on JNA alike. It lives, with every answer kept and every handle got
  through it, no longer than the answer that carried it, and a call through it once that answer has ended is the
  caller's mistake. A handle, unlike a carried definition, is valid anywhere in its library.
- **Blocking is read and not acted on.** `BoundDefinition.blockingOperations` lists the operations the description
  marks `blocking`, and this binding calls every operation, those included, on the calling thread.
- **Outcomes are values.** A call answers `DescribedAnswer.Answered` with each answer by name, a NULL pointer, a NULL
  handle and a value of kind NONE left out; `None`; `Refused` with the provider's `detail` and `fix`; `Failed` with any
  other status; or `Unloaded` once the binary has closed. A holder still holding a definition after the unload calls it
  while the binary stays open, as a holding outlives the unload.
- **A handle is an object.** A handle answer is a `DescribedHandle` its holder frees with `release()`, once; handed
  back as an input it must be a live handle of the same library and type. The unload releases every handle still held,
  while the binary is still open.
