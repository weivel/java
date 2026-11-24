# `com.weivel.handles`

The table from the core's handles to this language's objects, as the core's `docs/contract/handles.md` asks of a
language's `handles` module. Its classes are listed in
[its source doc](../source/src/main/java/com/weivel/handles/README.md).

`Handles` is the table from the core's handles to the layer's objects: an entry per plugin, service registration,
capability implementation, watch and want, made in room a reservation took before the call, from the step the wrapper
runs before any event of that call is dispatched, and ended by its caller when what the handle stands for ends; and the
holdings, what each plugin was handed for another's registration or implementation, kept by the pair of the two handles
and dropped by the revocation that names them, even for a plugin already forgotten. A handle the table does not hold
answers nothing. Each entry carries a token a plugin is opened with as its payload, which leads back to the entry
without a search. A lookup costs one probe of a hash table keyed by primitive `long`s and allocates nothing; the table
has one lock of its own, always the innermost.
