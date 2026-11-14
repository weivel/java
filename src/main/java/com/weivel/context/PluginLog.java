package com.weivel.context;

import com.weivel.core.CoreConstants;
import java.util.Objects;
import java.util.Optional;

/** A plugin's log, which goes to the host's log with the plugin named. What the host's log throws goes to the sink. */
public final class PluginLog {
    private final ContextState state;
    private final String importName;

    PluginLog(ContextState state, String importName) {
        this.state = state;
        this.importName = importName;
    }

    /**
     * Writes one line at {@code level}.
     *
     * @param level a log level of the core's header, as {@link CoreConstants} names them
     * @param text the line
     */
    public void write(int level, String text) {
        state.write(new LogRecord(level, Optional.of(importName), Objects.requireNonNull(text, "text")));
    }

    /**
     * Writes one line at the debug level.
     *
     * @param text the line
     */
    public void debug(String text) {
        write(CoreConstants.LOG_DEBUG, text);
    }

    /**
     * Writes one line at the info level.
     *
     * @param text the line
     */
    public void info(String text) {
        write(CoreConstants.LOG_INFO, text);
    }

    /**
     * Writes one line at the warning level.
     *
     * @param text the line
     */
    public void warning(String text) {
        write(CoreConstants.LOG_WARNING, text);
    }

    /**
     * Writes one line at the error level.
     *
     * @param text the line
     */
    public void error(String text) {
        write(CoreConstants.LOG_ERROR, text);
    }
}
