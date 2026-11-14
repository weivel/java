package com.weivel.standard;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A repository identity as the standard takes it apart, its {@code weivel_standard_identity} copied: a local one
 * holds a name, a forge one a forge word, an optional host and the repository number; both hold the path segments
 * after the root.
 */
public final class Identity {
    /** Which of the two forms an identity takes, as {@code weivel_standard_identity_kind}. */
    public enum Kind {
        /** {@code local:<name>}. */
        LOCAL,
        /** {@code <forge>[@host]:<number>}. */
        FORGE
    }

    private final Kind kind;
    private final Optional<String> name;
    private final Optional<String> forge;
    private final Optional<String> host;
    private final BigInteger number;
    private final List<String> segments;

    /**
     * Makes the value.
     *
     * @param kind the form
     * @param name a local identity's name, which may hold a NUL character; empty for a forge one
     * @param forge a forge identity's forge word; empty for a local one
     * @param host the host a forge identity names, or empty
     * @param number a forge identity's repository number, up to 64 bits unsigned; 0 for a local one
     * @param segments the path segments after the root, in order
     */
    public Identity(Kind kind, Optional<String> name, Optional<String> forge, Optional<String> host,
                    BigInteger number, List<String> segments) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
        this.forge = Objects.requireNonNull(forge, "forge");
        this.host = Objects.requireNonNull(host, "host");
        this.number = Objects.requireNonNull(number, "number");
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
     * Answers {@code forge}.
     *
     * @return a forge identity's forge word, else empty
     */
    public Optional<String> forge() {
        return forge;
    }

    /**
     * Answers {@code host}.
     *
     * @return the host a forge identity names, else empty
     */
    public Optional<String> host() {
        return host;
    }

    /**
     * Answers {@code number}.
     *
     * @return a forge identity's repository number, 0 for a local one
     */
    public BigInteger number() {
        return number;
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
        return kind == that.kind && name.equals(that.name) && forge.equals(that.forge) && host.equals(that.host)
                && number.equals(that.number) && segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, forge, host, number, segments);
    }

    @Override
    public String toString() {
        return "Identity[kind=" + kind + ", name=" + name + ", forge=" + forge + ", host=" + host + ", number="
                + number + ", segments=" + segments + "]";
    }
}
