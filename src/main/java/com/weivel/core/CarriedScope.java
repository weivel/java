package com.weivel.core;

/**
 * What one definition an answer carried lives in: it counts the calls running through that definition, refuses every
 * new one once it is closed, and runs what its close leaves to do once the last running call has returned, on the
 * thread of that call. A definition no answer carried, a service's own, lives in {@link #UNSCOPED}, which never
 * closes.
 */
final class CarriedScope {
    /** The scope of a definition no answer carried, which lets every call in and counts none. */
    static final CarriedScope UNSCOPED = new CarriedScope(false);

    private final boolean scoped;
    private int running;
    private boolean live = true;
    private Runnable whenIdle;

    private CarriedScope(boolean scoped) {
        this.scoped = scoped;
    }

    /** A scope of its own, for one definition an answer carried. */
    static CarriedScope carried() {
        return new CarriedScope(true);
    }

    /**
     * Counts a call in.
     *
     * @return false once the scope is closed, when no call may run
     */
    boolean enter() {
        if (!scoped) {
            return true;
        }
        synchronized (this) {
            if (!live) {
                return false;
            }
            running++;
            return true;
        }
    }

    /** Counts a call {@link #enter} let in out again, running what a close left to do when it was the last. */
    void leave() {
        if (!scoped) {
            return;
        }
        Runnable idle = null;
        synchronized (this) {
            running--;
            if (running == 0 && whenIdle != null) {
                idle = whenIdle;
                whenIdle = null;
            }
        }
        if (idle != null) {
            idle.run();
        }
    }

    /**
     * Refuses every new call at once, and runs {@code then} once no call runs: now, on this thread, when none does, or
     * else on the thread of the last running call as it returns.
     */
    void close(Runnable then) {
        synchronized (this) {
            live = false;
            if (running > 0) {
                whenIdle = then;
                return;
            }
        }
        then.run();
    }
}
