package com.weivel.standard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A repository identity as the standard takes it apart, its {@code weivel_standard_identity} copied: a local one
 * holds a name, a git one the object id of the repository's root commit in lowercase hex; both hold the path segments
 * after the root.
 */
public final class Identity {
    /** Which of the two forms an identity takes, as {@code weivel_standard_identity_kind}. */
    public enum Kind {
        /** {@code local:<name>}. */
        LOCAL,
        /** {@code git:<40 or 64 lowercase hex digits>}. */
        GIT
    }

    private final Kind kind;
    private final Optional<String> name;
    private final Optional<String> commit;
    private final List<String> segments;

    /**
     * Makes the value.
     *
     * @param kind the form
     * @param name a local identity's name; empty for a git one
     * @param commit a git identity's root commit, its 40 or 64 lowercase hex digits; empty for a local one
     * @param segments the path segments after the root, in order
     */
    public Identity(Kind kind, Optional<String> name, Optional<String> commit, List<String> segments) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
        this.commit = Objects.requireNonNull(commit, "commit");
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    /**
     * Answers {@code kind}.
     *
     * @return the form
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Answers {@code name}.
     *
     * @return a local identity's name, else empty
     */
    public Optional<String> name() {
        return name;
    }

    /**
     * Answers {@code commit}.
     *
     * @return a git identity's root commit in lowercase hex, else empty
     */
    public Optional<String> commit() {
        return commit;
    }

    /**
     * Answers {@code segments}.
     *
     * @return the path segments, unmodifiable
     */
    public List<String> segments() {
        return segments;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Identity)) {
            return false;
        }
        Identity that = (Identity) other;
        return kind == that.kind && name.equals(that.name) && commit.equals(that.commit)
                && segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, commit, segments);
    }

    @Override
    public String toString() {
        return "Identity[kind=" + kind + ", name=" + name + ", commit=" + commit + ", segments=" + segments + "]";
    }
}
