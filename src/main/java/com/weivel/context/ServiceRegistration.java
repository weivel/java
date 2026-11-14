package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.CoreCalls;

/** A service a plugin registered, kept until it unregisters it or stops. */
public final class ServiceRegistration {
    private final ContextState state;
    private final PluginContext context;
    private final long registration;

    ServiceRegistration(ContextState state, PluginContext context, long registration) {
        this.state = state;
        this.context = context;
        this.registration = registration;
    }

    /**
     * Unregisters the service, which the core revokes from everyone holding it.
     *
     * @return nothing beyond its success, or the core's refusal, as for one its plugin's release already ended
     * @implNote The registration's entry ends only once every event drained before the unregistration has been
     * dispatched, so a want the core settled with it, or a watch it reached, still finds the provider's object.
     */
    public Answer<Void> unregister() {
        return state.callThenDefer(context, CoreCalls.unregister(registration), Answer.Ok::new,
                () -> state.handles.end(registration));
    }
}
