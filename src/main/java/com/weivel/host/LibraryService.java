package com.weivel.host;

import com.weivel.core.LibraryFunction;
import com.weivel.core.LibraryOutput;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The object a C library's service is registered with under {@code WEIVEL_HOST}: one function per declared function,
 * called by its declared name. A consumer resolving the service is answered this very object, and a call through it
 * is a plain Java call into the library that crosses no core call and takes no lock of the layer's.
 *
 * <p>A direct buffer's input is lent to the library for the length of the call, and a heap array's copied once into
 * native memory, since lending the heap would hold off garbage collection for the whole call. The body the library
 * answers is copied once into a Java array and released before the call answers. Once the library is unloaded every
 * call answers {@link LibraryAnswer.Unloaded}, calling nothing. Any thread may call.
 */
public final class LibraryService {
    private final String id;
    private final Map<String, LibraryFunction> functions;
    private final LibraryLifetime lifetime;
    private final Pauses pauses;

    LibraryService(String id, Map<String, LibraryFunction> functions, LibraryLifetime lifetime, Pauses pauses) {
        this.id = id;
        this.functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        this.lifetime = lifetime;
        this.pauses = pauses;
    }

    /**
     * Answers {@code id}.
     *
     * @return the service id, as declared
     */
    public String id() {
        return id;
    }

    /**
     * Answers {@code functionNames}.
     *
     * @return the declared functions' names, in the declaration's order
     */
    public List<String> functionNames() {
        return Collections.unmodifiableList(new ArrayList<>(functions.keySet()));
    }

    /**
     * Calls a function with a heap array, copied once into native memory for the call.
     *
     * @param function the function's declared name
     * @param input the bytes, never changed
     * @return the result, the failure, or {@link LibraryAnswer.Unloaded}
     * @throws IllegalArgumentException for a name the service does not declare
     */
    public LibraryAnswer call(String function, byte[] input) {
        Objects.requireNonNull(input, "input");
        LibraryFunction bound = named(function);
        if (!lifetime.enter()) {
            return new LibraryAnswer.Unloaded();
        }
        try {
            pauses.whileALibraryCallRuns(id);
            return answered(bound.call(input));
        } finally {
            lifetime.leave();
        }
    }

    /**
     * Calls a function with a buffer's remaining bytes: a direct buffer's lent in place, a heap buffer's copied once.
     *
     * @param function the function's declared name
     * @param input the bytes, never changed, its position and limit left as they were
     * @return the result, the failure, or {@link LibraryAnswer.Unloaded}
     * @throws IllegalArgumentException for a name the service does not declare
     */
    public LibraryAnswer call(String function, ByteBuffer input) {
        Objects.requireNonNull(input, "input");
        LibraryFunction bound = named(function);
        if (!lifetime.enter()) {
            return new LibraryAnswer.Unloaded();
        }
        try {
            pauses.whileALibraryCallRuns(id);
            return answered(bound.call(input));
        } finally {
            lifetime.leave();
        }
    }

    @Override
    public String toString() {
        return "LibraryService[id=" + id + ", functions=" + functions.keySet() + "]";
    }

    private LibraryFunction named(String function) {
        Objects.requireNonNull(function, "function");
        LibraryFunction bound = functions.get(function);
        if (bound == null) {
            throw new IllegalArgumentException("the service " + id + " declares no function " + function);
        }
        return bound;
    }

    private static LibraryAnswer answered(LibraryOutput output) {
        return LibraryAnswer.of(output.status(), output.body());
    }
}
