package com.weivel.standard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the standard answered: its status, the value on {@link StandardStatus#OK} alone, its refusal when it filled
 * one, and for a manifest every field validation found wrong.
 *
 * @param <T> the value's type
 */
public final class StandardAnswer<T> {
    private final StandardStatus status;
    private final Optional<T> value;
    private final Optional<StandardRefusal> refusal;
    private final List<String> paths;

    /**
     * Makes the value, keeping {@code value} only for an OK status.
     *
     * @param status the status
     * @param value the value
     * @param refusal the refusal, or empty
     * @param paths every field validation found wrong, in its order; empty for an answer of no validation
     */
    public StandardAnswer(StandardStatus status, Optional<T> value, Optional<StandardRefusal> refusal,
                          List<String> paths) {
        this.status = Objects.requireNonNull(status, "status");
        this.value = status == StandardStatus.OK ? Objects.requireNonNull(value, "value") : Optional.<T>empty();
        this.refusal = Objects.requireNonNull(refusal, "refusal");
        this.paths = Collections.unmodifiableList(new ArrayList<>(paths));
    }

    static <T> StandardAnswer<T> of(StandardStatus status, Optional<T> value, Optional<StandardRefusal> refusal) {
        return new StandardAnswer<>(status, value, refusal, Collections.<String>emptyList());
    }

    /**
     * Answers {@code status}.
     *
     * @return the status
     */
    public StandardStatus status() {
        return status;
    }

    /**
     * Answers {@code value}.
     *
     * @return the value on OK, else empty
     */
    public Optional<T> value() {
        return value;
    }

    /**
     * Answers {@code refusal}.
     *
     * @return the refusal, or empty
     */
    public Optional<StandardRefusal> refusal() {
        return refusal;
    }

    /**
     * Answers {@code paths}.
     *
     * @return every field validation found wrong, unmodifiable
     */
    public List<String> paths() {
        return paths;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StandardAnswer)) {
            return false;
        }
        StandardAnswer<?> that = (StandardAnswer<?>) other;
        return status == that.status && value.equals(that.value) && refusal.equals(that.refusal)
                && paths.equals(that.paths);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, value, refusal, paths);
    }

    @Override
    public String toString() {
        return "StandardAnswer[status=" + status + ", value=" + value + ", refusal=" + refusal + ", paths=" + paths
                + "]";
    }
}
