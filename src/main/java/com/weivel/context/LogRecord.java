package com.weivel.context;

import java.util.Objects;
import java.util.Optional;

/**
 * One line of the host's log.
 */
public final class LogRecord {
    private final int level;
    private final Optional<String> plugin;
    private final String text;

    /**
     * Checks that every part is present.
     *
     * @param level the level
     * @param plugin the plugin, or empty
     * @param text the line
     */
    public LogRecord(int level, Optional<String> plugin, String text) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(text, "text");
        this.level = level;
        this.plugin = plugin;
        this.text = text;
    }

    /**
     * Answers {@code level}.
     *
     * @return a log level of the core's header
     */
    public int level() {
        return level;
    }

    /**
     * Answers {@code plugin}.
     *
     * @return the import name of the plugin that wrote it; empty for a line of the core's own
     */
    public Optional<String> plugin() {
        return plugin;
    }

    /**
     * Answers {@code text}.
     *
     * @return the line
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LogRecord)) {
            return false;
        }
        LogRecord that = (LogRecord) other;
        return level == that.level
                && Objects.equals(plugin, that.plugin)
                && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(level);
        hash = 31 * hash + Objects.hashCode(plugin);
        hash = 31 * hash + Objects.hashCode(text);
        return hash;
    }

    @Override
    public String toString() {
        return "LogRecord[level=" + level + ", plugin=" + plugin + ", text=" + text + "]";
    }
}
