package com.weivel.core;

/** One native function, looked up in an {@link OpenLibrary} with its {@link NativeSignature}. */
@FunctionalInterface
interface NativeFunction {
    /**
     * Calls the function.
     *
     * @param arguments one per parameter, in order: a pointer as its address, an {@code int} narrowed from its long
     * @return what the function answered, an {@code int} widened with its sign, a {@code void} answered as 0
     * @throws IllegalStateException when the library it was looked up in is closed
     * @throws IllegalArgumentException when the arguments are not one per parameter
     */
    long call(long... arguments);
}
