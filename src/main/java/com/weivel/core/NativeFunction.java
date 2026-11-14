package com.weivel.core;

/**
 * One native function, looked up in an {@link OpenLibrary} with its {@link NativeSignature}. Each argument is a
 * {@code long}: a pointer as its address, an {@code int} narrowed from it. Each call answers a {@code long}: an
 * {@code int} widened with its sign, a {@code void} as 0. A call throws {@link IllegalStateException} when the
 * library the function was looked up in is closed, and {@link IllegalArgumentException} when it is not handed one
 * argument per parameter.
 *
 * @implNote The fixed-arity calls exist so a call allocates no argument array: a backend that can call without one
 * overrides them, and the defaults hand the arguments to {@link #call(long...)}.
 */
@FunctionalInterface
interface NativeFunction {
    long call(long... arguments);

    default long call() {
        return call(new long[0]);
    }

    default long call(long first) {
        return call(new long[] {first});
    }

    default long call(long first, long second) {
        return call(new long[] {first, second});
    }

    default long call(long first, long second, long third) {
        return call(new long[] {first, second, third});
    }

    default long call(long first, long second, long third, long fourth) {
        return call(new long[] {first, second, third, fourth});
    }

    default long call(long first, long second, long third, long fourth, long fifth) {
        return call(new long[] {first, second, third, fourth, fifth});
    }

    default long call(long first, long second, long third, long fourth, long fifth, long sixth) {
        return call(new long[] {first, second, third, fourth, fifth, sixth});
    }

    default long call(long first, long second, long third, long fourth, long fifth, long sixth, long seventh) {
        return call(new long[] {first, second, third, fourth, fifth, sixth, seventh});
    }
}
