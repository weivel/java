package com.weivel.standard;

import com.weivel.core.Answer;
import com.weivel.core.ForeignFunction;
import com.weivel.core.ForeignLibrary;
import com.weivel.core.ForeignMemory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The standard's shared binary, loaded from a path its caller gives through the layer's native seam, and every
 * function of it this package calls. Nothing outside this package and {@code conformance} loads it, and a host never
 * needs it.
 */
public final class StandardBinary implements AutoCloseable {
    private final ForeignLibrary library;
    private final Map<StandardFunction, ForeignFunction> functions;
    private volatile ReleaseObserver releaseObserver = ReleaseObserver.NONE;

    private StandardBinary(ForeignLibrary library, Map<StandardFunction, ForeignFunction> functions) {
        this.library = library;
        this.functions = functions;
    }

    /**
     * Opens the standard's binary and binds every function of it this package calls.
     *
     * @param path the binary, a path the caller chose
     * @return the binary; {@link Answer.Mistake} for a path that names no library, or naming the first of the
     *     standard's functions the library lacks
     */
    public static Answer<StandardBinary> load(Path path) {
        Objects.requireNonNull(path, "path");
        List<String> symbols = new ArrayList<>();
        for (StandardFunction function : StandardFunction.values()) {
            symbols.add(function.symbol());
        }
        Answer<ForeignLibrary> opened = ForeignLibrary.open(path, symbols);
        if (!(opened instanceof Answer.Ok)) {
            return Answer.retyped(opened);
        }
        ForeignLibrary library = ((Answer.Ok<ForeignLibrary>) opened).value();
        Map<StandardFunction, ForeignFunction> functions = new EnumMap<>(StandardFunction.class);
        for (StandardFunction function : StandardFunction.values()) {
            functions.put(function, library.function(function.symbol(), function.returned(), function.parameters()));
        }
        return new Answer.Ok<>(new StandardBinary(library, functions));
    }

    long call(StandardFunction function, long... arguments) {
        return functions.get(function).call(arguments);
    }

    /** Hands a struct the standard filled to the standard's release for its kind, telling the observer first. */
    void release(StandardFunction release, long struct) {
        releaseObserver.releasing(release, struct);
        call(release, struct);
    }

    /** The seam a test sees every release through, and fails one through. */
    void observeReleases(ReleaseObserver observer) {
        releaseObserver = Objects.requireNonNull(observer, "observer");
    }

    ForeignMemory memory() {
        return library.memory();
    }

    /**
     * Closes the binary, after which nothing read through it may be called.
     *
     * @throws IllegalStateException when it was closed already, or a call into it is under way
     */
    @Override
    public void close() {
        library.close();
    }
}
