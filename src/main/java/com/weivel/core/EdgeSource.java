package com.weivel.core;

/** Which declarations draw the edges an activation plan orders by, mirroring {@code weivel_edge_source}. */
public enum EdgeSource {
    /** A plugin's provided and consumed capabilities ({@code WEIVEL_EDGES_CAPABILITIES}). */
    CAPABILITIES(1),
    /** A plugin's provided and consumed services ({@code WEIVEL_EDGES_SERVICES}). */
    SERVICES(2);

    private final int value;

    EdgeSource(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }
}
