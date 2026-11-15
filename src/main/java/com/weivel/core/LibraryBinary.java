package com.weivel.core;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A C library's shared binary, loaded from a path its caller gives, with the release symbol that frees every body its
 * functions answer. It decides nothing of when it may close: its owner closes it once no call runs in it.
 */
public final class LibraryBinary implements AutoCloseable {
    private static final NativeSignature RELEASE = NativeSignature.of(NativeType.VOID, NativeType.ADDRESS);
    private static final NativeSignature BYTES = NativeSignature.of(NativeType.INT, NativeType.ADDRESS, NativeType.LONG,
            NativeType.ADDRESS, NativeType.ADDRESS);

    private final NativeAccess access;
    private final OpenLibrary library;
    private final NativeFunction release;
    private final SpareBlock spare;

    private LibraryBinary(NativeAccess access, OpenLibrary library, NativeFunction release) {
        this.access = access;
        this.library = library;
        this.release = release;
        this.spare = new SpareBlock(access);
    }

    /**
     * Opens a library and binds its release symbol, {@code void release(uint8_t *buffer)}.
     *
     * @param path the library, a path the caller chose
     * @param releaseSymbol the symbol that frees what the library's functions answer
     * @return the binary; {@link Answer.Mistake} for a path that names no library, or a library lacking the symbol,
     *     which is then closed again
     */
    public static Answer<LibraryBinary> open(Path path, String releaseSymbol) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(releaseSymbol, "releaseSymbol");
        NativeAccess access = NativeBackends.current();
        OpenLibrary library;
        try {
            library = access.open(path);
        } catch (IllegalArgumentException notALibrary) {
            return new Answer.Mistake<>(path + " is no library the platform could open: "
                    + notALibrary.getMessage());
        }
        if (!library.has(releaseSymbol)) {
            library.close();
            return new Answer.Mistake<>(path + " exports no release symbol " + releaseSymbol);
        }
        return new Answer.Ok<>(new LibraryBinary(access, library, library.function(releaseSymbol, RELEASE)));
    }

    /**
     * Binds one function of the standard's bytes kind.
     *
     * @param symbol the function's symbol
     * @return the function; {@link Answer.Mistake} naming the symbol when the library exports none of that name
     */
    public Answer<LibraryFunction> function(String symbol) {
        return function(symbol, LibraryCopies.NONE);
    }

    Answer<LibraryFunction> function(String symbol, LibraryCopies copies) {
        Objects.requireNonNull(symbol, "symbol");
        if (!library.has(symbol)) {
            return new Answer.Mistake<>("the library exports no function " + symbol);
        }
        return new Answer.Ok<>(new LibraryFunction(access, library.function(symbol, BYTES), release, spare, copies));
    }

    /**
     * Closes the binary, after which no function bound in it may be called.
     *
     * @throws IllegalStateException when the platform refuses, as for a call still under way or a second close
     */
    @Override
    public void close() {
        library.close();
        spare.close();
    }
}
