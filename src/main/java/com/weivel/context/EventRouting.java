package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreEvent;
import com.weivel.core.CoreListener;
import com.weivel.core.FailureKind;
import com.weivel.core.RefusalStatus;
import com.weivel.handles.HandleEntry;
import com.weivel.handles.HandleKind;
import com.weivel.handles.Handles;
import java.util.Optional;

/**
 * The one listener the contexts of a host add to their core: it routes each event to the plugin it concerns by the
 * handle it names, through the table, and to the host's log.
 *
 * @implNote An event naming a plugin by its handle reaches that plugin's context only when it was drained after the
 * context was made, since before that the handle may have stood for another plugin. An event naming a want, a watch
 * or a registration reaches whatever record the table holds for that handle, which only a context made before the
 * call that answered the handle can have put there.
 */
final class EventRouting implements CoreListener {
    private final ContextState state;

    EventRouting(ContextState state) {
        this.state = state;
    }

    @Override
    public void hear(CoreEvent event) {
        switch (event) {
            case CoreEvent.WantSettledEvent settled -> settle(settled);
            case CoreEvent.WatchEvent watch -> watched(watch);
            case CoreEvent.CapabilityEvent capability -> capabilityChanged(capability);
            case CoreEvent.RevokedEvent revoked -> revoked(revoked);
            case CoreEvent.ReachRefusedEvent reach -> contextOf(reach.plugin(), reach).ifPresent(context ->
                    context.tell(new PluginEvent.ReachRefused(reach.kind(), reach.id(), reach.refusal())));
            case CoreEvent.CapacityWantedEvent question -> CapacityAnswering.answer(state, question);
            case CoreEvent.LogEvent log -> state.write(new LogRecord(log.level(), Optional.empty(), log.text()));
            case CoreEvent.RefusalEvent refusal -> {
            }
            case CoreEvent.DeadlineEvent deadline -> {
            }
        }
    }

    /** Settles a want once: whoever ends its entry, this settlement or its withdrawal, completes it. */
    private void settle(CoreEvent.WantSettledEvent settled) {
        HandleEntry entry = state.handles.end(settled.want());
        if (entry == null || !(entry.object() instanceof Records.Want want)) {
            return;
        }
        if (settled.implementation() == 0) {
            want.holdingRoom().cancel();
            want.settlement().complete(new Answer.Refused<>(RefusalStatus.REFUSED, settled.refusal()));
            return;
        }
        Optional<Object> service = serviceOf(settled.implementation(), settled);
        if (service.isEmpty()) {
            want.holdingRoom().cancel();
            want.settlement().complete(new Answer.Defect<>("the core settled a want with a registration the layer "
                    + "holds no object for"));
            return;
        }
        want.holdingRoom().hold(settled.plugin(), settled.implementation(), service.get());
        if (settled.degraded()) {
            contextOf(settled.plugin(), settled).ifPresent(context -> context.tell(
                    new PluginEvent.Degraded(want.id(), service.get(), settled.unmetConstraint())));
        }
        want.settlement().complete(new Answer.Ok<>(service.get()));
    }

    private void watched(CoreEvent.WatchEvent event) {
        Optional<Records.ServiceWatch> watch = state.recordOf(event.watch(), HandleKind.WATCH,
                Records.ServiceWatch.class);
        if (watch.isEmpty()) {
            return;
        }
        ServiceWatcher watcher = watch.get().watcher();
        if (event.change() == CoreConstants.CHANGE_REGISTERED) {
            serviceOf(event.implementation(), event).ifPresent(service -> {
                hold(event.plugin(), event.implementation(), service);
                state.callBack(() -> watcher.registered(service), FailureKind.PLUGIN_CALLBACK,
                        "a plugin's service watcher threw", Optional.of(event));
            });
        } else if (event.change() == CoreConstants.CHANGE_UNREGISTERED) {
            state.callBack(watcher::unregistered, FailureKind.PLUGIN_CALLBACK, "a plugin's service watcher threw",
                    Optional.of(event));
        }
    }

    private void capabilityChanged(CoreEvent.CapabilityEvent event) {
        Optional<Records.CapabilityWatch> watch = state.recordOf(event.watch(), HandleKind.WATCH,
                Records.CapabilityWatch.class);
        if (watch.isEmpty()) {
            return;
        }
        Optional<Object> implementation = state.recordOf(event.implementation(),
                HandleKind.CAPABILITY_IMPLEMENTATION, Records.Implementation.class)
                .map(Records.Implementation::implementation);
        if (implementation.isEmpty()) {
            unknown("a capability watch heard of an implementation the layer holds no object for", event);
            return;
        }
        CapabilityWatcher watcher = watch.get().watcher();
        if (event.change() == CoreConstants.CAPABILITY_PROVIDED) {
            hold(event.plugin(), event.implementation(), implementation.get());
            state.callBack(() -> watcher.provided(implementation.get()), FailureKind.PLUGIN_CALLBACK,
                    "a plugin's capability watcher threw", Optional.of(event));
        } else if (event.change() == CoreConstants.CAPABILITY_WITHDRAWN) {
            state.callBack(() -> watcher.withdrawn(implementation.get()), FailureKind.PLUGIN_CALLBACK,
                    "a plugin's capability watcher threw", Optional.of(event));
        }
    }

    /** Drops the holding the revocation names, even for a plugin forgotten, and tells the plugin of it. */
    private void revoked(CoreEvent.RevokedEvent event) {
        Object object = state.handles.revoke(event);
        if (object != null) {
            contextOf(event.plugin(), event).ifPresent(context -> context.tell(new PluginEvent.Revoked(object)));
        }
    }

    private Optional<Object> serviceOf(long registration, CoreEvent event) {
        Optional<Object> service = state.recordOf(registration, HandleKind.SERVICE_REGISTRATION,
                Records.Service.class).map(Records.Service::service);
        if (service.isEmpty()) {
            unknown("the core handed a plugin a registration the layer holds no object for", event);
        }
        return service;
    }

    private void hold(long plugin, long implementation, Object object) {
        Handles.Reservation room = state.handles.reserve();
        room.hold(plugin, implementation, object);
    }

    private void unknown(String text, CoreEvent event) {
        state.report(FailureKind.OBJECT_UNKNOWN, text, Optional.of(event), Optional.empty());
    }

    private Optional<PluginContext> contextOf(long plugin, CoreEvent event) {
        return state.recordOf(plugin, HandleKind.PLUGIN, PluginContext.class)
                .filter(context -> context.hears(event.sequence()));
    }
}
