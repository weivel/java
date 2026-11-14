package com.weivel.context;

/**
 * Hears a watched capability: each verified implementation that joins, and the very implementation that leaves. It
 * is called outside every lock of the layer's, by the one thread dispatching, and may call into its context; what it
 * throws goes to the error sink.
 */
public interface CapabilityWatcher {
    /**
     * An implementation joined, its provider's activation verified.
     *
     * @param implementation the provider's own object
     */
    void provided(Object implementation);

    /**
     * An implementation left, its provider quarantined or released.
     *
     * @param implementation the provider's own object, the very one that left
     */
    void withdrawn(Object implementation);
}
