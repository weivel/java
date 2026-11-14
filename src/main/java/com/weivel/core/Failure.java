package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * One failure, as the error sink is told it.
 */
public final class Failure {
    private final FailureKind kind;
    private final String text;
    private final int eventKind;
    private final long sequence;
    private final Optional<Throwable> cause;

    /**
     * Checks that every part is present.
     *
     * @param kind what failed
     * @param text what failed, in English
     * @param eventKind the kind of the event concerned, or 0
     * @param sequence that event's sequence number, or 0
     * @param cause what was thrown, or empty
     */
    public Failure(FailureKind kind, String text, int eventKind, long sequence, Optional<Throwable> cause) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(cause, "cause");
        this.kind = kind;
        this.text = text;
        this.eventKind = eventKind;
        this.sequence = sequence;
        this.cause = cause;
    }

    /**
     * Answers {@code kind}.
     *
     * @return what failed
     */
    public FailureKind kind() {
        return kind;
    }

    /**
     * Answers {@code text}.
     *
     * @return one English line saying what failed
     */
    public String text() {
        return text;
    }

    /**
     * Answers {@code eventKind}.
     *
     * @return the event kind of the core's header of the event concerned, 0 when no event is
     */
    public int eventKind() {
        return eventKind;
    }

    /**
     * Answers {@code sequence}.
     *
     * @return that event's drain sequence number, 0 when no event is
     */
    public long sequence() {
        return sequence;
    }

    /**
     * Answers {@code cause}.
     *
     * @return what the failing code threw; empty when it answered a failure instead
     */
    public Optional<Throwable> cause() {
        return cause;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Failure)) {
            return false;
        }
        Failure that = (Failure) other;
        return Objects.equals(kind, that.kind)
                && Objects.equals(text, that.text)
                && eventKind == that.eventKind
                && sequence == that.sequence
                && Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(kind);
        hash = 31 * hash + Objects.hashCode(text);
        hash = 31 * hash + Integer.hashCode(eventKind);
        hash = 31 * hash + Long.hashCode(sequence);
        hash = 31 * hash + Objects.hashCode(cause);
        return hash;
    }

    @Override
    public String toString() {
        return "Failure[kind=" + kind + ", text=" + text + ", eventKind=" + eventKind + ", sequence=" + sequence
                + ", cause=" + cause + "]";
    }
}
