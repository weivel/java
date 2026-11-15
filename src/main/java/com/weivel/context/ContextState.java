package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.CallSteps;
import com.weivel.core.Core;
import com.weivel.core.CoreCall;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreEvent;
import com.weivel.core.CoreInternals;
import com.weivel.core.DeferredStep;
import com.weivel.core.Failure;
import com.weivel.core.FailureKind;
import com.weivel.handles.HandleEntry;
import com.weivel.handles.HandleKind;
import com.weivel.handles.Handles;
import com.weivel.timer.CoreTimer;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What every context of one host shares: the core, the table, the timer, the host's log, and the contexts made.
 *
 * @implNote It takes no lock of its own. A context's call takes the layer lock, through the wrapper, and inside it,
 * from the call's guard and answered step, the table's lock, which is always the innermost; nothing here ever takes
 * them the other way round. What changes outside those locks is held in concurrent collections and volatile fields.
 */
final class ContextState {
    final Core core;
    final Handles handles;
    final CoreTimer timer;
    final HostLog hostLog;
    final Set<PluginContext> contexts = ConcurrentHashMap.newKeySet();
    final Releases releases = new Releases();

    ContextState(Core core, Handles handles, CoreTimer timer, HostLog hostLog) {
        this.core = core;
        this.handles = handles;
        this.timer = timer;
        this.hostLog = hostLog;
    }

    /**
     * Makes one call for {@code context}, answered detached once the core has forgotten the plugin.
     *
     * @implNote Detachment is read once before the call, so a detached context answers detached before the wrapper
     * judges a caller's mistake in the call, and decided under the layer lock by the guard, which a forget on another
     * thread may have overtaken.
     */
    <T> Answer<T> call(PluginContext context, CoreCall<T> call, CallSteps.AnsweredStep<T> answered) {
        if (context.isDetached()) {
            return new Answer.Detached<>();
        }
        return CoreInternals.call(core, call, new CallSteps<>(context::guarded, answered));
    }

    /** Makes one call for {@code context} as {@link #call} does, the call made only when {@code guard} lets it. */
    <T> Answer<T> call(PluginContext context, CoreCall<T> call, CallSteps.CallGuard<T> guard,
                       CallSteps.AnsweredStep<T> answered) {
        if (context.isDetached()) {
            return new Answer.Detached<>();
        }
        return CoreInternals.call(core, call, new CallSteps<>(() -> {
            Optional<Answer<T>> detached = context.guarded();
            return detached.isPresent() ? detached : guard.check();
        }, answered));
    }

    /**
     * Makes one call for {@code context} as {@link #call} does and, when the core answers it OK, queues {@code step}
     * right behind the call's own events, so it runs once every event drained before it has been dispatched.
     */
    <T> Answer<T> callThenDefer(PluginContext context, CoreCall<T> call, CallSteps.AnsweredStep<T> answered,
                                Runnable step) {
        if (context.isDetached()) {
            return new Answer.Detached<>();
        }
        return CoreInternals.callThenDefer(core, call, new CallSteps<>(context::guarded, answered), () -> {
            step.run();
            return DeferredStep.Status.DONE;
        });
    }

    /** The object of the entry of {@code handle} when it is of {@code kind}; empty when the table holds none such. */
    <T> Optional<T> recordOf(long handle, HandleKind kind, Class<T> type) {
        HandleEntry entry = handles.find(handle);
        return entry != null && entry.kind() == kind && type.isInstance(entry.object())
                ? Optional.of(type.cast(entry.object()))
                : Optional.empty();
    }

    /**
     * Completes a want's settlement from a dispatch step, never inside the call that asked for it and never under a
     * lock: once the core is destroyed no dispatch is left to run it, so it is completed at once.
     */
    void settleLater(CompletableFuture<Answer<Object>> settlement, Answer<Object> answer) {
        DeferredStep.Deferral deferral = CoreInternals.defer(core, () -> {
            settlement.complete(answer);
            return DeferredStep.Status.DONE;
        });
        if (deferral == DeferredStep.Deferral.DESTROYED) {
            settlement.complete(answer);
        }
    }

    /** Runs a plugin's or the host's code; what it throws, but a virtual machine error, goes to the error sink. */
    void callBack(Runnable callback, FailureKind kind, String text, Optional<CoreEvent> event) {
        try {
            callback.run();
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report(kind, text, event, Optional.of(failure));
        }
    }

    void report(FailureKind kind, String text, Optional<CoreEvent> event, Optional<Throwable> cause) {
        CoreInternals.report(core, new Failure(kind, text, event.map(ContextState::kindOf).orElse(0),
                event.map(CoreEvent::sequence).orElse(0L), cause));
    }

    void write(LogRecord record) {
        callBack(() -> hostLog.write(record), FailureKind.LOG, "the host's log threw", Optional.empty());
    }

    private static int kindOf(CoreEvent event) {
        if (event instanceof CoreEvent.LogEvent) {
            return CoreConstants.EVENT_LOG;
        }
        if (event instanceof CoreEvent.RefusalEvent) {
            return CoreConstants.EVENT_REFUSAL;
        }
        if (event instanceof CoreEvent.WantSettledEvent) {
            return CoreConstants.EVENT_WANT_SETTLED;
        }
        if (event instanceof CoreEvent.WatchEvent) {
            return CoreConstants.EVENT_WATCH;
        }
        if (event instanceof CoreEvent.RevokedEvent) {
            return CoreConstants.EVENT_REVOKED;
        }
        if (event instanceof CoreEvent.ReachRefusedEvent) {
            return CoreConstants.EVENT_REACH_REFUSED;
        }
        if (event instanceof CoreEvent.CapacityWantedEvent) {
            return CoreConstants.EVENT_CAPACITY_WANTED;
        }
        if (event instanceof CoreEvent.CapabilityEvent) {
            return CoreConstants.EVENT_CAPABILITY;
        }
        if (event instanceof CoreEvent.HoldingsReleasedEvent) {
            return CoreConstants.EVENT_HOLDINGS_RELEASED;
        }
        return CoreConstants.EVENT_DEADLINE;
    }
}
