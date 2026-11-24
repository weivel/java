# `com.weivel.conformance`

The driver for the layer suite, as the core's `docs/contract/conformance.md` asks of a language's `conformance`
module. It is how this implementation is held to the same behaviour as every other: the conformance kit's layer
vectors state what only a language layer can witness, and the kit's `docs/vectors/layer/` says what a suite must show
for each. Its classes are listed in [its source doc](../source/src/main/java/com/weivel/conformance/README.md), and the
suite in [the tests' doc](../source/src/test/java/com/weivel/conformance/README.md).

`LayerDriver` reads the pinned kit's layer vectors through the standard's JSON reader, bound through `ForeignLibrary`,
and writes no file; it builds each vector's plugins as manifest values and entries, drives them through the host's
public surface on the real core, a `want` vector on a clock and scheduler of the suite's own, a `library` vector with
the core's echo library and a `description` vector with the standard's described library, every host given the
standard's description check, and answers a `LayerWitness` in the shape of the vector's `then`.

## Which vectors Java sits

The suite has one test per vector Java sits, its display name the vector's id. Java sits every layer vector but those
under `host/`: they run a language's host program, and Java ships no host program, so the kit's
`checks/languages.txt` exempts Java from that directory (`java layer-exempt host`). A coverage test discovers the
suite's display names through the JUnit Platform Launcher and fails on a vector none names, skipping only the
directories the kit exempts Java from; a second test goes red when the kit drops the exemption or the exempted
directory holds no vector any more, so a change to the exemption is noticed rather than every host vector reading as
one no test names.

## Why the suite is the strong check

The kit's `layers` drift check, run by this repository's drift job, only proves each vector's id appears somewhere in
the Java test sources. The suite is the strong check behind it: each test runs its vector on the real core and the
real standard and compares what it saw with the vector's whole `then`, and the coverage test reads the names of tests
that exist, not text that happens to match.
