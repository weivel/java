package com.weivel.core;

/**
 * Hears every event of a core. It is called outside the layer lock by the one thread dispatching, and may call into
 * the core; the events such a call drains are heard after it returns, behind every event drained before them. What it
 * throws goes to the error sink, and every other listener still hears the event.
 */
@FunctionalInterface
public interface CoreListener {
    /**
     * Hears one event.
     *
     * @param event the event, the layer's own copy
     */
    void hear(CoreEvent event);
}
