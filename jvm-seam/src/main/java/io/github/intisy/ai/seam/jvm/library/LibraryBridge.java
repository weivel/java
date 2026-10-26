package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import io.github.intisy.ai.engine.PluginException;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An opened library: every declared symbol bound, callable by symbol until closed.
 *
 * @implNote Every declared function is the one {@code json} kind,
 * {@code int32_t f(const uint8_t *in, size_t in_len, uint8_t **out, size_t *out_len)}, so there is
 * one call path and no per-function code. Every result is copied out of library memory before the
 * library's own release function frees it, in a {@code finally}, so a result that turns out not to
 * be UTF-8 or JSON is still released exactly once; a NULL result is never released, since it is
 * how a library that could not allocate its answer reports that, and there is nothing of its to free.
 * A function declaring {@code context} is sent {@code {"argument": A, "context": {...}}} holding only
 * the facts it declared, gathered afresh from the context supplier at that call; one declaring none
 * is sent its argument unchanged, and the supplier is never asked.
 */
public final class LibraryBridge implements AutoCloseable {

    /** Told of every release the bridge makes; a test seam, a host passes none. */
    interface ReleaseObserver {
        void released();
    }

    private static final ReleaseObserver UNOBSERVED = () -> { };

    private static final class BoundFunction {
        private final String label;
        private final Function function;
        private final List<String> context;

        BoundFunction(String label, Function function, List<String> context) {
            this.label = label;
            this.function = function;
            this.context = context;
        }
    }

    private final String libraryId;
    private final NativeLibrary nativeLibrary;
    private final Function release;
    private final Map<String, BoundFunction> functions;
    private final ReleaseObserver releaseObserver;
    private final Supplier<LibraryContext> contextSupplier;
    private final ReadWriteLock openness = new ReentrantReadWriteLock();
    private boolean closed;

    private LibraryBridge(String libraryId, NativeLibrary nativeLibrary, Function release,
                          Map<String, BoundFunction> functions, ReleaseObserver releaseObserver,
                          Supplier<LibraryContext> contextSupplier) {
        this.libraryId = libraryId;
        this.nativeLibrary = nativeLibrary;
        this.release = release;
        this.functions = functions;
        this.releaseObserver = releaseObserver;
        this.contextSupplier = contextSupplier;
    }

    /**
     * Opens a verified binary and binds every symbol its declaration names, refusing at open any it lacks.
     *
     * @param binary the verified shared library, as {@link LibraryAcquirer} placed it
     * @param declaration the verified declaration naming its release function and its functions
     * @return the opened library
     * @throws PluginException naming the library when the binary cannot be loaded or lacks a declared symbol
     */
    public static LibraryBridge open(Path binary, LibraryDeclaration declaration) {
        return open(binary, declaration, processContext());
    }

    /**
     * Opens a verified binary as {@link #open(Path, LibraryDeclaration)} does, handing its contextual functions the supplied facts.
     *
     * @param binary the verified shared library, as {@link LibraryAcquirer} placed it
     * @param declaration the verified declaration naming its release function and its functions
     * @param contextSupplier asked once at each call to a function that declares {@code context}, and never otherwise
     * @return the opened library
     * @throws PluginException naming the library when the binary cannot be loaded or lacks a declared symbol
     */
    public static LibraryBridge open(Path binary, LibraryDeclaration declaration, Supplier<LibraryContext> contextSupplier) {
        return open(binary, declaration, UNOBSERVED, contextSupplier);
    }

    static LibraryBridge open(Path binary, LibraryDeclaration declaration, ReleaseObserver releaseObserver) {
        return open(binary, declaration, releaseObserver, processContext());
    }

    static Supplier<LibraryContext> processContext() {
        return () -> LibraryContext.ofProcess(Collections.<String>emptyList());
    }

    private static LibraryBridge open(Path binary, LibraryDeclaration declaration, ReleaseObserver releaseObserver,
                                      Supplier<LibraryContext> contextSupplier) {
        NativeLibrary nativeLibrary = load(binary, declaration.id());
        try {
            Function release = bindSymbol(nativeLibrary, declaration.id(), declaration.release());
            return new LibraryBridge(declaration.id(), nativeLibrary, release, bindDeclared(nativeLibrary, declaration),
                    releaseObserver, contextSupplier);
        } catch (RuntimeException refused) {
            SharedNativeLibraries.letGo(nativeLibrary);
            throw refused;
        }
    }

    private static NativeLibrary load(Path binary, String libraryId) {
        try {
            return SharedNativeLibraries.hold(binary.toAbsolutePath().toString());
        } catch (UnsatisfiedLinkError unloadable) {
            throw new PluginException(libraryId, binary + " could not be loaded: " + unloadable.getMessage(),
                    "rebuild the library for this platform and publish it again");
        }
    }

    private static Function bindSymbol(NativeLibrary nativeLibrary, String libraryId, String symbol) {
        try {
            return nativeLibrary.getFunction(symbol);
        } catch (UnsatisfiedLinkError absent) {
            throw new PluginException(libraryId,
                    "the binary exports no symbol " + symbol + ", which its declaration names: " + absent.getMessage(),
                    "rebuild the library so it exports " + symbol + ", or re-pin a release whose library.json matches its binary");
        }
    }

    private static Map<String, BoundFunction> bindDeclared(NativeLibrary nativeLibrary, LibraryDeclaration declaration) {
        Map<String, BoundFunction> bound = new LinkedHashMap<String, BoundFunction>();
        for (LibraryDeclaration.ServiceDeclaration service : declaration.services()) {
            for (LibraryDeclaration.FunctionDeclaration function : service.functions()) {
                Function nativeFunction = bindSymbol(nativeLibrary, declaration.id(), function.symbol());
                bound.put(function.symbol(), new BoundFunction(service.id() + "." + function.name(), nativeFunction, function.context()));
            }
        }
        return bound;
    }

    /** @return the id of the library this bridge opened */
    public String libraryId() {
        return libraryId;
    }

    /**
     * Calls one declared {@code json} function.
     *
     * @param symbol the declared symbol to call
     * @param argumentJson the argument as JSON text; null sends {@code null}
     * @return the function's JSON result, as text
     * @throws PluginException naming the library when the call fails, the library is closed, or its answer is not UTF-8 JSON
     */
    public String call(String symbol, String argumentJson) {
        openness.readLock().lock();
        try {
            BoundFunction bound = functions.get(symbol);
            if (closed || bound == null) {
                String reason = closed ? "the library is closed" : symbol + " is not a declared function";
                throw new PluginException(libraryId, symbol + " cannot be called: " + reason, "call a declared function of an open library");
            }
            return invoke(bound, withDeclaredContext(bound, argumentJson == null ? "null" : argumentJson));
        } finally {
            openness.readLock().unlock();
        }
    }

    private String withDeclaredContext(BoundFunction bound, String argumentJson) {
        if (bound.context.isEmpty()) {
            return argumentJson;
        }
        return "{\"argument\":" + argumentJson + ",\"context\":" + gatherContext(bound.label).declaredFacts(bound.context) + "}";
    }

    private LibraryContext gatherContext(String label) {
        LibraryContext context;
        try {
            context = contextSupplier.get();
        } catch (RuntimeException failed) {
            String reason = failed.getMessage() == null ? failed.getClass().getName() : failed.getMessage();
            throw new PluginException(libraryId, label + " needs the host's context, and gathering it failed: " + reason,
                    "fix the host's library context supplier so it returns the facts");
        }
        String malformed = LibraryContext.malformedFact(context);
        if (malformed != null) {
            throw new PluginException(libraryId, label + " was given a context whose " + malformed + " is malformed",
                    "make the host's library context supplier return " + malformed + " as " + LibraryContext.shapeOf(malformed));
        }
        return context;
    }

    private String invoke(BoundFunction bound, String argumentJson) {
        byte[] input = argumentJson.getBytes(StandardCharsets.UTF_8);
        Memory inputMemory = new Memory(Math.max(1, input.length));
        inputMemory.write(0, input, 0, input.length);
        PointerByReference out = new PointerByReference();
        Memory outLength = new Memory(Native.SIZE_T_SIZE);
        outLength.clear();
        int status = bound.function.invokeInt(new Object[] {inputMemory, sizeArgument(input.length), out, outLength});
        byte[] answer = takeResult(bound.label, out.getValue(), sizeAt(outLength));
        return answerOf(bound.label, status, answer);
    }

    /**
     * A C {@code size_t} as JNA passes it: a {@code long} where it is 64 bits, an {@code int} where it is 32.
     *
     * @implNote Not {@code NativeLong}, which is a C {@code long} and so 32 bits on 64-bit Windows,
     * where {@code size_t} is 64 (measured: {@code Native.SIZE_T_SIZE} is 8 there). Not an
     * {@code IntegerType} subclass either: JNA instantiates one reflectively and cannot reach a
     * class that is not public (measured).
     */
    private static Object sizeArgument(long size) {
        return Native.SIZE_T_SIZE == 8 ? (Object) Long.valueOf(size) : (Object) Integer.valueOf((int) size);
    }

    private static long sizeAt(Memory cell) {
        return Native.SIZE_T_SIZE == 8 ? cell.getLong(0) : cell.getInt(0) & 0xffffffffL;
    }

    private byte[] takeResult(String label, Pointer result, long length) {
        if (result == null) {
            return null;
        }
        try {
            if (length > Integer.MAX_VALUE) {
                throw new PluginException(libraryId, label + " answered " + length + " bytes, more than a JVM array holds",
                        "report it to the maintainers of " + libraryId + ": an answer must stay under 2 GiB");
            }
            return result.getByteArray(0, (int) length);
        } finally {
            release.invokeVoid(new Object[] {result});
            releaseObserver.released();
        }
    }

    private String answerOf(String label, int status, byte[] answer) {
        if (answer == null) {
            throw missingAnswer(label, status);
        }
        String text = textOf(label, answer);
        JsonElement parsed = jsonOf(label, text);
        if (status != 0) {
            throw failureOf(label, status, parsed);
        }
        return text;
    }

    private String textOf(String label, byte[] answer) {
        try {
            return StrictJson.decodeUtf8(answer);
        } catch (CharacterCodingException notUtf8) {
            throw new PluginException(libraryId, label + " answered bytes that are not UTF-8", malformedAnswerFix());
        }
    }

    private JsonElement jsonOf(String label, String text) {
        try {
            return StrictJson.parse(text);
        } catch (IOException notJson) {
            throw new PluginException(libraryId, label + " answered text that is not JSON", malformedAnswerFix());
        }
    }

    private String malformedAnswerFix() {
        return "report it to the maintainers of " + libraryId + ": its answers must be UTF-8 JSON";
    }

    private PluginException failureOf(String label, int status, JsonElement answer) {
        JsonObject failure = answer.isJsonObject() ? answer.getAsJsonObject() : new JsonObject();
        if (isString(failure.get("message")) && isString(failure.get("fix"))) {
            return new PluginException(libraryId, label + ": " + failure.get("message").getAsString(), failure.get("fix").getAsString());
        }
        return new PluginException(libraryId, label + " failed with status " + status + " and no {message, fix} object",
                "report it to the maintainers of " + libraryId + ": a failure must say what went wrong and how to fix it");
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private PluginException missingAnswer(String label, int status) {
        if (status == 0) {
            return new PluginException(libraryId, label + " reported success but returned no answer",
                    "report it to the maintainers of " + libraryId + ": a success must carry its JSON result");
        }
        return new PluginException(libraryId, label + " failed with status " + status + " and returned no answer",
                "the library could not allocate its answer; retry the call");
    }

    /**
     * Unloads the binary; a later call is refused rather than reaching unloaded code. Closing twice does nothing.
     *
     * @implNote A call holds the read side of a lock and closing takes the write side, so a close
     * waits for calls in flight: unloading under a running call jumps into freed code, and JNA
     * reports that as {@code Invalid memory access} at best (measured on a disposed library).
     */
    @Override
    public void close() {
        openness.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            SharedNativeLibraries.letGo(nativeLibrary);
        } finally {
            openness.writeLock().unlock();
        }
    }
}
