package com.weivel.context;

/**
 * Hears a watched service: each later registration the core reports for the id, with the provider's own object, and
 * each unregistration, with nothing. It is called outside every lock of the layer's, by the one thread dispatching,
 * and may call into its context; what it throws goes to the error sink.
 */
public interface ServiceWatcher {
    /**
     * A service was registered under the watched id.
     *
     * @param service the provider's own object
     */
    void registered(Object service);

    /** A service under the watched id was unregistered. */
    void unregistered();
}
