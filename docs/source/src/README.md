Every source of the repository, in Gradle's source set layout: `main` is the layer the jar ships, `test` its suites, and `plugins` a source set of its own, packed into a JAR the host tests load and kept off the test class path, so that loading an entry from a JAR is tested for real. `build.gradle` wires the three together, as [building.md](../../building.md) describes.

<!-- generated: source-children -->
- [main/](main/) - the layer the jar ships: the Java 8 sources and the Java 22 foreign function backend
- [plugins/](plugins/) - the plugin classes a host test loads from a JAR off the test class path
- [test/](test/) - the suites, run against the built jar on every Java the jar serves
<!-- end generated -->
