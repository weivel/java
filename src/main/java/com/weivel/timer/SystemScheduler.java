package com.weivel.timer;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The scheduler {@link Schedulers#system()} makes: one daemon thread of a {@link ScheduledThreadPoolExecutor} of its
 * own, whose clock is {@link System#nanoTime()} in milliseconds. It takes a delay rather than a time, so it reads its
 * clock each time it schedules, to turn the time into that delay. It can wait any time.
 *
 * <p>Whoever made it owns it and closes it once nothing follows a core on it; a host closes the one it made. Its
 * thread runs the callbacks it was given, and so, when the timer fires, the listeners of the core it hands the time.
 */
public final class SystemScheduler implements Scheduler, AutoCloseable {
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000;

    private final ScheduledThreadPoolExecutor executor;

    SystemScheduler() {
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

    /**
     * Schedules {@code callback} on this scheduler's thread.
     *
     * @throws IllegalStateException once the scheduler is closed
     */
    @Override
    public Task schedule(long at, Runnable callback) {
        OnceTask task = new OnceTask(callback);
        try {
            task.future = executor.schedule(task, delayUntil(at, now()), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException closed) {
            throw new IllegalStateException("this scheduler is closed", closed);
        }
        return task;
    }

    /**
     * Shuts the scheduler's thread down: a task not yet begun never runs, and one running finishes on it.
     */
    @Override
    public void close() {
        executor.shutdownNow();
    }

    /**
     * Whether the scheduler's thread has ended, once it was closed.
     *
     * @param timeoutMillis how long to wait for it
     * @return whether it ended in that time
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public boolean awaitClosed(long timeoutMillis) throws InterruptedException {
        return executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * The delay from {@code now} until {@code at}: none when it has passed, and the longest there is when the
     * difference does not fit a {@code long}.
     */
    static long delayUntil(long at, long now) {
        long delay = at - now;
        boolean overflowed = ((at ^ now) & (at ^ delay)) < 0;
        if (overflowed) {
            return at > now ? Long.MAX_VALUE : 0;
        }
        return Math.max(0, delay);
    }

    /**
     * A callback run at most once, and a cancel that answers true only when it took the callback out before it began.
     *
     * @implNote A {@link java.util.concurrent.Future} cancelled while its callback runs answers true, so whether the
     * callback began is decided here, by whichever of the run and the cancel claims it first.
     */
    private static final class OnceTask implements Task, Runnable {
        private final Runnable callback;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private volatile ScheduledFuture<?> future;

        OnceTask(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void run() {
            if (claimed.compareAndSet(false, true)) {
                callback.run();
            }
        }

        @Override
        public boolean cancel() {
            if (!claimed.compareAndSet(false, true)) {
                return false;
            }
            ScheduledFuture<?> scheduled = future;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            return true;
        }
    }
}
