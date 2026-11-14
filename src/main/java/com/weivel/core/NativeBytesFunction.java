package com.weivel.core;

/**
 * A library function of the standard's bytes kind, {@code int32_t f(const uint8_t *input, size_t input_length,
 * uint8_t **output, size_t *output_length)}, looked up in an {@link OpenLibrary}. Both calls answer the function's
 * status and throw {@link IllegalStateException} when the library is closed.
 */
interface NativeBytesFunction {
    /** Calls the function with native memory at {@code input}, which C reads in place. */
    int call(long input, long inputLength, long output, long outputLength);

    /**
     * Calls the function with {@code length} bytes of a heap array from {@code offset}: lent in place where the
     * backend can lend the heap ({@link NativeAccess#lendsTheHeap()}), else copied once into native memory for the
     * call.
     */
    int call(byte[] input, int offset, int length, long output, long outputLength);
}
