package com.weivel.core;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** The core's shared binary, loaded from a path its caller gives, and the table of its functions. */
public final class CoreBinary implements AutoCloseable {
    private final OpenLibrary library;
    private final CoreFunctions functions;

    private CoreBinary(OpenLibrary library, CoreFunctions functions) {
        this.library = library;
        this.functions = functions;
    }

    /**
     * Opens the core's binary and resolves every function of the core in it.
     *
     * @param path the binary, a path the caller chose
     * @return the loaded binary, or why there is none; a failure is answered, never thrown
     */
    public static CoreLoad load(Path path) {
        Objects.requireNonNull(path, "path");
        NativeAccess access = NativeBackends.current();
        OpenLibrary library;
        try {
            library = access.open(path);
        } catch (IllegalArgumentException notALibrary) {
            return new CoreLoad.NotALibrary(path, String.valueOf(notALibrary.getMessage()));
        }
        Optional<CoreFunction> missing = CoreFunctions.firstMissing(library);
        if (missing.isPresent()) {
            library.close();
            return new CoreLoad.MissingFunction(path, missing.get().symbol());
        }
        return new CoreLoad.Loaded(new CoreBinary(library, CoreFunctions.link(access, library)));
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
        library.close();
    }
}
