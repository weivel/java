package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A C library's definition bound from its description, built once at the load: one {@link BoundOperation} per
 * operation the definition answers a status from, called by name with a {@code Map} of inputs, its answers arriving as
 * Java values. A struct arrives as an unmodifiable {@code Map} keyed by member name in the description's order, since
 * no library ships Java source for its structs and so no accessor exists to generate for one; releases are not
 * operations, since every answer is released before its call returns and a handle through its own
 * {@link DescribedHandle#release()}.
 *
 * <p>It stands for the very struct its definition's symbol answered: {@link #definition()} is the object an undescribed
 * definition is registered as, carrying the same address.
 */
public final class BoundDefinition {
    private final String name;
    private final Object definition;
    private final long address;
    private final Description description;
    private final Map<String, BoundOperation> operations;

    BoundDefinition(String name, Object definition, long address, Description description,
                    Map<String, BoundOperation> operations) {
        this.name = name;
        this.definition = definition;
        this.address = address;
        this.description = description;
        this.operations = Collections.unmodifiableMap(new LinkedHashMap<>(operations));
    }

    /**
     * Answers {@code name}.
     *
     * @return the definition's struct, as its description names it
     */
    public String name() {
        return name;
    }

    /**
     * The definition itself, as a host registers one that declares no description.
     *
     * @return a {@code MemorySegment} on the foreign function API, a JNA {@code Pointer} on JNA, carrying the address
     *     the definition's symbol answered
     */
    public Object definition() {
        return definition;
    }

    /**
     * Answers {@code address}.
     *
     * @return the address the definition's symbol answered
     */
    public long address() {
        return address;
    }

    /**
     * Answers {@code description}.
     *
     * @return the description it was bound from
     */
    public Description description() {
        return description;
    }

    /**
     * Answers {@code operationNames}.
     *
     * @return every operation's name, in the struct's order
     */
    public List<String> operationNames() {
        return Collections.unmodifiableList(new ArrayList<>(operations.keySet()));
    }

    /**
     * One operation, to call it again and again with no lookup by name.
     *
     * @param operation its name
     * @return the operation
     * @throws IllegalArgumentException for a name the definition has no operation of
     */
    public BoundOperation operation(String operation) {
        Objects.requireNonNull(operation, "operation");
        BoundOperation bound = operations.get(operation);
        if (bound == null) {
            throw new IllegalArgumentException(name + " has no operation " + operation + "; it has "
                    + String.join(", ", operations.keySet()));
        }
        return bound;
    }

    /**
     * Calls an operation, as {@link BoundOperation#call(Map)} does.
     *
     * @param operation its name
     * @param inputs one value per input, keyed by its name
     * @return what it answered
     * @throws IllegalArgumentException for a name the definition has no operation of, or inputs the operation refuses
     * @throws UnsupportedOperationException when the provider does not offer it
     */
    public DescribedAnswer call(String operation, Map<String, ?> inputs) {
        return operation(operation).call(Objects.requireNonNull(inputs, "inputs"));
    }

    @Override
    public String toString() {
        return "BoundDefinition[" + name + operations.keySet() + "]";
    }
}
