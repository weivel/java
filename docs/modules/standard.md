# `com.weivel.standard`

Java's access to the standard's defaults, the optional module the core's `docs/contract/standard.md` describes. What
the standard itself reads, validates and defines is in the standard's own docs. Its classes are listed in
[its source doc](../source/src/main/java/com/weivel/standard/README.md).

`com.weivel.standard` is optional, and no class of the packages above depends on it, which a test reads off the
built jar with `jdeps`. `StandardBinary.load` opens the standard's binary from the caller's path through the same
seam, through `ForeignLibrary`, which binds only the symbols its caller names and never one of the core's, and fails
naming the first function missing. `Standard` answers what the standard answers, each as a `StandardAnswer`: a JSON
manifest validated and read into a `Manifest`, plugins discovered on disk as `DiscoveredPlugin`s, a library
declaration read into a `LibraryDeclaration` for `loadLibrary`, identities, the mirror list, SHA-256, and the three
service ids, read from the standard's functions. Every struct the standard fills is copied into Java values and
released through the standard's own release before the call answers, every release of a call running even when
another fails. `readManifest` reads a manifest without validating it, and `validate` validates one without reading it.

A `DiscoveredPlugin` loads no code: the manifest calls its entry opaque and no module convention is a default the
layer may assume, so its caller registers `manifest()` with an entry it makes itself, from `entryPath()` through
`Host.loadEntry` with a class name the caller gives, or any other way.
`ForeignMemory.manifest` reads a manifest another library laid out up to the size it declares, as the core does.
`Config`, `Secrets` and `Paths` are the standard's service definitions as Java interfaces, a refusal thrown as a
`ServiceRefusal`.
