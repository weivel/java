package com.weivel.conformance;

import java.util.Map;
import java.util.Objects;

/**
 * One layer vector of the kit, as the standard's JSON reader read it: an object as a {@link Map} in member order, an
 * array as a {@link java.util.List}, an integral number as a {@link Long} and JSON's {@code null} as a value of its
 * own, which {@link LayerWitness#observed()} answers in the same shapes.
 */
public final class LayerVector {
    private final String id;
    private final String op;
    private final Map<String, Object> given;
    private final Map<String, Object> then;

    LayerVector(String id, String op, Map<String, Object> given, Map<String, Object> then) {
        this.id = Objects.requireNonNull(id, "id");
        this.op = Objects.requireNonNull(op, "op");
        this.given = Objects.requireNonNull(given, "given");
        this.then = Objects.requireNonNull(then, "then");
    }

    /**
     * Answers {@code id}.
     *
     * @return the vector's id, its path under the kit's layer vector directory without {@code .json}
     */
    public String id() {
        return id;
    }

    /**
     * Answers {@code op}.
     *
     * @return the op it drives: {@code consume}, {@code want}, {@code lifecycle} or {@code library}
     */
    public String op() {
        return op;
    }

    /**
     * Answers {@code given}.
     *
     * @return what the suite builds through the layer
     */
    public Map<String, Object> given() {
        return given;
    }

    /**
     * Answers {@code then}.
     *
     * @return what the run must show, field by field
     */
    public Map<String, Object> then() {
        return then;
    }

    @Override
    public String toString() {
        return "LayerVector[id=" + id + ", op=" + op + "]";
    }
}
