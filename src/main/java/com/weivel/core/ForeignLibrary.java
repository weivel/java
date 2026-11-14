package com.weivel.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A C library other than the core, opened from a path its caller gives through the layer's one native seam, for a
 * package of the layer that binds such a library's functions itself, as {@code standard} binds the standard's.
 *
 * @implNote This is the narrowest public route to the seam: its types stay package-private, and a caller reaches
 * only the symbols it named when it opened the library, never one of the core's, so nothing bound here can cross into
 * the core past the wrapper. Without a module descriptor Java cannot limit a public class to some packages, as
 * {@link CoreInternals} notes too.
 */
public final class ForeignLibrary implements AutoCloseable {
    private final NativeAccess access;
    private final OpenLibrary library;
    private final Set<String> symbols;

    private ForeignLibrary(NativeAccess access, OpenLibrary library, Set<String> symbols) {
        this.access = access;
        this.library = library;
        this.symbols = symbols;
    }

    /**
     * Opens a library and checks that it exports every symbol named, in order.
     *
     * @param path the library, a path the caller chose
     * @param symbols every symbol the caller will bind; none may be one of the core's
     * @return the library; {@link Answer.Mistake} for a core symbol named, for a path that names no library, or naming
     *     the first symbol the library lacks, the library then closed again
     */
    public static Answer<ForeignLibrary> open(Path path, List<String> symbols) {
        Objects.requireNonNull(path, "path");
        List<String> named = new ArrayList<>(Objects.requireNonNull(symbols, "symbols"));
        for (String symbol : named) {
            if (CoreSymbols.isCoreSymbol(Objects.requireNonNull(symbol, "a symbol"))) {
                return new Answer.Mistake<>(symbol + " is the core's, and only the core's wrapper calls the core");
            }
        }
        NativeAccess access = NativeBackends.current();
        OpenLibrary library;
        try {
            library = access.open(path);
        } catch (IllegalArgumentException notALibrary) {
            return new Answer.Mistake<>(path + " is no library the platform could open: "
                    + notALibrary.getMessage());
        }
        for (String symbol : named) {
            if (!library.has(symbol)) {
                library.close();
                return new Answer.Mistake<>(path + " exports no function " + symbol);
            }
        }
        return new Answer.Ok<>(new ForeignLibrary(access, library,
                Collections.unmodifiableSet(new HashSet<>(named))));
    }

    /**
     * Binds one of the symbols the library was opened with.
     *
     * @param symbol the symbol
     * @param returned what it answers
     * @param parameters what it takes, in order
     * @return the function
     * @throws IllegalArgumentException for a symbol not named when the library was opened
     */
    public ForeignFunction function(String symbol, NativeType returned, NativeType... parameters) {
        if (!symbols.contains(symbol)) {
            throw new IllegalArgumentException(symbol + " was not named when the library was opened");
        }
        return new ForeignFunction(library.function(symbol, NativeSignature.of(returned, parameters)));
    }

    /**
     * Native memory of the same seam as this library's functions, for their arguments and answers.
     *
     * @return memory of its own, freed when it is closed
     */
    public ForeignMemory memory() {
        return new ForeignMemory(new NativeArena(access));
    }

    /**
     * Closes the library, after which no function bound in it may be called.
     *
     * @throws IllegalStateException when it was closed already, or a call into it is under way
     */
    @Override
    public void close() {
        library.close();
    }
}
