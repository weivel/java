package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * A refusal of the core's, copied out of the event that carried it.
 */
public final class Refusal {
    private final int code;
    private final String detail;
    private final String fix;
    private final Optional<String> subject;
    private final boolean hasFix;

    /**
     * Checks that every text is present.
     *
     * @param code the refusal code
     * @param detail what happened
     * @param fix what would make it succeed
     * @param subject what the refusal is about, or empty
     * @param hasFix whether the fix is non-empty
     */
    public Refusal(int code, String detail, String fix, Optional<String> subject, boolean hasFix) {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(fix, "fix");
        Objects.requireNonNull(subject, "subject");
        this.code = code;
        this.detail = detail;
        this.fix = fix;
        this.subject = subject;
        this.hasFix = hasFix;
    }

    /**
     * Answers {@code code}.
     *
     * @return a refusal code of the core's header, as {@link CoreConstants} names them
     */
    public int code() {
        return code;
    }

    /**
     * Answers {@code detail}.
     *
     * @return what happened, in English; the layer's fixed sentence when the event could not be copied whole
     */
    public String detail() {
        return detail;
    }

    /**
     * Answers {@code fix}.
     *
     * @return what would make it succeed, in English; the layer's fixed sentence likewise
     */
    public String fix() {
        return fix;
    }

    /**
     * Answers {@code subject}.
     *
     * @return what the refusal is about, as its code says; empty when the code names nothing
     */
    public Optional<String> subject() {
        return subject;
    }

    /**
     * Answers {@code hasFix}.
     *
     * @return whether the core's {@code fix} is non-empty, exact even when the texts are not
     */
    public boolean hasFix() {
        return hasFix;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Refusal)) {
            return false;
        }
        Refusal that = (Refusal) other;
        return code == that.code
                && Objects.equals(detail, that.detail)
                && Objects.equals(fix, that.fix)
                && Objects.equals(subject, that.subject)
                && hasFix == that.hasFix;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(code);
        hash = 31 * hash + Objects.hashCode(detail);
        hash = 31 * hash + Objects.hashCode(fix);
        hash = 31 * hash + Objects.hashCode(subject);
        hash = 31 * hash + Boolean.hashCode(hasFix);
        return hash;
    }

    @Override
    public String toString() {
        return "Refusal[code=" + code + ", detail=" + detail + ", fix=" + fix + ", subject=" + subject + ", hasFix="
                + hasFix + "]";
    }
}
