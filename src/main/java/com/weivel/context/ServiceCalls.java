package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.CallSteps;
import com.weivel.core.Choice;
import com.weivel.core.CoreCalls;
import com.weivel.core.FailureKind;
import com.weivel.core.WantOptions;
import com.weivel.handles.HandleKind;
import com.weivel.handles.Handles;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

/**
 * A context's calls about services. Each takes the room its record and its holding need before the core is asked, and
 * makes the record in the call's answered step, under the layer lock, before any event of the call is dispatched.
 */
final class ServiceCalls {
    private ServiceCalls() {
    }

    static Answer<Object> get(ContextState state, PluginContext context, String id) {
        Handles.Reservation room = state.handles.reserve();
        Resolution resolution = new Resolution();
        Answer<Long> answer = state.call(context, CoreCalls.getService(context.plugin(), id), registration -> {
            resolution.found = state.recordOf(registration, HandleKind.SERVICE_REGISTRATION, Records.Service.class)
                    .map(Records.Service::service);
            resolution.found.ifPresent(service -> room.hold(context.plugin(), registration, service));
            return new Answer.Ok<>(registration);
        });
        room.cancel();
        if (!(answer instanceof Answer.Ok)) {
            return Answer.retyped(answer);
        }
        if (!resolution.found.isPresent()) {
            String detail = "the core answered a get with a registration the layer holds no object for";
            state.report(FailureKind.OBJECT_UNKNOWN, detail, Optional.empty(), Optional.empty());
            return new Answer.Defect<>(detail);
        }
        return new Answer.Ok<>(resolution.found.get());
    }

    static Answer<List<String>> ids(ContextState state, PluginContext context) {
        return state.call(context, CoreCalls.serviceIds(context.plugin()), Answer.Ok::new);
    }

    static PendingWant want(ContextState state, PluginContext context, String id, WantLimits limits) {
        CompletableFuture<Answer<Object>> settlement = new CompletableFuture<>();
        if (context.isDetached()) {
            state.settleLater(settlement, new Answer.Detached<>());
            return new PendingWant(state, context, 0, settlement);
        }
        Optional<WantOptions> options = optionsFor(state, limits);
        if (!options.isPresent()) {
            state.settleLater(settlement, new Answer.Mistake<>("a want with a timeout, a budget or a poll interval "
                    + "needs the time, and the host's scheduler has no clock"));
            return new PendingWant(state, context, 0, settlement);
        }
        Handles.Reservation entryRoom = state.handles.reserve();
        Handles.Reservation holdingRoom = state.handles.reserve();
        Records.Want record = new Records.Want(id, settlement, holdingRoom);
        Answer<Long> answer = state.call(context, CoreCalls.wantService(context.plugin(), id, options.get()), want -> {
            entryRoom.add(HandleKind.WANT, want, context.plugin(), record);
            return new Answer.Ok<>(want);
        });
        if (answer instanceof Answer.Ok) {
            Answer.Ok<Long> made = (Answer.Ok<Long>) answer;
            return new PendingWant(state, context, made.value(), settlement);
        }
        entryRoom.cancel();
        holdingRoom.cancel();
        state.settleLater(settlement, Answer.retyped(answer));
        return new PendingWant(state, context, 0, settlement);
    }

    static Answer<Watching> watch(ContextState state, PluginContext context, String id, ServiceWatcher watcher) {
        Handles.Reservation room = state.handles.reserve();
        Answer<Long> answer = state.call(context, CoreCalls.watchService(context.plugin(), id), watch -> {
            room.add(HandleKind.WATCH, watch, context.plugin(), new Records.ServiceWatch(watcher));
            return new Answer.Ok<>(watch);
        });
        room.cancel();
        return answer instanceof Answer.Ok
                ? new Answer.Ok<>(new Watching(state, context, ((Answer.Ok<Long>) answer).value()))
                : Answer.retyped(answer);
    }

    static Answer<ServiceRegistration> register(ContextState state, PluginContext context, String id, Object service,
                                                Optional<CapacityAnswerer> answerer) {
        return register(state, context, id, service, answerer, () -> { });
    }

    static Answer<ServiceRegistration> register(ContextState state, PluginContext context, String id, Object service,
                                                Optional<CapacityAnswerer> answerer, Runnable registered) {
        return register(state, context, id, service, answerer, () -> Optional.<Answer<Long>>empty(), registered);
    }

    static Answer<ServiceRegistration> register(ContextState state, PluginContext context, String id, Object service,
                                                Optional<CapacityAnswerer> answerer, CallSteps.CallGuard<Long> guard,
                                                Runnable registered) {
        return register(state, context, id, service, answerer, guard, registered, Optional.<Runnable>empty());
    }

    /**
     * Registers as the other registrations do and, when {@code released} is present, awaiting releases: the core
     * keeps each holding past the unregistration until its holder lets go, and {@code released} runs from the
     * dispatch of the registration's {@code WEIVEL_EVENT_HOLDINGS_RELEASED}.
     */
    static Answer<ServiceRegistration> register(ContextState state, PluginContext context, String id, Object service,
                                                Optional<CapacityAnswerer> answerer, CallSteps.CallGuard<Long> guard,
                                                Runnable registered, Optional<Runnable> released) {
        Handles.Reservation room = state.handles.reserve();
        Records.Service record = new Records.Service(id, service, answerer);
        Answer<Long> answer = state.call(context, released.isPresent()
                ? CoreCalls.registerServiceAwaitingReleases(context.plugin(), id)
                : CoreCalls.registerService(context.plugin(), id), guard,
                registration -> {
                    room.add(HandleKind.SERVICE_REGISTRATION, registration, context.plugin(), record);
                    released.ifPresent(then -> state.releases.await(registration, context.plugin(), id, service, then));
                    registered.run();
                    return new Answer.Ok<>(registration);
                });
        room.cancel();
        return answer instanceof Answer.Ok
                ? new Answer.Ok<>(new ServiceRegistration(state, context, ((Answer.Ok<Long>) answer).value()))
                : Answer.retyped(answer);
    }

    /**
     * Ends the plugin's revoked holding a revocation named in the core, for a registration made awaiting releases.
     *
     * @return true; false, calling nothing, when the plugin keeps no such revoked holding
     */
    static Answer<Boolean> releaseHolding(ContextState state, PluginContext context, PluginEvent.Revoked revoked) {
        if (!revoked.awaitsRelease()
                || !state.releases.takeRevoked(context.plugin(), revoked.registration(), revoked.object())) {
            return new Answer.Ok<>(false);
        }
        Answer<Void> released = state.call(context, CoreCalls.releaseHolding(context.plugin(),
                revoked.registration()), Answer.Ok::new);
        return released instanceof Answer.Ok ? new Answer.Ok<>(true) : Answer.retyped(released);
    }

    /** The want's options, each limit as the plugin gave it; empty when it needs a time no clock can give. */
    private static Optional<WantOptions> optionsFor(ContextState state, WantLimits limits) {
        if (!limits.needsTheTime()) {
            return Optional.of(WantOptions.unbounded());
        }
        OptionalLong now = state.timer.time();
        if (!now.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new WantOptions(chosen(limits.timeoutMillis()), now.getAsLong(),
                limits.timeoutMillis().orElse(0), chosen(limits.budgetMillis()), limits.budgetMillis().orElse(0),
                limits.pollMillis().orElse(0)));
    }

    private static Choice chosen(OptionalLong limit) {
        return limit.isPresent() ? Choice.YES : Choice.DEFAULT;
    }

    /** What a get's answered step found, read once the call has returned. */
    private static final class Resolution {
        private Optional<Object> found = Optional.empty();
    }
}
