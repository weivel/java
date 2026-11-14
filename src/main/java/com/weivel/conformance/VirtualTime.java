package com.weivel.conformance;

import com.weivel.timer.Scheduler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The clock and the scheduler the suite owns and hands the layer in place of the language's own: time moves only
 * when the suite moves it, and a task runs only when the suite runs time forward over its due time, at that time, on
 * the suite's thread.
 */
final class VirtualTime implements Scheduler {
    private final List<VirtualTask> tasks = new ArrayList<>();
    private final List<Long> fired = new ArrayList<>();
    private long clock;

    @Override
    public synchronized long now() {
        return clock;
    }

    @Override
    public Task schedule(long at, Runnable callback) {
        VirtualTask task = new VirtualTask(at, callback);
        synchronized (this) {
            tasks.add(task);
        }
        return task;
    }

    /**
     * Runs every task due by {@code time}, earliest first and those scheduled meanwhile included, the clock standing
     * at each one's due time as it runs, then stands at {@code time}.
     *
     * @param afterEach what the suite does once each task has run
     */
    void runUntil(long time, Runnable afterEach) {
        for (VirtualTask due = nextDue(time); due != null; due = nextDue(time)) {
            due.callback.run();
            afterEach.run();
        }
        moveClockTo(time);
    }

    /** Moves the clock to {@code time} and runs no task, as a scheduler that has not yet taken its turn. */
    synchronized void moveClockTo(long time) {
        clock = Math.max(clock, time);
    }

    /** The clock's time each time a task actually ran, in the order they ran. */
    synchronized List<Long> fired() {
        return Collections.unmodifiableList(new ArrayList<>(fired));
    }

    private synchronized VirtualTask nextDue(long time) {
        VirtualTask earliest = null;
        for (VirtualTask task : tasks) {
            if (task.at <= time && (earliest == null || task.at < earliest.at)) {
                earliest = task;
            }
        }
        if (earliest != null) {
            tasks.remove(earliest);
            clock = Math.max(clock, earliest.at);
            fired.add(clock);
        }
        return earliest;
    }

    private synchronized boolean takeOut(VirtualTask task) {
        return tasks.remove(task);
    }

    private final class VirtualTask implements Task {
        private final long at;
        private final Runnable callback;

        private VirtualTask(long at, Runnable callback) {
            this.at = at;
            this.callback = callback;
        }

        @Override
        public boolean cancel() {
            return takeOut(this);
        }
    }
}
