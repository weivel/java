package com.weivel.timer;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Schedulers the layer offers its callers. */
public final class Schedulers {
    private Schedulers() {
    }

    /**
     * The process's scheduler: one daemon thread of a {@link ScheduledThreadPoolExecutor}, shared by every caller and
     * never shut down, whose clock is {@link System#nanoTime()} in milliseconds. It takes a delay rather than a time,
     * so it reads its clock each time it schedules, to turn the time into that delay. It can wait any time.
     *
     * @return the scheduler
     */
    public static Scheduler system() {
        return SystemScheduler.INSTANCE;
    }

    /** The scheduler {@link #system()} answers. */
    private static final class SystemScheduler implements Scheduler {
        private static final SystemScheduler INSTANCE = new SystemScheduler();
        private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000;

        private final ScheduledThreadPoolExecutor executor;

        private SystemScheduler() {
            executor = new ScheduledThreadPoolExecutor(1, runnable -> {
                Thread thread = new Thread(runnable, "weivel-timer");
                thread.setDaemon(true);
                return thread;
            });
            executor.setRemoveOnCancelPolicy(true);
        }

        @Override
        public long now() {
            return System.nanoTime() / NANOSECONDS_PER_MILLISECOND;
        }

        @Override
        public Task schedule(long at, Runnable callback) {
            long delay = Math.max(0, at - now());
            ScheduledFuture<?> future = executor.schedule(callback, delay, TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        }
    }
}
