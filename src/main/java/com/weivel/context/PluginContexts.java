package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.Core;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreInternals;
import com.weivel.core.Manifest;
import com.weivel.core.QuarantineReason;
import com.weivel.core.Verification;
import com.weivel.handles.HandleEntry;
import com.weivel.handles.HandleKind;
import com.weivel.handles.Handles;
import com.weivel.timer.CoreTimer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The contexts of every plugin of one host, over what they share: the core, the table of its handles, its timer and
 * the host's log. It is the surface a host builds a plugin's context through, opens the plugin with, and ends,
 * detaches and frees it through.
 *
 * <ul>
 *   <li>{@link #make} builds a plugin's context, no later than the host first opens it; the host opens the plugin
 *   with {@link #open}, whose payload is {@link #payloadOf}, and the plugin keeps that context until it is forgotten,
 *   a quarantined plugin opened again included.</li>
 *   <li>Every event is routed to the plugin it concerns through one listener on the core, added once for all the
 *   contexts, by the handle the event names.</li>
 *   <li>{@link #verify} verifies the plugin's activation and, when the core refuses it, quarantines the plugin in the
 *   same hold of the layer lock, so no other call and no dispatch comes between them.</li>
 *   <li>{@link #registerHostService} registers a service of the host's own under {@code WEIVEL_HOST}, whose object a
 *   consumer resolves as any provider's.</li>
 *   <li>{@link #release} and {@link #quarantine} make that call in the core and, in the same hold of the layer lock,
 *   queue the end of the plugin's watches, listeners, registrations, implementations and answerers in the layer's
 *   tables right behind the call's own events. From then on the plugin hears nothing until it is opened again.</li>
 *   <li>{@link #forget} forgets the plugin in the core and detaches its context in the same hold of the layer lock,
 *   so no call through the context reaches the core after the forget, and ends its entries once the events drained
 *   before the forget have been dispatched.</li>
 *   <li>{@link #close} frees what the contexts hold once the core is destroyed.</li>
 * </ul>
 *
 * <p>Any thread may call.
 */
public final class PluginContexts implements AutoCloseable {
    private static final Set<HandleKind> ENDED_WITH_THE_PLUGIN = EnumSet.of(HandleKind.SERVICE_REGISTRATION,
            HandleKind.CAPABILITY_IMPLEMENTATION, HandleKind.WATCH);

    private final ContextState state;
    private final EventRouting routing;
    private final PluginContext hostContext;
    private volatile boolean closed;

    private PluginContexts(ContextState state, EventRouting routing) {
        this.state = state;
        this.routing = routing;
        this.hostContext = new PluginContext(state, CoreConstants.HOST, Manifest.named("host"), 0);
    }

    /**
     * Makes the contexts of one host, adding the one listener that routes the core's events to them.
     *
     * @param core the host's core
     * @param handles the table of the core's handles, which the contexts keep their records in
     * @param timer the core's timer, whose clock times a want and a capacity answer
     * @param log where the plugins' log and the core's own log events go
     * @return the contexts
     */
    public static PluginContexts create(Core core, Handles handles, CoreTimer timer, HostLog log) {
        ContextState state = new ContextState(Objects.requireNonNull(core, "core"),
                Objects.requireNonNull(handles, "handles"), Objects.requireNonNull(timer, "timer"),
                Objects.requireNonNull(log, "log"));
        EventRouting routing = new EventRouting(state);
        core.addListener(routing);
        return new PluginContexts(state, routing);
    }

    /**
     * Builds the context of a plugin the core registered, which hears no event drained before it was made.
     *
     * @param plugin the plugin's handle
     * @param manifest the plugin's manifest value, as the host registered it
     * @return the context
     * @throws IllegalStateException when the table holds the plugin's handle already, as it does once its context
     *     is made, or the contexts are closed
     */
    public PluginContext make(long plugin, Manifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        if (closed) {
            throw new IllegalStateException("the contexts are closed");
        }
        if (state.handles.find(plugin) != null) {
            throw new IllegalStateException("plugin " + plugin + " has its context, or another entry, already");
        }
        Handles.Reservation room = state.handles.reserve();
        PluginContext context = new PluginContext(state, plugin, manifest, CoreInternals.drainedCount(state.core));
        HandleEntry entry = room.add(HandleKind.PLUGIN, plugin, Handles.NO_OWNER, context);
        context.token(entry.token());
        state.contexts.add(context);
        return context;
    }

    /**
     * Opens the plugin in the core with its context's payload. A plugin opened again after a quarantine hears what is
     * queued for it from this open on.
     *
     * @param context the context
     * @return nothing beyond its success, the core's refusal, or {@link Answer.Detached} once the plugin is forgotten
     */
    public Answer<Void> open(PluginContext context) {
        return open(context, () -> { });
    }

    /**
     * Opens the plugin as {@link #open(PluginContext)} does, and runs {@code opened} once the core has answered the
     * open OK, under the layer lock and before any event of the open is dispatched.
     *
     * @param context the context
     * @param opened the layer's own record that the core holds the plugin open; it may not call into the core
     * @return nothing beyond its success, the core's refusal, or {@link Answer.Detached} once the plugin is forgotten
     */
    public Answer<Void> open(PluginContext context, Runnable opened) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(opened, "opened");
        return state.callThenDefer(context, CoreCalls.openPlugin(context.plugin(), context.token()), nothing -> {
            opened.run();
            return new Answer.Ok<>(nothing);
        }, context::unsilence);
    }

    /**
     * Verifies the plugin's activation and, when the core refuses it, quarantines the plugin in the same hold of the
     * layer lock, with no reason of the layer's, ending its entries once the quarantine's own events are dispatched.
     *
     * @param context the context
     * @return whether it was verified or quarantined, the core's answer when it refused the verification for another
     *     reason, or {@link Answer.Detached} once the plugin is forgotten
     */
    public Answer<Verification> verify(PluginContext context) {
        Objects.requireNonNull(context, "context");
        AtomicReference<Verification> answered = new AtomicReference<>();
        return state.callThenDefer(context, CoreCalls.verifyOrQuarantine(context.plugin()), verification -> {
            answered.set(verification);
            return new Answer.Ok<>(verification);
        }, () -> {
            if (answered.get().quarantinedInTheCore()) {
                endEntries(context);
            }
        });
    }

    /**
     * Registers a service the host itself supplies, under {@code WEIVEL_HOST}, whose object is answered to a consumer
     * as any provider's is. No release or quarantine ends it; only its unregistration or the core's destroy does.
     *
     * @param id the service id
     * @param service the host's own object
     * @return the registration; the core's refusal
     */
    public Answer<ServiceRegistration> registerHostService(String id, Object service) {
        return ServiceCalls.register(state, hostContext, Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(service, "service"), Optional.<CapacityAnswerer>empty());
    }

    /**
     * Releases the plugin in the core and ends its entries once the release's own events have been dispatched.
     *
     * @param context the context
     * @return nothing beyond its success, the core's refusal, or {@link Answer.Detached} once the plugin is forgotten
     */
    public Answer<Void> release(PluginContext context) {
        Objects.requireNonNull(context, "context");
        return state.callThenDefer(context, CoreCalls.releasePlugin(context.plugin()), Answer.Ok::new,
                () -> endEntries(context));
    }

    /**
     * Quarantines the plugin in the core and ends its entries once the quarantine's own events have been dispatched.
     * The plugin keeps its context, and {@link #open} opens it again.
     *
     * @param context the context
     * @param reason why, as the caller knows it; empty for none
     * @return nothing beyond its success, the core's refusal, or {@link Answer.Detached} once the plugin is forgotten
     */
    public Answer<Void> quarantine(PluginContext context, Optional<QuarantineReason> reason) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(reason, "reason");
        return state.callThenDefer(context, CoreCalls.quarantinePlugin(context.plugin(), reason), Answer.Ok::new,
                () -> endEntries(context));
    }

    /**
     * The payload the host opens the plugin with, which leads back to its context without a search.
     *
     * @param context the context
     * @return the payload
     */
    public long payloadOf(PluginContext context) {
        return Objects.requireNonNull(context, "context").token();
    }

    /**
     * The context a payload leads to.
     *
     * @param payload a payload the core answered for a plugin
     * @return the context; empty once its plugin is forgotten, or for a payload no context was made with
     */
    public Optional<PluginContext> byPayload(long payload) {
        HandleEntry entry = state.handles.findByToken(payload);
        return entry != null && entry.object() instanceof PluginContext
                ? Optional.of((PluginContext) entry.object())
                : Optional.<PluginContext>empty();
    }

    /**
     * Ends the plugin's own entries in the layer's tables once the core has released or quarantined it and the events
     * that call queued have been dispatched: its watches, listeners, registrations, implementations and answerers. It
     * calls nothing in the core, which ended them already. A want is left to its settlement, which the core always
     * makes: the release or quarantine rejected every want then waiting, and rejects every later one at once. A
     * listener added afterwards hears nothing queued after the release or quarantine until {@link #open} opens the
     * plugin again.
     *
     * @param context the context
     * @implNote Only {@link #release} and {@link #quarantine} queue it, in the hold of their call, so an open made
     * from another thread meanwhile is always ordered after it.
     */
    void endEntries(PluginContext context) {
        context.silence();
        state.handles.endOwned(context.plugin(), ENDED_WITH_THE_PLUGIN);
        context.dropListeners();
    }

    /**
     * Forgets the plugin in the core and, in the same hold of the layer lock, detaches its context; its entries, its
     * own among them, end once every event drained before the forget has been dispatched, so such an event still
     * finds what it names.
     *
     * @param context the context
     * @return nothing beyond its success, the core's refusal, or {@link Answer.Detached} for one forgotten already
     */
    public Answer<Void> forget(PluginContext context) {
        return state.callThenDefer(context, CoreCalls.forgetPlugin(context.plugin()), forgotten -> {
            context.detach();
            return new Answer.Ok<>(forgotten);
        }, () -> endForgotten(context));
    }

    private void endForgotten(PluginContext context) {
        state.handles.endOwned(context.plugin(), ENDED_WITH_THE_PLUGIN);
        state.handles.end(context.plugin());
        context.dropListeners();
        state.contexts.remove(context);
    }

    /**
     * Frees what the contexts hold, once the core is destroyed: the listener, every entry they made, and the
     * settlement of every want left waiting, which is completed as {@link Answer.Destroyed}, since no dispatch is left
     * to run.
     *
     * @throws IllegalStateException when the core is not destroyed
     */
    @Override
    public void close() {
        if (!state.core.isDestroyed()) {
            throw new IllegalStateException("the core is not destroyed; destroy it before closing its contexts");
        }
        closed = true;
        state.core.removeListener(routing);
        for (PluginContext context : Collections.unmodifiableList(new ArrayList<>(state.contexts))) {
            for (HandleEntry ended : state.handles.endOwned(context.plugin())) {
                if (ended.object() instanceof Records.Want) {
                    Records.Want want = (Records.Want) ended.object();
                    want.holdingRoom().cancel();
                    want.settlement().complete(new Answer.Destroyed<>());
                }
            }
            state.handles.end(context.plugin());
            context.dropListeners();
            state.contexts.remove(context);
        }
    }
}
