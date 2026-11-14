package com.weivel.core;

/**
 * How a timer follows the core's deadline. The wrapper records the latest deadline announcement as it drains it,
 * under the layer lock, and calls both hooks outside that lock, so either may call into the core.
 */
public interface DeadlineHooks {
    /**
     * Reads the clock, in the milliseconds the core's deadlines are counted in. It is read before a call only while a
     * deadline is recorded, once per call: when it has reached that deadline the wrapper hands the core the time, and
     * only then makes the call it was asked for. The advance it makes is not checked first, and neither is an advance
     * its caller asks for.
     *
     * @return the time now
     */
    long now();

    /**
     * Is told the latest deadline drained, after the call that drained it let the lock go and before any event that
     * call drained is dispatched. One thread is told at a time, and what it is told last is always the latest
     * announcement drained, whichever thread drained it.
     *
     * @param at the deadline, meaningful when {@code set}
     * @param set whether a deadline is set; false when none is left
     */
    void follow(long at, boolean set);
}
