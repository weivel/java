The `plugins` source set: classes the host tests load from a JAR. The `pluginsJar` task packs them, and they stay off the test class path, so a test proves the host's own class loader is what finds them.

<!-- generated: source-children -->
- [java/](java/) - the source root of the plugin classes
<!-- end generated -->
