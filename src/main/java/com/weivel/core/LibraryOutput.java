package com.weivel.core;

import java.util.Arrays;

/** What one call of a {@link LibraryFunction} answered: its status and its body, the library's copy already freed. */
public final class LibraryOutput {
    private final int status;
    private final byte[] body;

    LibraryOutput(int status, byte[] body) {
        this.status = status;
        this.body = body;
    }

    /**
     * Answers {@code status}.
     *
     * @return 0 for a result, any other value for a failure
     */
    public int status() {
        return status;
    }

    /**
     * Answers {@code body}.
     *
     * @return the body, the one copy made of the library's, which the caller now owns; empty when the library
     *     answered none
     */
    public byte[] body() {
        return body;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LibraryOutput)) {
            return false;
        }
        LibraryOutput that = (LibraryOutput) other;
        return status == that.status && Arrays.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return 31 * status + Arrays.hashCode(body);
    }

    @Override
    public String toString() {
        return "LibraryOutput[status=" + status + ", body=" + body.length + " bytes]";
    }
}
