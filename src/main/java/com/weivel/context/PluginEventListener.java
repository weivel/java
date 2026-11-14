package com.weivel.context;

/**
 * Hears what the core reports about one plugin. It is called outside every lock of the layer's, by the one thread
 * dispatching, and may call into its context; what it throws goes to the error sink, and every other listener still
 * hears the event.
 */
@FunctionalInterface
public interface PluginEventListener {
    /**
     * Hears one event.
     *
     * @param event what the core reported
     */
    void hear(PluginEvent event);
}
