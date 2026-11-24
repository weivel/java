`com.weivel.handles`: the table from the core's handles to the layer's objects, and the holdings. What it keeps, when an entry ends and why a lookup allocates nothing is [the handles module](../../../../../../../modules/handles.md).

<!-- generated: source-children -->
- [HandleEntry.java](../../../../../../../../src/main/java/com/weivel/handles/HandleEntry.java) - one entry of the table: a handle the core answered and the layer's object for it
- [HandleKind.java](../../../../../../../../src/main/java/com/weivel/handles/HandleKind.java) - what a handle of an entry stands for
- [Handles.java](../../../../../../../../src/main/java/com/weivel/handles/Handles.java) - the table: an entry per handle the core answered, and the holdings kept by pairs of handles
- [PairTable.java](../../../../../../../../src/main/java/com/weivel/handles/PairTable.java) - a hash table keyed by a pair of `long`s, so a lookup boxes and allocates nothing
- [TableMemory.java](../../../../../../../../src/main/java/com/weivel/handles/TableMemory.java) - where the table's arrays come from: the heap, or a test's memory that runs out on purpose
- [package-info.java](../../../../../../../../src/main/java/com/weivel/handles/package-info.java) - the package's summary in its Javadoc
<!-- end generated -->
