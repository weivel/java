package com.weivel.standard;

/** How the standard answered a call, as its {@code weivel_status}. */
public enum StandardStatus {
    /** It did what it was asked ({@code WEIVEL_STATUS_OK}). */
    OK(0),
    /** The call was malformed ({@code WEIVEL_STATUS_MALFORMED}), its refusal saying why when there is one. */
    MALFORMED(2),
    /** It declined ({@code WEIVEL_STATUS_REFUSED}), its refusal saying why. */
    REFUSED(3),
    /** Memory ran out and nothing was answered ({@code WEIVEL_STATUS_OUT_OF_MEMORY}). */
    OUT_OF_MEMORY(4);

    private final int value;

    StandardStatus(int value) {
        this.value = value;
    }

    /** @throws IllegalStateException for a status no function of the standard this package calls answers */
    static StandardStatus of(long value) {
        for (StandardStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalStateException("the standard answered the status " + value + ", which none of its "
                + "functions this package calls answers");
    }
}
