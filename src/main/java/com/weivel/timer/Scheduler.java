package com.weivel.timer;

/**
 * The caller's clock and scheduler: the layer owns no thread, no clock and no timer of its own. Each method is called
 * outside every lock of the layer's, on whichever thread calls into the core, so each may call into the core itself.
 *
 * <p>What a scheduler must hold to: it runs a task's callback at most once, on any thread, and not before the clock
 * has reached the time the task was scheduled for (a callback run early is harmless, and the timer arms again); while
 * it runs a callback it holds no lock its own {@link #schedule} or {@link Task#cancel} take, because the callback
 * calls into the core and what that queues schedules and cancels from inside it.
 */
public interface Scheduler {
    /**
     * The time now, in milliseconds on a monotonic scale of the caller's: the scale every deadline is counted on.
     *
     * @return the time now
     */
    long now();

    /**
     * Schedules {@code callback} to run once when the clock reaches {@code at}, at once when it already has.
     *
     * @param at the time on this scheduler's clock
     * @param callback what to run
     * @return the task, which can be cancelled
     */
    Task schedule(long at, Runnable callback);

    /**
     * The longest this scheduler can wait, in milliseconds. A deadline further away is waited for in steps of this
     * length, the clock read once as each step is scheduled.
     *
     * @return the longest wait; {@link Long#MAX_VALUE} for a scheduler that can wait any time, the default
     */
    default long maximumWait() {
        return Long.MAX_VALUE;
    }

    /**
     * A scheduler that arms nothing and reads no clock: a timer on it follows no deadline, and the wrapper makes no
     * check before a call. A want with a timeout or with constraints needs the time, so on this scheduler it is the
     * caller's mistake, refused before anything is asked of the core.
     *
     * @return the scheduler
     */
    static Scheduler none() {
        return NoScheduler.INSTANCE;
    }

    /** One scheduled callback. */
    interface Task {
        /**
         * Cancels the task.
         *
         * @return true when the callback was taken out before it began, so it will never run; false when it began,
         *     has run, or the scheduler cannot tell, which is always safe to answer
         */
        boolean cancel();
    }
}
