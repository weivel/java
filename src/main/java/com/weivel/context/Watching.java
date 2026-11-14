package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.CoreCalls;

/** A watch of a service or of a capability, until it stops. */
public final class Watching {
    private final ContextState state;
    private final PluginContext context;
    private final long watch;

    Watching(ContextState state, PluginContext context, long watch) {
        this.state = state;
        this.context = context;
        this.watch = watch;
    }

    /**
     * Stops the watch in the core; once the core has stopped it, the watcher hears nothing more.
     *
     * @return nothing beyond its success, or the core's refusal, as for a watch its plugin's release already stopped
     */
    public Answer<Void> stop() {
        return state.call(context, CoreCalls.unregister(watch), stopped -> {
            state.handles.end(watch);
            return new Answer.Ok<>(stopped);
        });
    }
}
