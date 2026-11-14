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
core's header declares into one table of downcall handles, which nothing else in the layer names. A path that is no
library, and a library that lacks one of the core's functions, are each answered as a value, the second naming the
first function missing. `CoreBinary.close` closes what the load opened.

The other packages hold only their purpose so far.

## Using it

The layer needs Java 25 and the core's shared binary. Java refuses, or warns about, native access a launch did not
grant, so a process using the layer from the class path runs with `--enable-native-access=ALL-UNNAMED`.

## Building

`./gradlew check` builds and tests everything. The wrapper fetches its pinned Gradle, and Gradle fetches a Java 25
toolchain when none is installed; the native build needs CMake 3.15 or later and a C compiler on the `PATH`. The
build reads the core, the standard and the conformance kit from `siblings/core`, `siblings/standard` and
`siblings/conformance` at the commits `.weivel/repo.json` pins: CI checks them out there, and locally they are links
or clones of those checkouts. Gradle builds the core's shared library, its echo test library and its `layouts`
program, and the standard's shared library and `layouts` program, with CMake in Release, and the tests run against
those binaries.
