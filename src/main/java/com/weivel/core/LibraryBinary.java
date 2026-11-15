package com.weivel.core;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A C library's shared binary, loaded from a path its caller gives, with the release symbol that frees every body its
 * bytes functions answer, when it declares one. It decides nothing of when it may close: its owner closes it once
 * nothing of it can run.
 */
public final class LibraryBinary implements AutoCloseable {
    private static final NativeSignature RELEASE = NativeSignature.of(NativeType.VOID, NativeType.ADDRESS);
    private static final NativeSignature BYTES = NativeSignature.of(NativeType.INT, NativeType.ADDRESS, NativeType.LONG,
            NativeType.ADDRESS, NativeType.ADDRESS);
    private static final NativeSignature DEFINITION = NativeSignature.of(NativeType.ADDRESS);

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
     * Opens a library and binds its release symbol, {@code void release(uint8_t *buffer)}, when it declares one.
     *
     * @param path the library, a path the caller chose
     * @param releaseSymbol the symbol that frees what the library's bytes functions answer; empty for a library of
     *     definitions alone
     * @return the binary; {@link Answer.Mistake} for a path that names no library, or a library lacking the symbol,
     *     which is then closed again
     */
    public static Answer<LibraryBinary> open(Path path, Optional<String> releaseSymbol) {
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
        if (!releaseSymbol.isPresent()) {
            return new Answer.Ok<>(new LibraryBinary(access, library, null));
        }
        if (!library.has(releaseSymbol.get())) {
            library.close();
            return new Answer.Mistake<>(path + " exports no release symbol " + releaseSymbol.get());
        }
        return new Answer.Ok<>(new LibraryBinary(access, library, library.function(releaseSymbol.get(), RELEASE)));
    }

    /**
     * Binds one function of the standard's bytes kind.
     *
     * @param symbol the function's symbol
     * @return the function; {@link Answer.Mistake} naming the symbol when the library exports none of that name, or
     *     when the binary was opened with no release symbol
     */
    public Answer<LibraryFunction> function(String symbol) {
        return function(symbol, LibraryCopies.NONE);
    }

    Answer<LibraryFunction> function(String symbol, LibraryCopies copies) {
        Objects.requireNonNull(symbol, "symbol");
        if (release == null) {
            return new Answer.Mistake<>("the library declares no release symbol, which its bytes function " + symbol
                    + " needs");
        }
        if (!library.has(symbol)) {
            return new Answer.Mistake<>("the library exports no function " + symbol);
        }
        return new Answer.Ok<>(new LibraryFunction(access, library.function(symbol, BYTES), release, spare, copies));
    }

    /**
     * Calls a definition's symbol, {@code const void *f(void)}, once, and answers the struct it points at as the
     * language's own object carrying that very address: a {@code MemorySegment} of the struct's {@code size} bytes on
     * the foreign function API, a JNA {@code Pointer} on JNA. Nothing past the struct's {@code size} is read.
     *
     * @param symbol the definition's symbol
     * @return the object; {@link Answer.Mistake} for a symbol the library does not export, a NULL answer, or a struct
     *     whose {@code size} is below {@code 2 * sizeof(void *)}, the size and one pointer
     */
    public Answer<Object> definition(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (!library.has(symbol)) {
            return new Answer.Mistake<>("the library exports no function " + symbol);
        }
        long address = library.function(symbol, DEFINITION).call();
        if (address == 0) {
            return new Answer.Mistake<>("the definition " + symbol + " answered NULL");
        }
        long size = Integer.toUnsignedLong(access.getInt(address, 0));
        long smallest = 2 * access.addressSize();
        if (size < smallest) {
            return new Answer.Mistake<>("the definition " + symbol + " answered a struct of " + size
                    + " bytes, below the " + smallest + " of its size and one pointer");
        }
        return new Answer.Ok<>(access.addressed(address, size));
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
