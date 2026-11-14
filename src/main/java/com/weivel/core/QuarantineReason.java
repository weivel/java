package com.weivel.core;

import java.util.Objects;

/**
 * Why a plugin is quarantined, as its caller knows it, mirroring {@code weivel_quarantine_reason}.
 *
 * @param code a refusal code of the core's header, or {@link CoreConstants#REFUSAL_NONE} when none fits
 * @param detail what happened, in English
 * @param fix what would make it succeed, in English
 */
public record QuarantineReason(int code, String detail, String fix) {
    /**
     * Checks that both texts are present.
     *
     * @param code the refusal code
     * @param detail what happened
     * @param fix what would make it succeed
     */
    public QuarantineReason {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(fix, "fix");
    }
}
