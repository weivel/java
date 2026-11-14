package com.weivel.host;

import com.weivel.core.ActivationPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * What {@link Host#activate} answers once every activate of its plan has been started, before any need have settled:
 * the plan, and each plugin's outcome as a stage that completes once it is known.
 */
public final class Activation {
    private final ActivationPlan plan;
    private final List<CompletableFuture<Outcome>> outcomes;

    Activation(ActivationPlan plan, List<CompletableFuture<Outcome>> outcomes) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.outcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
    }

    /**
     * Answers {@code plan}.
     *
     * @return the core's plan, cycles and refusals included, its order the order the activates were started in
     */
    public ActivationPlan plan() {
        return plan;
    }

    /**
     * Each plugin's outcome, at the index of its plugin in the plan's order.
     *
     * @return one stage per plugin started, each completing once, always normally
     */
    public List<CompletionStage<Outcome>> outcomes() {
        return Collections.<CompletionStage<Outcome>>unmodifiableList(outcomes);
    }

    /**
     * Every outcome, once all have arrived.
     *
     * @return a stage completing normally with the outcomes in the plan's order
     */
    public CompletionStage<List<Outcome>> settled() {
        return CompletableFuture.allOf(outcomes.toArray(new CompletableFuture<?>[0])).thenApply(nothing -> {
            List<Outcome> every = new ArrayList<>(outcomes.size());
            for (CompletableFuture<Outcome> outcome : outcomes) {
                every.add(outcome.join());
            }
            return Collections.unmodifiableList(every);
        });
    }
}
