package com.weivel.core;

import java.util.Objects;

/**
 * Why a plugin is quarantined, as its caller knows it, mirroring {@code weivel_quarantine_reason}.
 */
public final class QuarantineReason {
    private final int code;
    private final String detail;
    private final String fix;

    /**
     * Checks that both texts are present.
     *
     * @param code the refusal code
     * @param detail what happened
     * @param fix what would make it succeed
     */
    public QuarantineReason(int code, String detail, String fix) {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(fix, "fix");
        this.code = code;
        this.detail = detail;
        this.fix = fix;
    }

    /**
     * Answers {@code code}.
     *
     * @return a refusal code of the core's header, or {@link CoreConstants#REFUSAL_NONE} when none fits
     */
    public int code() {
        return code;
    }

    /**
     * Answers {@code detail}.
     *
     * @return what happened, in English
     */
    public String detail() {
        return detail;
    }

    /**
     * Answers {@code fix}.
     *
     * @return what would make it succeed, in English
     */
    public String fix() {
        return fix;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuarantineReason)) {
            return false;
        }
        QuarantineReason that = (QuarantineReason) other;
        return code == that.code
                && Objects.equals(detail, that.detail)
                && Objects.equals(fix, that.fix);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(code);
        hash = 31 * hash + Objects.hashCode(detail);
        hash = 31 * hash + Objects.hashCode(fix);
        return hash;
    }

    @Override
    public String toString() {
        return "QuarantineReason[code=" + code + ", detail=" + detail + ", fix=" + fix + "]";
    }
}
