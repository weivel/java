package com.weivel.core;

/**
 * One implementation of a capability, as {@code weivel_all_capabilities} answers it.
 */
public final class CapabilityImplementation {
    private final long implementation;
    private final long provider;

    /**
     * Makes the value.
     *
     * @param implementation the handle its provider was answered
     * @param provider the providing plugin
     */
    public CapabilityImplementation(long implementation, long provider) {
        this.implementation = implementation;
        this.provider = provider;
    }

    /**
     * Answers {@code implementation}.
     *
     * @return the handle its provider was answered
     */
    public long implementation() {
        return implementation;
    }

    /**
     * Answers {@code provider}.
     *
     * @return the providing plugin
     */
    public long provider() {
        return provider;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapabilityImplementation)) {
            return false;
        }
        CapabilityImplementation that = (CapabilityImplementation) other;
        return implementation == that.implementation
                && provider == that.provider;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(implementation);
        hash = 31 * hash + Long.hashCode(provider);
        return hash;
    }

    @Override
    public String toString() {
        return "CapabilityImplementation[implementation=" + implementation + ", provider=" + provider + "]";
    }
}
