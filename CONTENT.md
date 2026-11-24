## What this is

The Java language implementation of weivel, for a host written in Java that runs plugins written in Java. It decides
nothing itself: compatibility, order, reach, waiting and revocation are the core's answers, reached from one
multi-release jar through Java's foreign function and memory API on Java 22 and later and through JNA on Java 8 to 21.

## What it is not

It is not the core, and it carries no C: it loads the core's binary from the path its caller gives. It forces no path,
file name, variable or format on its caller.

## Where to read on

Start at [docs/README.md](docs/README.md), the implementation's architecture and the full statement of what it is and
is not, and descend: what each module does, what a process using it needs, how to build and test it, and one doc per
source folder.
