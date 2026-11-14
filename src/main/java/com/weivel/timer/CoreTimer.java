package com.weivel.timer;

import com.weivel.core.Answer;
import com.weivel.core.Core;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreInternals;
import com.weivel.core.DeadlineHooks;
import com.weivel.core.Failure;
import com.weivel.core.FailureKind;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Arms one core's next deadline on its caller's {@link Scheduler}.
 *
 * <ul>
 *   <li>It is armed from the latest deadline announcement the wrapper drained and from nothing else: one with a
 *   deadline schedules one task for it, cancelling the task of any deadline armed before, and one of none cancels it.
 *   The wrapper tells the timer as the call that drained the announcement leaves it, before any of its events is
 *   dispatched, and tells it last of the latest one drained, whichever thread drained it.</li>
 *   <li>When the task runs it reads the clock and hands the core that time with {@code weivel_advance}, through the
 *   wrapper; what that advance queues arms or disarms the timer again.</li>
 *   <li>A scheduler that cannot wait as long as the deadline lies away is waited on as long as it can wait, and the
 *   timer arms again when that wait ends, never advancing the core before the deadline.</li>
 *   <li>While a deadline is armed the wrapper reads this clock once before each call and advances the core first when
 *   the clock has reached it, so the core learns a deadline passed before any other call, whether the task ran or
 *   not.</li>
 *   <li>The clock is read only there, when the task runs, and when the timer schedules on a scheduler that cannot
 *   wait any time. It keeps no list of the calls that move a deadline, never asks the core for its deadline, and
 *   hands the core no time but the one its scheduler's clock answered.</li>
 * </ul>
 *
 * <p>A timer on {@link Scheduler#none()} arms nothing and reads no clock, and the wrapper then checks nothing.
 *
 * <p>Any thread may call. The timer's own lock is held only inside its methods and never while the scheduler, the
 * clock or the core runs.
 *
 * @implNote Every task carries the ticket its scheduling was given, and every arm, disarm and destroy draws a new one,
 * so a callback that runs after its task was replaced or cancelled, because the scheduler could not take it out, finds
 * its ticket stale and does nothing: before it reads the clock, and again after, since an arm or a destroy may land
 * on another thread while it reads.
 */
public final class CoreTimer {
    private final Core core;
    private final Scheduler scheduler;
    private final boolean following;
    private final Hooks hooks = new Hooks();
    private final ReentrantLock lock = new ReentrantLock();
    private long ticket;
    private Scheduler.Task task;
    private long armedAt;
    private boolean armed;
    private boolean destroyed;

    private CoreTimer(Core core, Scheduler scheduler, boolean following) {
        this.core = core;
        this.scheduler = scheduler;
        this.following = following;
    }

    /**
     * Starts the timer of a core. With a scheduler it becomes the core's deadline hooks, and a deadline the core
     * already waits on is armed at once. On {@link Scheduler#none()} it installs nothing.
     *
     * @param core the core to follow
     * @param scheduler the caller's clock and scheduler
     * @return the timer; {@link Answer.Mistake} when another timer on a scheduler follows the core already, since one
     *     core is followed by one timer; {@link Answer.Destroyed} for a destroyed core
     */
    public static Answer<CoreTimer> start(Core core, Scheduler scheduler) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(scheduler, "scheduler");
        boolean following = !(scheduler instanceof NoScheduler);
        CoreTimer timer = new CoreTimer(core, scheduler, following);
        if (following) {
            return switch (CoreInternals.installDeadlineHooks(core, timer.hooks)) {
                case Answer.Ok<Void> installed -> new Answer.Ok<>(timer);
                case Answer.Mistake<Void> taken -> new Answer.Mistake<>(
                        "another timer follows this core already; destroy it before starting another");
                default -> new Answer.Destroyed<>();
            };
        }
        if (core.isDestroyed()) {
            return new Answer.Destroyed<>();
        }
        return new Answer.Ok<>(timer);
    }

    /**
     * The time now on the scheduler's clock: what the layer hands the core as {@code now} for a want with a timeout
     * or with constraints.
     *
     * @return the time; empty on {@link Scheduler#none()}, which has no clock to time a want by
     */
    public OptionalLong time() {
        return following ? OptionalLong.of(scheduler.now()) : OptionalLong.empty();
    }

    /**
     * The deadline the timer is armed for, as it was last told.
     *
     * @return the deadline; empty when it is disarmed, which a timer on no scheduler and a destroyed one always are
     */
    public OptionalLong armed() {
        lock.lock();
        try {
            return armed ? OptionalLong.of(armedAt) : OptionalLong.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Disarms the timer for good: it cancels the one task it holds and takes its own hooks off the core, and only its
     * own, so nothing of the layer's is left for the scheduler to run. A callback the scheduler begins all the same does nothing. It must not
     * be called under the layer lock, since taking the hooks off calls through the wrapper.
     */
    public void destroy() {
        Scheduler.Task cancelled;
        lock.lock();
        try {
            if (destroyed) {
                return;
            }
            destroyed = true;
            armed = false;
            ticket++;
            cancelled = task;
            task = null;
        } finally {
            lock.unlock();
        }
        cancel(cancelled);
        if (following) {
            CoreInternals.removeDeadlineHooks(core, hooks);
        }
    }

    private void follow(long at, boolean set) {
        Scheduler.Task cancelled;
        long arming;
        lock.lock();
        try {
            if (destroyed) {
                return;
            }
            armed = set;
            armedAt = at;
            arming = ++ticket;
            cancelled = task;
            task = null;
        } finally {
            lock.unlock();
        }
        cancel(cancelled);
        if (set) {
            arm(arming, at, OptionalLong.empty());
        }
    }

    /** Schedules the wait for {@code at} under {@code arming}, reading the clock only when the scheduler is bounded. */
    private void arm(long arming, long at, OptionalLong knownNow) {
        Optional<Scheduler.Task> scheduled = scheduled(arming, waitEnd(at, knownNow));
        if (scheduled.isEmpty()) {
            return;
        }
        boolean current;
        lock.lock();
        try {
            current = !destroyed && ticket == arming;
            if (current) {
                task = scheduled.get();
            }
        } finally {
            lock.unlock();
        }
        if (!current) {
            cancel(scheduled.get());
        }
    }

    private long waitEnd(long at, OptionalLong knownNow) {
        long longest = scheduler.maximumWait();
        if (longest == Long.MAX_VALUE) {
            return at;
        }
        long now = knownNow.isPresent() ? knownNow.getAsLong() : scheduler.now();
        long reachable = now > Long.MAX_VALUE - longest ? Long.MAX_VALUE : now + longest;
        return Math.min(at, reachable);
    }

    private Optional<Scheduler.Task> scheduled(long arming, long waitEnd) {
        try {
            return Optional.ofNullable(scheduler.schedule(waitEnd, () -> fire(arming)));
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report("the scheduler failed to schedule the timer, so the deadline is left to the check before each call",
                    failure);
            return Optional.empty();
        }
    }

    private void fire(long firing) {
        try {
            fireCurrent(firing);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report("the timer failed when it fired", failure);
        }
    }

    private void fireCurrent(long firing) {
        if (!isCurrent(firing)) {
            return;
        }
        long now = scheduler.now();
        long at;
        long rearming;
        lock.lock();
        try {
            if (destroyed || ticket != firing) {
                return;
            }
            task = null;
            at = armedAt;
            rearming = now < at ? ++ticket : ticket;
        } finally {
            lock.unlock();
        }
        if (now < at) {
            arm(rearming, at, OptionalLong.of(now));
            return;
        }
        core.call(CoreCalls.advance(now));
    }

    private boolean isCurrent(long firing) {
        lock.lock();
        try {
            return !destroyed && ticket == firing;
        } finally {
            lock.unlock();
        }
    }

    private void cancel(Scheduler.Task cancelled) {
        if (cancelled == null) {
            return;
        }
        try {
            cancelled.cancel();
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report("the scheduler failed to cancel the timer's task", failure);
        }
    }

    private void report(String text, Throwable failure) {
        CoreInternals.report(core, new Failure(FailureKind.DEADLINE_HOOK, text, 0, 0, Optional.of(failure)));
    }

    /** What the wrapper calls: the clock for its check before a call, and each announcement it drained. */
    private final class Hooks implements DeadlineHooks {
        @Override
        public long now() {
            return scheduler.now();
        }

        @Override
        public void follow(long at, boolean set) {
            CoreTimer.this.follow(at, set);
        }
    }
}
