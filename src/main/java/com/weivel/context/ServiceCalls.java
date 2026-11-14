package com.weivel.context;

import com.weivel.core.Answer;
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
            return Answers.retyped(answer);
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
        state.settleLater(settlement, Answers.retyped(answer));
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
                : Answers.retyped(answer);
    }

    static Answer<ServiceRegistration> register(ContextState state, PluginContext context, String id, Object service,
                                                Optional<CapacityAnswerer> answerer) {
        Handles.Reservation room = state.handles.reserve();
        Records.Service record = new Records.Service(id, service, answerer);
        Answer<Long> answer = state.call(context, CoreCalls.registerService(context.plugin(), id), registration -> {
            room.add(HandleKind.SERVICE_REGISTRATION, registration, context.plugin(), record);
            return new Answer.Ok<>(registration);
        });
        room.cancel();
        return answer instanceof Answer.Ok
                ? new Answer.Ok<>(new ServiceRegistration(state, context, ((Answer.Ok<Long>) answer).value()))
                : Answers.retyped(answer);
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
