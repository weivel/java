The repository root: the Gradle build of one multi-release jar (`build.gradle`, `settings.gradle` and the Gradle wrapper) and `repo.json`, which pins the commits of the core, the standard and the conformance kit the build reads from `siblings/`. No code lies at the root; every source is under `src/`. How to build the jar and run its tests and benchmarks is [building.md](../building.md), and what a process using the jar needs is [using.md](../using.md).

<!-- generated: source-children -->
- [src/](src/) - every source: the layer the jar ships, the plugins the host tests load from a JAR, and the tests
<!-- end generated -->
