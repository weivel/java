package com.weivel.context;

import java.util.Objects;
import java.util.Optional;

/**
 * One line of the host's log.
 *
 * @param level a log level of the core's header
 * @param plugin the import name of the plugin that wrote it; empty for a line of the core's own
 * @param text the line
 */
public record LogRecord(int level, Optional<String> plugin, String text) {
    /**
     * Checks that every part is present.
     *
     * @param level the level
     * @param plugin the plugin, or empty
     * @param text the line
     */
    public LogRecord {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(text, "text");
    }
}
