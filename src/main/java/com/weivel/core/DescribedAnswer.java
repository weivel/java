package com.weivel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What one call of a described operation answered: its answers on OK, NONE, a refusal with the provider's own texts,
 * any other status, or that the library's binary was closed before the call, so nothing was called. Each case is a
 * final nested class. Every answer was copied into Java values and released before the call answered.
 */
public abstract class DescribedAnswer {
    private DescribedAnswer() {
    }

    /** {@code WEIVEL_STATUS_OK}: the operation's answers. */
    public static final class Answered extends DescribedAnswer {
        private final Map<String, Object> answers;
        private final String only;

        Answered(Map<String, Object> answers, String only) {
            this.answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
            this.only = only;
        }

        /**
         * Answers {@code answers}.
         *
         * @return each answer parameter's value by its name, as {@link DescribedKind} says each kind arrives; a value
         *     tree of kind NONE and a NULL handle are left out
         */
        public Map<String, Object> answers() {
            return answers;
        }

        /**
         * The value of an operation's one answer, as most operations have.
         *
         * @return it; null when the operation has no answer or several, or its one answer was left out, which
         *     {@link #hasValue()} tells apart from a text or value answered as null
         */
        public Object value() {
            return only == null ? null : answers.get(only);
        }

        /**
         * Whether the operation has one answer and it was not left out.
         *
         * @return whether {@link #value()} carries it
         */
        public boolean hasValue() {
            return only != null && answers.containsKey(only);
        }

        @Override
        public String toString() {
            return "Answered" + answers;
        }
    }

    /** {@code WEIVEL_STATUS_NONE}: the operation has nothing to answer, which is neither an answer nor a refusal. */
    public static final class None extends DescribedAnswer {
        None() {
        }

        @Override
        public String toString() {
            return "None";
        }
    }

    /** {@code WEIVEL_STATUS_REFUSED}: the provider refused, saying why and what fixes it. */
    public static final class Refused extends DescribedAnswer {
        private final Optional<String> detail;
        private final Optional<String> fix;

        Refused(Optional<String> detail, Optional<String> fix) {
            this.detail = detail;
            this.fix = fix;
        }

        /**
         * Answers {@code detail}.
         *
         * @return the provider's text saying why; empty when it gave none
         */
        public Optional<String> detail() {
            return detail;
        }

        /**
         * Answers {@code fix}.
         *
         * @return the provider's text saying what fixes it; empty when it gave none
         */
        public Optional<String> fix() {
            return fix;
        }

        @Override
        public String toString() {
            return "Refused[detail=" + detail + ", fix=" + fix + "]";
        }
    }

    /** Any other status, as {@code WEIVEL_STATUS_MALFORMED}, or REFUSED from an operation that takes no refusal. */
    public static final class Failed extends DescribedAnswer {
        private final int status;

        Failed(int status) {
            this.status = status;
        }

        /**
         * Answers {@code status}.
         *
         * @return the {@code weivel_status} the operation returned, one of {@link CoreConstants}' {@code STATUS_}s or
         *     one the core does not publish
         */
        public int status() {
            return status;
        }

        @Override
        public String toString() {
            return "Failed[status=" + status + "]";
        }
    }

    /** The library's binary was closed before the call: nothing was called. */
    public static final class Unloaded extends DescribedAnswer {
        Unloaded() {
        }

        @Override
        public String toString() {
            return "Unloaded";
        }
    }
}
