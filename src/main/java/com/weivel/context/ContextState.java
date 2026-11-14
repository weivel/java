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

    ContextState(Core core, Handles handles, CoreTimer timer, HostLog hostLog) {
        this.core = core;
        this.handles = handles;
        this.timer = timer;
        this.hostLog = hostLog;
    }

    /** Makes one call for {@code context}, whose guard answers it detached once the core has forgotten the plugin. */
    <T> Answer<T> call(PluginContext context, CoreCall<T> call, CallSteps.AnsweredStep<T> answered) {
        return CoreInternals.call(core, call, new CallSteps<>(context::guarded, answered));
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
        return switch (event) {
            case CoreEvent.LogEvent log -> CoreConstants.EVENT_LOG;
            case CoreEvent.RefusalEvent refusal -> CoreConstants.EVENT_REFUSAL;
            case CoreEvent.WantSettledEvent want -> CoreConstants.EVENT_WANT_SETTLED;
            case CoreEvent.WatchEvent watch -> CoreConstants.EVENT_WATCH;
            case CoreEvent.RevokedEvent revoked -> CoreConstants.EVENT_REVOKED;
            case CoreEvent.ReachRefusedEvent reach -> CoreConstants.EVENT_REACH_REFUSED;
            case CoreEvent.CapacityWantedEvent capacity -> CoreConstants.EVENT_CAPACITY_WANTED;
            case CoreEvent.CapabilityEvent capability -> CoreConstants.EVENT_CAPABILITY;
            case CoreEvent.DeadlineEvent deadline -> CoreConstants.EVENT_DEADLINE;
        };
    }
}
