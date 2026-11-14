package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * What the layer's own packages run around one call, under the layer lock: a guard before the call is made, and a
 * step once the core has answered it OK. Both are the layer's code and never a caller's, so neither may call into the
 * core, which would take the lock again on the thread that holds it; a failure of either is a defect of the layer,
 * reported to the error sink and answered as a {@link Answer.Defect}.
 *
 * @param guard checked under the lock once a due deadline was handed over and before the call is made: an answer it
 *     gives is the call's answer, the core not called and nothing drained
 * @param answered run under the lock after the core answered OK and the call's events were drained, and before any of
 *     them is dispatched, on this thread or another, so an entry it makes for the handle the core answered is there
 *     when a listener hears of that handle; what it answers is the call's answer
 * @param <T> the result of the call
 */
public record CallSteps<T>(CallGuard<T> guard, AnsweredStep<T> answered) {
    /**
     * Checks that both steps are present.
     *
     * @param guard the guard
     * @param answered the answered step
     */
    public CallSteps {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(answered, "answered");
    }

    /**
     * Steps that let every call through and answer what the core answered.
     *
     * @param <T> the result of the call
     * @return the steps
     */
    public static <T> CallSteps<T> none() {
        return new CallSteps<>(Optional::empty, Answer.Ok::new);
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
}
