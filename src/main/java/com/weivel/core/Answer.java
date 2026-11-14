package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * What a call that reaches the core answers, as a value: the call's result, an absence, the core's refusal, or why
 * the call was never made.
 *
 * @param <T> the result of a call that succeeded
 */
public abstract class Answer<T> {
    private Answer() {
    }

    /**
     * Reads an answer that carries no value as an answer of another result type.
     *
     * @param answer any answer but a success, which carries its own value
     * @param <U> the result type to read it as
     * @return the same answer, of the other type
     * @throws IllegalArgumentException for a success
     */
    public static <U> Answer<U> retyped(Answer<?> answer) {
        if (answer instanceof Ok) {
            throw new IllegalArgumentException("a success carries its own value");
        }
        if (answer instanceof Absent) {
            return new Absent<>();
        }
        if (answer instanceof Refused) {
            Refused<?> refused = (Refused<?>) answer;
            return new Refused<>(refused.status(), refused.refusal());
        }
        if (answer instanceof Detached) {
            return new Detached<>();
        }
        if (answer instanceof Destroyed) {
            return new Destroyed<>();
        }
        if (answer instanceof Defect) {
            return new Defect<>(((Defect<?>) answer).detail());
        }
        return new Mistake<>(((Mistake<?>) answer).detail());
    }

    /**
     * The core did what it was asked.
     *
     * @param <T> the result's type
     */
    public static final class Ok<T> extends Answer<T> {
        private final T value;

        /**
         * Makes the value.
         *
         * @param value the call's result; {@code null} for a call that answers nothing beyond its success
         */
        public Ok(T value) {
            this.value = value;
        }

        /**
         * Answers {@code value}.
         *
         * @return the call's result; {@code null} for a call that answers nothing beyond its success
         */
        public T value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Ok)) {
                return false;
            }
            Ok<?> that = (Ok<?>) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(value);
            return hash;
        }

        @Override
        public String toString() {
            return "Ok[value=" + value + "]";
        }
    }

    /**
     * The core had nothing to answer: a get found nothing registered, or no deadline is set. Not a refusal.
     *
     * @param <T> the result a success would have carried
     */
    public static final class Absent<T> extends Answer<T> {
        /**
         * Makes the value.
         */
        public Absent() {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Absent;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "Absent[]";
        }
    }

    /**
     * The core declined the call.
     *
     * @param <T> the result a success would have carried
     */
    public static final class Refused<T> extends Answer<T> {
        private final RefusalStatus status;
        private final Optional<Refusal> refusal;

        /**
         * Checks that both parts are present.
         *
         * @param status how the core declined it
         * @param refusal the refusal, or empty
         */
        public Refused(RefusalStatus status, Optional<Refusal> refusal) {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(refusal, "refusal");
            this.status = status;
            this.refusal = refusal;
        }

        /**
         * Answers {@code status}.
         *
         * @return how the core declined it
         */
        public RefusalStatus status() {
            return status;
        }

        /**
         * Answers {@code refusal}.
         *
         * @return the refusal the core queued for this call, read from the events its drain held; empty where the core
         *     queued none, because the layer never invents one
         */
        public Optional<Refusal> refusal() {
            return refusal;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Refused)) {
                return false;
            }
            Refused<?> that = (Refused<?>) other;
            return Objects.equals(status, that.status)
                    && Objects.equals(refusal, that.refusal);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(status);
            hash = 31 * hash + Objects.hashCode(refusal);
            return hash;
        }

        @Override
        public String toString() {
            return "Refused[status=" + status + ", refusal=" + refusal + "]";
        }
    }

    /**
     * The call was made through a context whose plugin the core has forgotten, so it was answered without reaching the
     * core. Neither a success nor the core's refusal.
     *
     * @param <T> the result a success would have carried
     */
    public static final class Detached<T> extends Answer<T> {
        /**
         * Makes the value.
         */
        public Detached() {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Detached;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "Detached[]";
        }
    }

    /**
     * The core behind the layer was destroyed, so no call was made.
     *
     * @param <T> the result a success would have carried
     */
    public static final class Destroyed<T> extends Answer<T> {
        /**
         * Makes the value.
         */
        public Destroyed() {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Destroyed;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "Destroyed[]";
        }
    }

    /**
     * A defect of the layer: the core called a call the layer built whole malformed, or the layer failed in its own
     * code. It is also reported to the error sink.
     *
     * @param <T> the result a success would have carried
     */
    public static final class Defect<T> extends Answer<T> {
        private final String detail;

        /**
         * Checks that the detail is present.
         *
         * @param detail what went wrong
         */
        public Defect(String detail) {
            Objects.requireNonNull(detail, "detail");
            this.detail = detail;
        }

        /**
         * Answers {@code detail}.
         *
         * @return what went wrong, in English
         */
        public String detail() {
            return detail;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Defect)) {
                return false;
            }
            Defect<?> that = (Defect<?>) other;
            return Objects.equals(detail, that.detail);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(detail);
            return hash;
        }

        @Override
        public String toString() {
            return "Defect[detail=" + detail + "]";
        }
    }

    /**
     * The caller's mistake, found by the layer before anything reached the core: the call was not made, and no sink
     * was told, since it is the caller's to correct.
     *
     * @param <T> the result a success would have carried
     */
    public static final class Mistake<T> extends Answer<T> {
        private final String detail;

        /**
         * Checks that the detail is present.
         *
         * @param detail what was wrong
         */
        public Mistake(String detail) {
            Objects.requireNonNull(detail, "detail");
            this.detail = detail;
        }

        /**
         * Answers {@code detail}.
         *
         * @return what was wrong with the call, in English
         */
        public String detail() {
            return detail;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Mistake)) {
                return false;
            }
            Mistake<?> that = (Mistake<?>) other;
            return Objects.equals(detail, that.detail);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(detail);
            return hash;
        }

        @Override
        public String toString() {
            return "Mistake[detail=" + detail + "]";
        }
    }
}
