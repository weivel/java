package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.Core;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreInternals;
import com.weivel.core.Manifest;
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

/**
 * The contexts of every plugin of one host, over what they share: the core, the table of its handles, its timer and
 * the host's log. It is the surface a host builds a plugin's context through, opens the plugin with, and ends,
 * detaches and frees it through.
 *
 * <ul>
 *   <li>{@link #make} builds a plugin's context, no later than the host first opens it; the host opens the plugin
 *   with {@link #payloadOf} as its payload, and the plugin keeps that context until it is forgotten, a quarantined
 *   plugin opened again included.</li>
 *   <li>Every event is routed to the plugin it concerns through one listener on the core, added once for all the
 *   contexts, by the handle the event names.</li>
 *   <li>{@link #endEntries} ends a plugin's watches, listeners, registrations, implementations and answerers in the
 *   layer's tables, calling nothing in the core, once the events its release or quarantine call queued have been
 *   dispatched; a host makes it a step deferred behind that call's events.</li>
 *   <li>{@link #forget} forgets the plugin in the core and detaches its context in the same hold of the layer lock,
 *   so no call through the context reaches the core after the forget.</li>
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
    private volatile boolean closed;

    private PluginContexts(ContextState state, EventRouting routing) {
        this.state = state;
        this.routing = routing;
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
     * makes: the release or quarantine rejected every want then waiting, and rejects every later one at once.
     *
     * @param context the context
     */
    public void endEntries(PluginContext context) {
        state.handles.endOwned(context.plugin(), ENDED_WITH_THE_PLUGIN);
        context.dropListeners();
    }

    /**
     * Forgets the plugin in the core and, in the same hold of the layer lock, detaches its context and ends its
     * entries, its own among them.
     *
     * @param context the context
     * @return nothing beyond its success, the core's refusal, or {@link Answer.Detached} for one forgotten already
     */
    public Answer<Void> forget(PluginContext context) {
        Answer<Void> answer = state.call(context, CoreCalls.forgetPlugin(context.plugin()), forgotten -> {
            context.detach();
            state.handles.endOwned(context.plugin(), ENDED_WITH_THE_PLUGIN);
            state.handles.end(context.plugin());
            return new Answer.Ok<>(forgotten);
        });
        if (answer instanceof Answer.Ok) {
            context.dropListeners();
            state.contexts.remove(context);
        }
        return answer;
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
