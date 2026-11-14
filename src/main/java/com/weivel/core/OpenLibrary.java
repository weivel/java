package com.weivel.core;

/** A shared library a {@link NativeAccess} opened, held open until it is closed. */
interface OpenLibrary extends AutoCloseable {
    /** Whether the library exports {@code symbol}. */
    boolean has(String symbol);

    /**
     * The function the library exports as {@code symbol}, called as {@code signature} says.
     *
     * @throws IllegalArgumentException naming the symbol, when the library exports none of that name
     */
    NativeFunction function(String symbol, NativeSignature signature);

    /**
     * The function the library exports as {@code symbol}, of the standard's bytes kind.
     *
     * @throws IllegalArgumentException naming the symbol, when the library exports none of that name
     */
    NativeBytesFunction bytesFunction(String symbol);

    /**
     * Closes the library, after which no function looked up in it may be called.
     *
     * @throws IllegalStateException when it was closed already, or a call into it is under way
     */
    @Override
    void close();
}
