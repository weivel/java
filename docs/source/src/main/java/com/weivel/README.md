The packages of the layer, one per module the core's `MODULES` list names. What each does, and why, is [the modules docs](../../../../../../modules/README.md); each package's doc below lists its classes. The packages depend on one another in one direction only: `handles` and `timer` on `core`, `context` on those three, `host` on `context`, and `standard`, `conformance` and `benchmarks` on the ones before them, never the reverse.

<!-- generated: source-children -->
- [benchmarks/](benchmarks/) - the timing gates: calls through weivel timed against the same calls made bare
- [conformance/](conformance/) - the driver that runs the kit's layer vectors through the host on the real core
- [context/](context/) - what a plugin sees of the core, and the surface a host makes its contexts through
- [core/](core/) - the core binary's load, the one wrapper, the native seam, and descriptions bound into calls
- [handles/](handles/) - the table from the core's handles to Java objects
- [host/](host/) - Java plugins and C libraries on one core
- [standard/](standard/) - optional: the standard's defaults in Java
- [timer/](timer/) - the core's next deadline, armed on the caller's scheduler
<!-- end generated -->
