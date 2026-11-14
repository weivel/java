package com.weivel.timer;

/** Schedulers the layer offers its callers. */
public final class Schedulers {
    private Schedulers() {
    }

    /**
     * A scheduler of its own on one daemon thread, whose clock is {@link System#nanoTime()} in milliseconds. Each
     * call makes another, which its caller owns and closes once nothing follows a core on it.
     *
     * @return the scheduler
     */
    public static SystemScheduler system() {
        return new SystemScheduler();
    }
}
