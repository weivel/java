# C libraries as host services

`loadLibrary` loads a C library from a `LibraryDeclaration` the caller builds or the standard reads, and the library's
path, and registers each declared service under `WEIVEL_HOST`: a service of bytes functions as a `LibraryService`,
whose functions a consumer calls by name as plain Java calls, and a definition service that declares no
description as the language's own object carrying the very address its definition's symbol answered, called once at
the load: a `MemorySegment` of the struct's `size` bytes on the foreign function backend, a JNA `Pointer` on JNA. A
definition answering NULL, or a struct whose `size` is below `2 * sizeof(void *)`, refuses the load as the caller's
mistake before anything is registered. A definition whose service declares a `description` symbol is bound instead, as
[descriptions.md](descriptions.md) says.

A declaration may list no service and only `contracts`, the descriptions a library publishes for plugins to implement.
Such a library loads, its binary opened, and registers nothing: the host keeps its contracts on the declaration and
binds nothing from them, and does not yet check a capability a plugin provides against the contract describing it. A
declaration of neither a service nor a contract is the caller's mistake, as the standard's reader refuses it.

## Unloading, and when the binary closes

A definition, described or not, is registered awaiting releases: `unloadLibrary` unregisters it at once, each holder
hears a `PluginEvent.Revoked` that `awaitsRelease`, and lets go by handing that revocation to
`PluginContext.releaseHolding` once it no longer uses the object, however it came to hold it, by a get, a want or a
watch; the revocation names the registration, so an object registered under several is let go of under that one
alone. A holder released, quarantined or forgotten has let go with it, and a call through an undescribed definition
after its holder let go is undefined, where a bound one answers `Unloaded` once its binary has closed. The binary
closes once the core's `WEIVEL_EVENT_HOLDINGS_RELEASED` of every definition service has been dispatched and no bytes
call runs in it, or, for a definition the destroy finds still registered, once the destroy has destroyed the core,
since a collected language has no free of the host to wait for; every address a definition answered is invalid from
then on. `LoadedLibrary.definitions()` hands those addresses to a caller that holds no registration of them, so they
die with the binary. A plugin registers awaiting releases with `PluginContext.registerAwaitingReleases`, and hears
`PluginEvent.HoldingsReleased` once nothing holds what it registered, as it hears any event about it: one its own
release or quarantine queued still reaches it, one queued after those reaches no one.

## What a bytes call allocates

A direct `ByteBuffer` input is lent to the library for the call; a `byte[]` is copied once into native memory, because
lending the heap would hold off garbage collection for the whole C call. The copy and the cells the answer comes back
in share one block, and each library keeps one such block of 256 bytes spare for whichever call takes it first. A call
allocates no native memory when it finds the spare free and needs no more than it: the 16 bytes of answer cells, plus
the copy of a heap input, so a `byte[]` or heap buffer of up to 240 bytes, or any direct buffer. A larger heap input,
or a call that finds the spare taken, allocates a block of its own and frees it. The body the library answers is
copied once into a Java array and released before the call answers, so no bytes it answered are ever held past a
call. `unloadLibrary`, and the destroy, close a library to calls at once.
