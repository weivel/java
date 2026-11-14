package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * A refusal of the core's, copied out of the event that carried it.
 *
 * @param code a refusal code of the core's header, as {@link CoreConstants} names them
 * @param detail what happened, in English; the layer's fixed sentence when the event could not be copied whole
 * @param fix what would make it succeed, in English; the layer's fixed sentence likewise
 * @param subject what the refusal is about, as its code says; empty when the code names nothing
 * @param hasFix whether the core's {@code fix} is non-empty, exact even when the texts are not
 */
public record Refusal(int code, String detail, String fix, Optional<String> subject, boolean hasFix) {
    /**
     * Checks that every text is present.
     *
     * @param code the refusal code
     * @param detail what happened
     * @param fix what would make it succeed
     * @param subject what the refusal is about, or empty
     * @param hasFix whether the fix is non-empty
     */
    public Refusal {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(fix, "fix");
        Objects.requireNonNull(subject, "subject");
    }
}
