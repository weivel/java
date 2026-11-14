package com.weivel.core;

import java.util.Optional;

/**
 * What {@link CoreCalls#verifyOrQuarantine} answers: the activation passed, or the core refused it and the plugin was
 * quarantined in the same crossing, with the status the quarantine answered.
 */
public final class Verification {
    private static final Verification VERIFIED = new Verification(Optional.<Integer>empty());

    private final Optional<Integer> quarantineStatus;

    private Verification(Optional<Integer> quarantineStatus) {
        this.quarantineStatus = quarantineStatus;
    }

    static Verification passed() {
        return VERIFIED;
    }

    static Verification quarantined(int status) {
        return new Verification(Optional.of(status));
    }

    /**
     * Whether the core verified the activation.
     *
     * @return true when it passed, false when the core refused it and the plugin was quarantined
     */
    public boolean verified() {
        return !quarantineStatus.isPresent();
    }

    /**
     * Whether the quarantine of a refused activation took.
     *
     * @return true for a refused activation whose quarantine the core answered OK
     */
    public boolean quarantinedInTheCore() {
        return quarantineStatus.isPresent() && quarantineStatus.get() == 0;
    }

    /**
     * Answers {@code quarantineStatus}.
     *
     * @return the core's status for the quarantine; empty when the activation passed and nothing was quarantined
     */
    public Optional<Integer> quarantineStatus() {
        return quarantineStatus;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Verification && quarantineStatus.equals(((Verification) other).quarantineStatus);
    }

    @Override
    public int hashCode() {
        return quarantineStatus.hashCode();
    }

    @Override
    public String toString() {
        return "Verification[quarantineStatus=" + quarantineStatus + "]";
    }
}
