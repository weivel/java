package com.weivel.core;

import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Optional;

/** The one table of downcall handles through which the layer reaches every function of one loaded core. */
final class CoreFunctions {
    private final MethodHandle[] handles;

    private CoreFunctions(MethodHandle[] handles) {
        this.handles = handles;
    }

    static Optional<CoreFunction> firstMissing(SymbolLookup binary) {
        return Arrays.stream(CoreFunction.values())
                .filter(function -> binary.find(function.symbol()).isEmpty())
                .findFirst();
    }

    /** Links every function of a binary {@link #firstMissing} found complete. */
    static CoreFunctions link(SymbolLookup binary) {
        Linker linker = Linker.nativeLinker();
        MethodHandle[] handles = new MethodHandle[CoreFunction.values().length];
        for (CoreFunction function : CoreFunction.values()) {
            handles[function.ordinal()] = linker.downcallHandle(binary.findOrThrow(function.symbol()),
                    function.descriptor());
        }
        return new CoreFunctions(handles);
    }

    MethodHandle handle(CoreFunction function) {
        return handles[function.ordinal()];
    }
}
