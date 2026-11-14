package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * One failure, as the error sink is told it.
 *
 * @param kind what failed
 * @param text one English line saying what failed
 * @param eventKind the event kind of the core's header of the event concerned, 0 when no event is
 * @param sequence that event's drain sequence number, 0 when no event is
 * @param cause what the failing code threw; empty when it answered a failure instead
 */
public record Failure(FailureKind kind, String text, int eventKind, long sequence, Optional<Throwable> cause) {
    /**
     * Checks that every part is present.
     *
     * @param kind what failed
     * @param text what failed, in English
     * @param eventKind the kind of the event concerned, or 0
     * @param sequence that event's sequence number, or 0
     * @param cause what was thrown, or empty
     */
    public Failure {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(cause, "cause");
    }
}
