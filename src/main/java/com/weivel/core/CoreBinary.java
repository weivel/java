package com.weivel.core;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** The core's shared binary, loaded from a path its caller gives, and the table of its functions. */
public final class CoreBinary implements AutoCloseable {
    private final Arena arena;
    private final CoreFunctions functions;

    private CoreBinary(Arena arena, CoreFunctions functions) {
        this.arena = arena;
        this.functions = functions;
    }

    /**
     * Opens the core's binary and resolves every function of the core in it.
     *
     * @param path the binary, a path the caller chose
     * @return the loaded binary, or why there is none; a failure is answered, never thrown
     * @implNote The arena is shared because any thread may call into the core, and closing it unloads the library.
     */
    public static CoreLoad load(Path path) {
        Objects.requireNonNull(path, "path");
        Arena arena = Arena.ofShared();
        SymbolLookup binary;
        try {
            binary = SymbolLookup.libraryLookup(path, arena);
        } catch (IllegalArgumentException notALibrary) {
            arena.close();
            return new CoreLoad.NotALibrary(path, String.valueOf(notALibrary.getMessage()));
        }
        Optional<CoreFunction> missing = CoreFunctions.firstMissing(binary);
        if (missing.isPresent()) {
            arena.close();
            return new CoreLoad.MissingFunction(path, missing.get().symbol());
        }
        return new CoreLoad.Loaded(new CoreBinary(arena, CoreFunctions.link(binary)));
    }

    CoreFunctions functions() {
        return functions;
    }

    /**
     * Closes the binary, after which no call through its functions may be made.
     *
     * @throws IllegalStateException when it was closed already, or a call into it is under way
     */
    @Override
    public void close() {
        arena.close();
    }
}
