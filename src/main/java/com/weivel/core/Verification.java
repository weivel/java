package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * What {@link CoreCalls#verifyOrQuarantine} answers: the activation passed, or the core refused it, with the refusal
 * it queued, and the plugin was quarantined in the same crossing.
 */
public final class Verification {
    private static final Verification PASSED = new Verification(true, Optional.<Refusal>empty());

    private final boolean verified;
    private final Optional<Refusal> refusal;

    private Verification(boolean verified, Optional<Refusal> refusal) {
        this.verified = verified;
        this.refusal = refusal;
    }

    static Verification passed() {
        return PASSED;
    }

    static Verification quarantined(Optional<Refusal> refusal) {
        return new Verification(false, Objects.requireNonNull(refusal, "refusal"));
    }

    /**
     * Whether the core verified the activation.
     *
     * @return true when it passed, false when the core refused it and quarantined the plugin
     */
    public boolean verified() {
        return verified;
    }

    /**
     * The core's refusal of the verification, the one that quarantined the plugin.
     *
     * @return the refusal as the core queued it; empty when the activation passed, or when the core queued none
     */
    public Optional<Refusal> refusal() {
        return refusal;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Verification)) {
            return false;
        }
        Verification that = (Verification) other;
        return verified == that.verified && refusal.equals(that.refusal);
    }

    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(verified) + refusal.hashCode();
    }

    @Override
    public String toString() {
        return "Verification[verified=" + verified + ", refusal=" + refusal + "]";
    }
}
