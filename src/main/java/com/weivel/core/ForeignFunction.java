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

    /**
     * Calls a function of one parameter, handing the backend no argument array.
     *
     * @param first its argument
     * @return what it answered
     * @throws IllegalStateException when its library is closed
     * @throws IllegalArgumentException when it takes another number of parameters
     */
    public long call(long first) {
        return function.call(first);
    }

    /**
     * Calls a function of four parameters, handing the backend no argument array.
     *
     * @param first its first argument
     * @param second its second argument
     * @param third its third argument
     * @param fourth its fourth argument
     * @return what it answered
     * @throws IllegalStateException when its library is closed
     * @throws IllegalArgumentException when it takes another number of parameters
     */
    public long call(long first, long second, long third, long fourth) {
        return function.call(first, second, third, fourth);
    }

    /**
     * Calls a function of five parameters, handing the backend no argument array.
     *
     * @param first its first argument
     * @param second its second argument
     * @param third its third argument
     * @param fourth its fourth argument
     * @param fifth its fifth argument
     * @return what it answered
     * @throws IllegalStateException when its library is closed
     * @throws IllegalArgumentException when it takes another number of parameters
     */
    public long call(long first, long second, long third, long fourth, long fifth) {
        return function.call(first, second, third, fourth, fifth);
    }
}
