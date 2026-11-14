package com.weivel.host;

import com.weivel.core.Refusal;
import java.util.Objects;
import java.util.Optional;

/** Where one plugin's activation ended, and the core's refusal when the core refused to open it. */
public final class Outcome {
    private final long plugin;
    private final ActivationState state;
    private final Optional<Refusal> refusal;

    /**
     * Checks that every part is present.
     *
     * @param plugin the plugin's handle
     * @param state where its activation ended
     * @param refusal the core's refusal of its open; empty for every other state
     */
    public Outcome(long plugin, ActivationState state, Optional<Refusal> refusal) {
        this.plugin = plugin;
        this.state = Objects.requireNonNull(state, "state");
        this.refusal = Objects.requireNonNull(refusal, "refusal");
    }

    /**
     * Answers {@code plugin}.
     *
     * @return the plugin's handle
     */
    public long plugin() {
        return plugin;
    }

    /**
     * Answers {@code state}.
     *
     * @return where its activation ended
     */
    public ActivationState state() {
        return state;
    }

    /**
     * Answers {@code refusal}.
     *
     * @return the core's refusal of its open, as the core queued it; empty unless {@link ActivationState#REFUSED}
     */
    public Optional<Refusal> refusal() {
        return refusal;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Outcome)) {
            return false;
        }
        Outcome that = (Outcome) other;
        return plugin == that.plugin && state == that.state && refusal.equals(that.refusal);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(plugin);
        hash = 31 * hash + state.hashCode();
        hash = 31 * hash + refusal.hashCode();
        return hash;
    }

    @Override
    public String toString() {
        return "Outcome[plugin=" + plugin + ", state=" + state + ", refusal=" + refusal + "]";
    }
}
