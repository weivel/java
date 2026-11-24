The layer's sources, in two trees because the jar is multi-release: `java/` is compiled by Java 8 into the jar's base, and `java22/` by Java 25 at release 22 into the jar's `META-INF/versions/22`, where a Java 22 or later finds the classes that replace the base's of the same name. Why the native seam has two backends is [the architecture](../../../README.md); the kit's `parity` check judges both trees as source roots of the same modules.

<!-- generated: source-children -->
- [java/](java/) - the layer for Java 8 and later: every module, the JNA backend of the native seam among them
- [java22/](java22/) - the foreign function backend of the native seam, for Java 22 and later
<!-- end generated -->
