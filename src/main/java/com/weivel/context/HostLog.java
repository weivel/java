package com.weivel.context;

/**
 * Where a host directs the log of its plugins and of the core. It is called outside every lock of the layer's; what
 * it throws goes to the error sink.
 */
@FunctionalInterface
public interface HostLog {
    /**
     * Writes one line.
     *
     * @param record the line, at its level, naming the plugin that wrote it
     */
    void write(LogRecord record);
}
