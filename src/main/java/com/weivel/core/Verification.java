package com.weivel.core;

/**
 * What {@link CoreCalls#verifyOrQuarantine} answers: the activation passed, or the core refused it and the plugin was
 * quarantined in the same crossing.
 */
public final class Verification {
    private static final Verification PASSED = new Verification(true);
    private static final Verification QUARANTINED = new Verification(false);

    private final boolean verified;

    private Verification(boolean verified) {
        this.verified = verified;
    }

    static Verification passed() {
        return PASSED;
    }

    static Verification quarantined() {
        return QUARANTINED;
    }

    /**
     * Whether the core verified the activation.
     *
     * @return true when it passed, false when the core refused it and quarantined the plugin
     */
    public boolean verified() {
        return verified;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Verification && verified == ((Verification) other).verified;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(verified);
    }

    @Override
    public String toString() {
        return "Verification[verified=" + verified + "]";
    }
}
