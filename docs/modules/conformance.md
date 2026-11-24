# `com.weivel.conformance`

The driver for the layer suite, as the core's `docs/contract/conformance.md` asks of a language's `conformance`
module. It is how this implementation is held to the same behaviour as every other: the conformance kit's layer
vectors state what only a language layer can witness, the kit's `docs/vectors/layer/` says what a suite must show for
each, and the kit's `layers` drift check, run by this repository's drift job, fails a vector no test names. Its classes
are listed in [its source doc](../source/src/main/java/com/weivel/conformance/README.md), and the suite in
[the tests' doc](../source/src/test/java/com/weivel/conformance/README.md).

`com.weivel.conformance` is the driver for the layer suite. `LayerDriver` reads the pinned kit's layer vectors
through the standard's JSON reader, bound through `ForeignLibrary`, and writes no file; it builds each vector's plugins
as manifest values and entries, drives them through the host's public surface on the real core, a `want` vector on a
clock and scheduler of the suite's own, a `library` vector with the core's echo library and a `description` vector
with the standard's described library, every host given the standard's description check, and answers a
`LayerWitness` in the shape of the vector's `then`. The suite has one test per vector, its display name the vector's
id, and a coverage test that discovers those names through the JUnit Platform Launcher and fails on a vector none
names.
