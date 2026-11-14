package com.weivel.timer;

import java.util.OptionalLong;

/**
 * Hears each move a timer makes as the core announces it: armed for a deadline, or disarmed. It is called on the
 * thread that drained the announcement, outside every lock of the layer's, before any event of that call is
 * dispatched; what it throws goes to the error sink.
 */
@FunctionalInterface
public interface TimerObserver {
    /** An observer that hears nothing. */
    TimerObserver NONE = deadline -> { };

    /**
     * The timer moved.
     *
     * @param deadline the deadline it is now armed for, on the scheduler's clock; empty when it disarmed
     */
    void moved(OptionalLong deadline);
}
