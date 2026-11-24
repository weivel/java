# Building and testing

`./gradlew check` builds and tests everything. The wrapper fetches its pinned Gradle, and Gradle fetches the Java 8, 11,
17, 21 and 25 toolchains when they are not installed; the native build needs CMake 3.15 or later and a C compiler on the
`PATH`. The build reads the core, the standard and the conformance kit from `siblings/core`, `siblings/standard` and
`siblings/conformance` at the commits `repo.json` pins: CI checks them out there, and locally they are links or
clones of those checkouts. Gradle builds the core's shared library, its echo test library and its `layouts` program, and
the standard's shared library, its described test library `weivel_standard_described` and its `layouts` program, with
CMake in Release, and the tests run against those binaries.
Each native build declares every sibling's sources it compiles as its inputs, the core's among the standard's, so a
change to either rebuilds it.

Every JNA toolchain is Amazon Corretto, whose Java 8 also ships for macOS on arm64, where the core is built natively.

## The jar and the suites

Java 8 compiles every source but the foreign function backend, which Java 25 compiles at release 22 into the jar's
`META-INF/versions/22`. `check` runs the whole suite against the built jar, never against the class directories, so the
multi-release choice is the one a caller gets: `test` on Java 25 through the foreign function API, and `testJava8`,
`testJava11`, `testJava17` and `testJava21` through JNA on every long-term release it serves, each asserting which
backend it ran on. Javadoc runs on Java 25, at release 8 for the jar's base and at release 22 for the Java 22 sources.

## The benchmarks

`./gradlew benchmark` runs the benchmark gates on both backends, `benchmarkFfm` on Java 25 and `benchmarkJna` on
Java 8, and is never part of `check`: CI runs it as a job of its own on every runner. By default it judges one run of
each gate against `java.txt` of the pinned kit; `-Pweivel.benchmark.mode=measure` judges no timing and prints one
`weivel-benchmark` line per gate for each of `-Pweivel.benchmark.repetitions` runs (10 by default), the samples a
bound is set from. No repository sets its own bound, so the gate reads no budget but the pinned kit's. What each gate
times is in [the benchmarks module](modules/benchmarks.md).

## A sibling behind a link

Gradle's file system watching may not see a change made behind a link: a sibling linked in as a junction or a
symbolic link can be edited without the next build noticing, which then reports the native tasks up to date. Run
with `--no-watch-fs` to have Gradle read the files again, or `--rerun-tasks` to rebuild everything.
