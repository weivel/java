package com.weivel.core;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * The one table of native functions through which the layer reaches every function of one loaded core, with the
 * native seam its arguments are laid out through.
 */
final class CoreFunctions {
    private final NativeAccess access;
    private final NativeFunction[] functions;

    private CoreFunctions(NativeAccess access, NativeFunction[] functions) {
        this.access = access;
        this.functions = functions;
    }

    static Optional<CoreFunction> firstMissing(OpenLibrary binary) {
        for (CoreFunction function : CoreFunction.values()) {
            if (!binary.has(function.symbol())) {
                return Optional.of(function);
            }
        }
        return Optional.empty();
    }

    /** Links every function of a binary {@link #firstMissing} found complete. */
    static CoreFunctions link(NativeAccess access, OpenLibrary binary) {
        NativeFunction[] functions = new NativeFunction[CoreFunction.values().length];
        for (CoreFunction function : CoreFunction.values()) {
            functions[function.ordinal()] = binary.function(function.symbol(), function.signature());
        }
        return new CoreFunctions(access, functions);
    }

    /** A table whose every function {@code replacement} answers for: the seam a test counts or fails a call through. */
    CoreFunctions mapped(BiFunction<CoreFunction, NativeFunction, NativeFunction> replacement) {
        NativeFunction[] replaced = new NativeFunction[functions.length];
        for (CoreFunction function : CoreFunction.values()) {
            replaced[function.ordinal()] = replacement.apply(function, functions[function.ordinal()]);
        }
        return new CoreFunctions(access, replaced);
    }

    NativeFunction function(CoreFunction function) {
        return functions[function.ordinal()];
    }

    /**
     * Calls {@code function}, answering the status every function of the core answers. One overload per arity the
     * core's calls use, so no call allocates an argument array.
     */
    int status(CoreFunction function, long first) {
        return (int) functions[function.ordinal()].call(first);
    }

    int status(CoreFunction function, long first, long second) {
        return (int) functions[function.ordinal()].call(first, second);
    }

    int status(CoreFunction function, long first, long second, long third) {
        return (int) functions[function.ordinal()].call(first, second, third);
    }

    int status(CoreFunction function, long first, long second, long third, long fourth) {
        return (int) functions[function.ordinal()].call(first, second, third, fourth);
    }

    int status(CoreFunction function, long first, long second, long third, long fourth, long fifth) {
        return (int) functions[function.ordinal()].call(first, second, third, fourth, fifth);
    }

    int status(CoreFunction function, long first, long second, long third, long fourth, long fifth, long sixth) {
        return (int) functions[function.ordinal()].call(first, second, third, fourth, fifth, sixth);
    }

    NativeAccess access() {
        return access;
    }
}
