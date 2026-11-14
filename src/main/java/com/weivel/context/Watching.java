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
     * Stops the watch in the core: the watcher hears nothing the core reports after the stop, and still hears what it
     * reported before.
     *
     * @return nothing beyond its success, or the core's refusal, as for a watch its plugin's release already stopped
     * @implNote The watch's entry ends only once every event drained before the stop has been dispatched, since each
     * was reported while the watch lived.
     */
    public Answer<Void> stop() {
        return state.callThenDefer(context, CoreCalls.unregister(watch), Answer.Ok::new,
                () -> state.handles.end(watch));
    }
}
