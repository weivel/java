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
    private final SpareArena arenas;
    private final DescribedHandles handles = new DescribedHandles();

    private LibraryBinary(NativeAccess access, OpenLibrary library, NativeFunction release) {
        this.access = access;
        this.library = library;
        this.release = release;
        this.spare = new SpareBlock(access);
        this.arenas = new SpareArena(access);
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
        Answer<Long> address = definitionAddress(symbol);
        if (!(address instanceof Answer.Ok)) {
            return Answer.retyped(address);
        }
        long definition = ((Answer.Ok<Long>) address).value();
        return new Answer.Ok<>(access.addressed(definition, sizeAt(definition)));
    }

    /**
     * Binds a definition from the description its library answers for it, as {@link #definition} answers the
     * definition itself: the description is checked by {@code check} before anything else of it is read, every struct
     * it names is laid out by this platform's own rules and held against it, and every operation is bound once here.
     *
     * @param definitionSymbol the definition's symbol
     * @param descriptionSymbol the symbol answering its {@code weivel_standard_description}
     * @param check the standard's description check, which its caller hands the layer
     * @param gate who decides whether a call may still enter the binary, for every call the bound definition makes
     * @return the bound definition; {@link Answer.Mistake} for anything {@link #definition} refuses, a description
     *     symbol the library does not export or that answers NULL, a description the check refuses, one whose layout
     *     disagrees with this platform's or that this binding cannot bind, and a backend that binds no description yet
     */
    public Answer<BoundDefinition> described(String definitionSymbol, String descriptionSymbol,
                                             DescriptionCheck check, CallGate gate) {
        Objects.requireNonNull(descriptionSymbol, "descriptionSymbol");
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(gate, "gate");
        Answer<Long> address = definitionAddress(definitionSymbol);
        if (!(address instanceof Answer.Ok)) {
            return Answer.retyped(address);
        }
        long definition = ((Answer.Ok<Long>) address).value();
        if (!library.has(descriptionSymbol)) {
            return new Answer.Mistake<>("the library exports no function " + descriptionSymbol);
        }
        long description = library.function(descriptionSymbol, DEFINITION).call();
        if (description == 0) {
            return new Answer.Mistake<>("the description " + descriptionSymbol + " answered NULL");
        }
        Optional<String> refusal = check.refusalOf(access.addressed(description, sizeAt(description)));
        if (refusal.isPresent()) {
            return new Answer.Mistake<>("the standard's check refuses the description " + descriptionSymbol + ": "
                    + refusal.get());
        }
        handles.gate(gate);
        try {
            return new Answer.Ok<>(DescribedBinder.bind(access, new DescriptionReader(access).read(description),
                    definition, access.addressed(definition, sizeAt(definition)),
                    new BoundOperation.Bound(access, arenas, handles, gate)));
        } catch (DescriptionRefused | DescriptionsNotBound refused) {
            return new Answer.Mistake<>(refused.getMessage());
        }
    }

    /**
     * Releases every handle a described definition of this binary answered and nobody released yet, through the
     * release each one's description names, each one a call is still handing its provider once that call returns;
     * the binary must still be open. Its close releases any answered after this.
     */
    public void releaseHandles() {
        handles.releaseAll();
    }

    private Answer<Long> definitionAddress(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (!library.has(symbol)) {
            return new Answer.Mistake<>("the library exports no function " + symbol);
        }
        long address = library.function(symbol, DEFINITION).call();
        if (address == 0) {
            return new Answer.Mistake<>("the definition " + symbol + " answered NULL");
        }
        long size = sizeAt(address);
        long smallest = 2 * access.addressSize();
        if (size < smallest) {
            return new Answer.Mistake<>("the definition " + symbol + " answered a struct of " + size
                    + " bytes, below the " + smallest + " of its size and one pointer");
        }
        return new Answer.Ok<>(address);
    }

    private long sizeAt(long address) {
        return Integer.toUnsignedLong(access.getInt(address, 0));
    }

    /**
     * Closes the binary, after which no function bound in it may be called.
     *
     * @throws IllegalStateException when the platform refuses, as for a call still under way or a second close
     */
    @Override
    public void close() {
        handles.releaseAtTheClose();
        library.close();
        spare.close();
        arenas.close();
    }
}
