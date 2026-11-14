package com.weivel.core;

/**
 * One call into the core, as {@link CoreCalls} builds it and {@link Core#call} makes it through the one wrapper.
 *
 * @param <T> the call's result when the core answers it OK
 */
public final class CoreCall<T> {
    private final CoreCalls.Crossing<T> crossing;
    private final boolean carriesCallersParts;
    private final boolean handsTheTime;

    CoreCall(CoreCalls.Crossing<T> crossing, boolean carriesCallersParts, boolean handsTheTime) {
        this.crossing = crossing;
        this.carriesCallersParts = carriesCallersParts;
        this.handsTheTime = handsTheTime;
    }

    CoreCalls.Crossing<T> crossing() {
        return crossing;
    }

    /**
     * Whether the call hands the core a structure its caller built, so that a malformed answer is the caller's mistake
     * rather than a defect of the layer.
     */
    boolean carriesCallersParts() {
        return carriesCallersParts;
    }

    /** Whether the call itself hands the core a time, so that no due deadline is checked before it. */
    boolean handsTheTime() {
        return handsTheTime;
    }
}
