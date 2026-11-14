package com.weivel.core;

/**
 * One function of a {@link ForeignLibrary}, called as the signature it was bound with says: each argument a
 * {@code long}, a pointer as its address and an {@code int} narrowed from it; the answer a {@code long}, an
 * {@code int} widened with its sign and a {@code void} as 0.
 */
public final class ForeignFunction {
    private final NativeFunction function;

    ForeignFunction(NativeFunction function) {
        this.function = function;
    }

    /**
     * Calls the function.
     *
     * @param arguments one per parameter, in order
     * @return what it answered
     * @throws IllegalStateException when its library is closed
     * @throws IllegalArgumentException when not handed one argument per parameter
     */
    public long call(long... arguments) {
        return function.call(arguments);
    }
}
