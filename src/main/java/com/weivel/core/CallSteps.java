package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * What the layer's own packages run around one call, under the layer lock: a guard before the call is made, and a
 * step once the core has answered it OK. Both are the layer's code and never a caller's, so neither may call into the
 * core, which would take the lock again on the thread that holds it, nor report to the error sink; a failure of either
 * is a defect of the layer, reported to the error sink once the lock is let go and answered as a
 * {@link Answer.Defect}. Like {@link CoreInternals}, through which alone a call takes them, they are for the layer's
 * own packages.
 *
 * @param <T> the result of the call
 */
public final class CallSteps<T> {
    private static final CallSteps<Object> NONE = new CallSteps<>(() -> Optional.<Answer<Object>>empty(),
            Answer.Ok::new);

    private final CallGuard<T> guard;
    private final AnsweredStep<T> answered;

    /**
     * Checks that both steps are present.
     *
     * @param guard the guard
     * @param answered the answered step
     */
    public CallSteps(CallGuard<T> guard, AnsweredStep<T> answered) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(answered, "answered");
        this.guard = guard;
        this.answered = answered;
    }

    /**
     * Answers {@code guard}.
     *
     * @return checked under the lock once a due deadline was handed over and before the call is made: an answer it
     *     gives is the call's answer, the core not called and nothing drained
     */
    public CallGuard<T> guard() {
        return guard;
    }

    /**
     * Answers {@code answered}.
     *
     * @return run under the lock after the core answered OK and the call's events were drained, and before any of them
     *     is dispatched, on this thread or another, so an entry it makes for the handle the core answered is there when
     *     a listener hears of that handle; what it answers is the call's answer
     */
    public AnsweredStep<T> answered() {
        return answered;
    }

    /**
     * Steps that let every call through and answer what the core answered.
     *
     * @param <T> the result of the call
     * @return the steps
     */
    @SuppressWarnings("unchecked")
    public static <T> CallSteps<T> none() {
        return (CallSteps<T>) (CallSteps<?>) NONE;
    }

    /**
     * A check made under the layer lock before a call.
     *
     * @param <T> the result of the call
     */
    @FunctionalInterface
    public interface CallGuard<T> {
        /**
         * Decides whether the call is made.
         *
         * @return the answer that cancels the call, or empty to let it through
         */
        Optional<Answer<T>> check();
    }

    /**
     * A step run under the layer lock once the core has answered a call OK.
     *
     * @param <T> the result of the call
     */
    @FunctionalInterface
    public interface AnsweredStep<T> {
        /**
         * Records what the core answered.
         *
         * @param value the call's result
         * @return the call's answer, ordinarily the result as an {@link Answer.Ok}
         */
        Answer<T> record(T value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CallSteps)) {
            return false;
        }
        CallSteps<?> that = (CallSteps<?>) other;
        return Objects.equals(guard, that.guard)
                && Objects.equals(answered, that.answered);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(guard);
        hash = 31 * hash + Objects.hashCode(answered);
        return hash;
    }

    @Override
    public String toString() {
        return "CallSteps[guard=" + guard + ", answered=" + answered + "]";
    }
}
