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
     */
    public Answer<Void> unregister() {
        return state.call(context, CoreCalls.unregister(registration), unregistered -> {
            state.handles.end(registration);
            return new Answer.Ok<>(unregistered);
        });
    }
}
