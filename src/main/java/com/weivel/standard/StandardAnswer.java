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
    private final Optional<String> mistake;

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
        this(status, value, refusal, paths, Optional.<String>empty());
    }

    private StandardAnswer(StandardStatus status, Optional<T> value, Optional<StandardRefusal> refusal,
                           List<String> paths, Optional<String> mistake) {
        this.status = Objects.requireNonNull(status, "status");
        this.value = status == StandardStatus.OK ? Objects.requireNonNull(value, "value") : Optional.<T>empty();
        this.refusal = Objects.requireNonNull(refusal, "refusal");
        this.paths = Collections.unmodifiableList(new ArrayList<>(paths));
        this.mistake = Objects.requireNonNull(mistake, "mistake");
    }

    static <T> StandardAnswer<T> of(StandardStatus status, Optional<T> value, Optional<StandardRefusal> refusal) {
        return new StandardAnswer<>(status, value, refusal, Collections.<String>emptyList());
    }

    /** The standard answered {@code status}, and what it answered is no value of this type: the caller's mistake. */
    static <T> StandardAnswer<T> mistaken(StandardStatus status, String detail) {
        return new StandardAnswer<>(status, Optional.<T>empty(), Optional.<StandardRefusal>empty(),
                Collections.<String>emptyList(), Optional.of(detail));
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

    /**
     * Answers {@code mistake}.
     *
     * @return why what the standard answered is no value of this type, the caller's document being at fault, with
     *     the standard's own status kept; empty when it is one
     */
    public Optional<String> mistake() {
        return mistake;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StandardAnswer)) {
            return false;
        }
        StandardAnswer<?> that = (StandardAnswer<?>) other;
        return status == that.status && value.equals(that.value) && refusal.equals(that.refusal)
                && paths.equals(that.paths) && mistake.equals(that.mistake);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, value, refusal, paths, mistake);
    }

    @Override
    public String toString() {
        return "StandardAnswer[status=" + status + ", value=" + value + ", refusal=" + refusal + ", paths=" + paths
                + ", mistake=" + mistake + "]";
    }
}
