package com.weivel.core;

/**
 * Points in a {@link LibraryFunction} call where a test of this package watches the copies a call makes, or makes the
 * copy of a body fail, as no real copy can be made to.
 */
interface LibraryCopies {
    LibraryCopies NONE = new LibraryCopies() {
    };

    /** A heap input was copied to {@code address}, which is what the library is then handed. */
    default void inputCopied(long address, int length) {
    }

    /** The library answered a body, which is about to be copied. */
    default void beforeTheBodyIsCopied() {
    }
}
