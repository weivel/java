package com.weivel.core;

import java.util.Optional;

/**
 * One call into the core, as {@link CoreCalls} builds it and {@link Core#call} makes it through the one wrapper.
 *
 * @param <T> the call's result when the core answers it OK
 */
public final class CoreCall<T> {
    private final CoreCalls.Crossing<T> crossing;
    private final boolean carriesCallersParts;
    private final boolean handsTheTime;
    private final Optional<String> mistake;

    private CoreCall(CoreCalls.Crossing<T> crossing, boolean carriesCallersParts, boolean handsTheTime,
                     Optional<String> mistake) {
        this.crossing = crossing;
        this.carriesCallersParts = carriesCallersParts;
        this.handsTheTime = handsTheTime;
        this.mistake = mistake;
    }

    CoreCall(CoreCalls.Crossing<T> crossing, boolean carriesCallersParts, boolean handsTheTime) {
        this(crossing, carriesCallersParts, handsTheTime, Optional.empty());
    }

    /** A call its caller got wrong, answered {@link Answer.Mistake} without anything reaching the core. */
    static <T> CoreCall<T> mistaken(String detail) {
        return new CoreCall<>(null, true, false, Optional.of(detail));
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

    /** What the caller got wrong, found before the call; empty for a call that may be made. */
    Optional<String> mistake() {
        return mistake;
    }
}
