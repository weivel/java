package com.weivel.timer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

/**
 * A clock and a scheduler the test owns: time moves only when the test moves it, and a task runs only when the test
 * moves time over its due time, on the test's thread. It counts every clock read and keeps every task it was given.
 */
public final class TestScheduler implements Scheduler {
    private final Object lock = new Object();
    private final AtomicInteger reads = new AtomicInteger();
    private final List<TestTask> tasks = new ArrayList<>();
    private final List<Long> fired = new ArrayList<>();
    private volatile long clock;
    private volatile long maximumWait = Long.MAX_VALUE;
    private volatile Runnable onRead = () -> { };
    private volatile LongConsumer onSchedule = at -> { };

    @Override
    public long now() {
        reads.incrementAndGet();
        onRead.run();
        return clock;
    }

    @Override
    public Task schedule(long at, Runnable callback) {
        onSchedule.accept(at);
        TestTask task = new TestTask(at, callback);
        synchronized (lock) {
            tasks.add(task);
        }
        return task;
    }

    @Override
    public long maximumWait() {
        return maximumWait;
    }

    void limitWaitsTo(long longest) {
        maximumWait = longest;
    }

    public void whenRead(Runnable action) {
        onRead = action;
    }

    void whenScheduled(LongConsumer action) {
        onSchedule = action;
    }

    /** Sets the clock without running any task. */
    public void setClock(long time) {
        clock = time;
    }

    /**
     * Runs every task due by {@code time}, earliest first, those scheduled meanwhile included, the clock moved on to
     * each one's due time as it runs unless it is past it already; then sets the clock to {@code time}.
     */
    public void moveTo(long time) {
        TestTask due = nextDue(time);
        while (due != null) {
            clock = Math.max(clock, due.at);
            due.callback.run();
            due = nextDue(time);
        }
        clock = Math.max(clock, time);
    }

    private TestTask nextDue(long time) {
        synchronized (lock) {
            TestTask due = tasks.stream().filter(task -> task.live() && task.at <= time)
                    .min(Comparator.comparingLong(task -> task.at)).orElse(null);
            if (due != null) {
                due.ran = true;
                fired.add(due.at);
            }
            return due;
        }
    }

    int reads() {
        return reads.get();
    }

    /** The due time of every task neither run nor cancelled. */
    public List<Long> live() {
        synchronized (lock) {
            return tasks.stream().filter(TestTask::live).map(task -> task.at).toList();
        }
    }

    /** The due time of every task given, in the order given. */
    List<Long> scheduled() {
        synchronized (lock) {
            return tasks.stream().map(task -> task.at).toList();
        }
    }

    /** The due time of every task run, in the order run. */
    List<Long> fired() {
        synchronized (lock) {
            return List.copyOf(fired);
        }
    }

    TestTask last() {
        synchronized (lock) {
            return tasks.getLast();
        }
    }

    /** One task; {@link #runAnyway()} runs its callback whatever became of it, as a late scheduler would. */
    final class TestTask implements Task {
        private final long at;
        private final Runnable callback;
        private boolean ran;
        private boolean cancelled;

        private TestTask(long at, Runnable callback) {
            this.at = at;
            this.callback = callback;
        }

        @Override
        public boolean cancel() {
            synchronized (lock) {
                if (ran || cancelled) {
                    return false;
                }
                cancelled = true;
                return true;
            }
        }

        boolean cancelled() {
            synchronized (lock) {
                return cancelled;
            }
        }

        void runAnyway() {
            callback.run();
        }

        private boolean live() {
            return !ran && !cancelled;
        }
    }
}
