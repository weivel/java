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
    private final TimerObserver observer;
    private final boolean following;
    private final long longestWait;
    private final Hooks hooks = new Hooks();
    private final ReentrantLock lock = new ReentrantLock();
    private long ticket;
    private Scheduler.Task task;
    private long armedAt;
    private boolean armed;
    private boolean destroyed;

    private CoreTimer(Core core, Scheduler scheduler, TimerObserver observer, boolean following, long longestWait) {
        this.core = core;
        this.scheduler = scheduler;
        this.observer = observer;
        this.following = following;
        this.longestWait = longestWait;
    }

    /**
     * Starts the timer of a core. With a scheduler it becomes the core's deadline hooks, and a deadline the core
     * already waits on is armed at once. On {@link Scheduler#none()} it installs nothing.
     *
     * @param core the core to follow
     * @param scheduler the caller's clock and scheduler
     * @return the timer; {@link Answer.Mistake} when another timer on a scheduler follows the core already, since one
     *     core is followed by one timer, or when the scheduler's {@link Scheduler#maximumWait()} is not positive, since
     *     it could wait for nothing; {@link Answer.Destroyed} for a destroyed core
     */
    public static Answer<CoreTimer> start(Core core, Scheduler scheduler) {
        return start(core, scheduler, TimerObserver.NONE);
    }

    /**
     * Starts the timer of a core as {@link #start(Core, Scheduler)} does, telling {@code observer} each move it makes.
     *
     * @param core the core to follow
     * @param scheduler the caller's clock and scheduler
     * @param observer what hears each arm and disarm as the core announces it
     * @return as {@link #start(Core, Scheduler)} answers
     */
    public static Answer<CoreTimer> start(Core core, Scheduler scheduler, TimerObserver observer) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(observer, "observer");
        boolean following = !(scheduler instanceof NoScheduler);
        long longestWait = following ? scheduler.maximumWait() : Long.MAX_VALUE;
        if (longestWait <= 0) {
            return new Answer.Mistake<>("a scheduler whose longest wait is " + longestWait
                    + " milliseconds can wait for no deadline");
        }
        CoreTimer timer = new CoreTimer(core, scheduler, observer, following, longestWait);
        if (following) {
            Answer<Void> installed = CoreInternals.installDeadlineHooks(core, timer.hooks);
            if (installed instanceof Answer.Ok) {
                return new Answer.Ok<>(timer);
            }
            if (installed instanceof Answer.Mistake) {
                return new Answer.Mistake<>(
                        "another timer follows this core already; destroy it before starting another");
            }
            return new Answer.Destroyed<>();
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
     * own. A scheduler that cannot take the task out, or that runs its callback all the same, finds the callback does
     * nothing. It must not be called under the layer lock, since taking the hooks off calls through the wrapper.
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

    /** The hooks the timer installs on its core: what a test calls in place of the wrapper. */
    DeadlineHooks hooks() {
        return hooks;
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
        tellObserver(set ? OptionalLong.of(at) : OptionalLong.empty());
    }

    private void tellObserver(OptionalLong deadline) {
        try {
            observer.moved(deadline);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report("the timer's observer failed when the timer moved", Optional.of(failure));
        }
    }

    /** Schedules the wait for {@code at} under {@code arming}, reading the clock only when the scheduler is bounded. */
    private void arm(long arming, long at, OptionalLong knownNow) {
        Optional<Scheduler.Task> scheduled = scheduled(arming, waitEnd(at, knownNow));
        if (!scheduled.isPresent()) {
            disarmIfCurrent(arming);
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
        if (longestWait == Long.MAX_VALUE) {
            return at;
        }
        long now = knownNow.isPresent() ? knownNow.getAsLong() : scheduler.now();
        long reachable = now > Long.MAX_VALUE - longestWait ? Long.MAX_VALUE : now + longestWait;
        return Math.min(at, reachable);
    }

    private Optional<Scheduler.Task> scheduled(long arming, long waitEnd) {
        try {
            return Optional.ofNullable(scheduler.schedule(waitEnd, () -> fire(arming)));
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report("the scheduler failed to schedule the timer, so the deadline is left to the check before each call",
                    Optional.of(failure));
            return Optional.empty();
        }
    }

    private void fire(long firing) {
        try {
            fireCurrent(firing);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report("the timer failed when it fired", Optional.of(failure));
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
        Answer<Void> advanced = core.call(CoreCalls.advance(now));
        if (!(advanced instanceof Answer.Ok)) {
            disarmAfterAFailedAdvance(firing, advanced);
        }
    }

    /**
     * Disarms a timer whose advance the core did not take, unless a newer announcement armed it meanwhile, so it
     * never reads armed with no task to fire; the deadline is left to the wrapper's check before each call.
     */
    private void disarmAfterAFailedAdvance(long firing, Answer<Void> advanced) {
        if (disarmIfCurrent(firing) && !(advanced instanceof Answer.Destroyed)) {
            report("the core did not take the time the timer handed it, so the deadline is left to the check before "
                    + "each call: " + advanced, Optional.empty());
        }
    }

    private boolean isCurrent(long firing) {
        lock.lock();
        try {
            return !destroyed && ticket == firing;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Disarms the timer when nothing armed it again since {@code arming}, so it never reads armed with no task.
     *
     * @return whether it disarmed
     */
    private boolean disarmIfCurrent(long arming) {
        lock.lock();
        try {
            if (destroyed || ticket != arming) {
                return false;
            }
            armed = false;
            ticket++;
            return true;
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
            report("the scheduler failed to cancel the timer's task", Optional.of(failure));
        }
    }

    private void report(String text, Optional<Throwable> failure) {
        CoreInternals.report(core, new Failure(FailureKind.DEADLINE_HOOK, text, 0, 0, failure));
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
