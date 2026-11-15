package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.CoreCalls;
import com.weivel.handles.HandleEntry;
import java.util.concurrent.CompletableFuture;

/** A want of a plugin's, and its settlement. */
public final class PendingWant {
    private final ContextState state;
    private final PluginContext context;
    private final long want;
    private final CompletableFuture<Answer<Object>> settlement;

    PendingWant(ContextState state, PluginContext context, long want, CompletableFuture<Answer<Object>> settlement) {
        this.state = state;
        this.context = context;
        this.want = want;
        this.settlement = settlement;
    }

    /**
     * The want's settlement, completed once, from a dispatch step and outside every lock, always normally: with the
     * provider's own object, with the core's refusal, or with what the call answered in place of a want when the core
     * made none. A want withdrawn while it waits is never settled by the core, so its settlement is never completed.
     *
     * @return the settlement
     */
    public CompletableFuture<Answer<Object>> settlement() {
        return settlement;
    }

    /**
     * Withdraws the want while it still waits, so the core never settles it and {@link #settlement()} is never
     * completed. A want the core settled already, its settlement not yet dispatched, is not withdrawn: the core refuses
     * the withdrawal, and the settlement still completes.
     *
     * @return nothing beyond its success; the core's refusal for a want that has settled already; an absence for one
     *     the core never made; {@link Answer.Detached} once the plugin is forgotten
     */
    public Answer<Void> withdraw() {
        if (context.isDetached()) {
            return new Answer.Detached<>();
        }
        if (want == 0) {
            return new Answer.Absent<>();
        }
        HandleEntry[] ended = new HandleEntry[1];
        Answer<Void> answer = state.call(context, CoreCalls.unregister(want), withdrawn -> {
            ended[0] = state.handles.end(want);
            return new Answer.Ok<>(withdrawn);
        });
        if (ended[0] != null && ended[0].object() instanceof Records.Want) {
            ((Records.Want) ended[0].object()).holdingRoom().cancel();
        }
        return answer;
    }
}
