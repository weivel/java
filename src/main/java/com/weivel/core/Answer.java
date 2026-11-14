package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * What a call that reaches the core answers, as a value: the call's result, an absence, the core's refusal, or why
 * the call was never made.
 *
 * @param <T> the result of a call that succeeded
 */
public sealed interface Answer<T>
        permits Answer.Ok, Answer.Absent, Answer.Refused, Answer.Detached, Answer.Destroyed, Answer.Defect {

    /**
     * The core did what it was asked.
     *
     * @param value the call's result; {@code null} for a call that answers nothing beyond its success
     * @param <T> the result's type
     */
    record Ok<T>(T value) implements Answer<T> {
    }

    /**
     * The core had nothing to answer: a get found nothing registered, or no deadline is set. Not a refusal.
     *
     * @param <T> the result a success would have carried
     */
    record Absent<T>() implements Answer<T> {
    }

    /**
     * The core declined the call.
     *
     * @param status how the core declined it
     * @param refusal the refusal the core queued for this call, read from the events its drain held; empty where the
     *     core queued none, because the layer never invents one
     * @param <T> the result a success would have carried
     */
    record Refused<T>(RefusalStatus status, Optional<Refusal> refusal) implements Answer<T> {
        /**
         * Checks that both parts are present.
         *
         * @param status how the core declined it
         * @param refusal the refusal, or empty
         */
        public Refused {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(refusal, "refusal");
        }
    }

    /**
     * The call was made through a context whose plugin the core has forgotten, so it was answered without reaching the
     * core. Neither a success nor the core's refusal.
     *
     * @param <T> the result a success would have carried
     */
    record Detached<T>() implements Answer<T> {
    }

    /**
     * The core behind the layer was destroyed, so no call was made.
     *
     * @param <T> the result a success would have carried
     */
    record Destroyed<T>() implements Answer<T> {
    }

    /**
     * A defect of the layer: the core called a call the layer built whole malformed, or the layer failed in its own
     * code. It is also reported to the error sink.
     *
     * @param detail what went wrong, in English
     * @param <T> the result a success would have carried
     */
    record Defect<T>(String detail) implements Answer<T> {
        /**
         * Checks that the detail is present.
         *
         * @param detail what went wrong
         */
        public Defect {
            Objects.requireNonNull(detail, "detail");
        }
    }
}
