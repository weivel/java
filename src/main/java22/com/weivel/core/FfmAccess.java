package com.weivel.core;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The native seam through the foreign function and memory API, for Java 22 and later: a library opened in a shared
 * arena, each function a downcall handle, memory read and written by address.
 */
final class FfmAccess implements NativeAccess {
    /** The one instance, holding nothing per caller. */
    static final FfmAccess INSTANCE = new FfmAccess();

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MemorySegment EVERYTHING = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);
    /**
     * The longest clear made with plain stores; a longer one fills a slice.
     *
     * @implNote Filling a slice makes a segment per clear, which escape analysis does not always remove, and a call's
     * arguments are cleared on every crossing; a copy from an array of zeros made none but cost about 30 ns a call.
     */
    private static final long LONGEST_STORED_CLEAR = 64;
    private static final MemoryLayout SIZE_T = LINKER.canonicalLayouts().get("size_t");
    private static final MethodHandle CALLOC = standardFunction("calloc", FunctionDescriptor.of(JAVA_LONG, SIZE_T,
            SIZE_T));
    private static final MethodHandle FREE = standardFunction("free", FunctionDescriptor.ofVoid(JAVA_LONG));
    private static final int MOST_PARAMETERS = 7;

    private FfmAccess() {
    }

    @Override
    public String name() {
        return "ffm";
    }

    @Override
    public long addressSize() {
        return ADDRESS.byteSize();
    }

    @Override
    public String toString() {
        return name();
    }

    /** @implNote The arena is shared because any thread may call into the library, and closing it unloads it. */
    @Override
    public OpenLibrary open(Path path) {
        Arena arena = Arena.ofShared();
        try {
            return new FfmLibrary(path, arena, SymbolLookup.libraryLookup(path, arena));
        } catch (IllegalArgumentException notALibrary) {
            arena.close();
            throw notALibrary;
        }
    }

    @Override
    public long allocate(long size) {
        long allocated;
        try {
            allocated = (long) CALLOC.invokeExact(1L, Math.max(1, size));
        } catch (Throwable thrown) {
            throw rethrown(thrown);
        }
        if (allocated == 0) {
            throw new OutOfMemoryError("no native memory for " + size + " bytes");
        }
        return allocated;
    }

    @Override
    public void free(long address) {
        try {
            FREE.invokeExact(address);
        } catch (Throwable thrown) {
            throw rethrown(thrown);
        }
    }

    @Override
    public int getInt(long address, long offset) {
        return EVERYTHING.get(JAVA_INT_UNALIGNED, address + offset);
    }

    @Override
    public void putInt(long address, long offset, int value) {
        EVERYTHING.set(JAVA_INT_UNALIGNED, address + offset, value);
    }

    @Override
    public long getLong(long address, long offset) {
        return EVERYTHING.get(JAVA_LONG_UNALIGNED, address + offset);
    }

    @Override
    public void putLong(long address, long offset, long value) {
        EVERYTHING.set(JAVA_LONG_UNALIGNED, address + offset, value);
    }

    @Override
    public double getDouble(long address, long offset) {
        return EVERYTHING.get(JAVA_DOUBLE_UNALIGNED, address + offset);
    }

    @Override
    public void putDouble(long address, long offset, double value) {
        EVERYTHING.set(JAVA_DOUBLE_UNALIGNED, address + offset, value);
    }

    @Override
    public byte[] readBytes(long address, long offset, int length) {
        byte[] bytes = new byte[length];
        MemorySegment.copy(EVERYTHING, JAVA_BYTE, address + offset, bytes, 0, length);
        return bytes;
    }

    @Override
    public void writeBytes(long address, long offset, byte[] bytes) {
        MemorySegment.copy(bytes, 0, EVERYTHING, JAVA_BYTE, address + offset, bytes.length);
    }

    @Override
    public void clear(long address, long offset, long length) {
        if (length < 0) {
            throw new IndexOutOfBoundsException("a clear of " + length + " bytes");
        }
        long start = address + offset;
        if (length > LONGEST_STORED_CLEAR) {
            EVERYTHING.asSlice(start, length).fill((byte) 0);
            return;
        }
        long cleared = 0;
        for (; cleared + Long.BYTES <= length; cleared += Long.BYTES) {
            EVERYTHING.set(JAVA_LONG_UNALIGNED, start + cleared, 0L);
        }
        for (; cleared < length; cleared++) {
            EVERYTHING.set(JAVA_BYTE, start + cleared, (byte) 0);
        }
    }

    @Override
    public String readText(long address) {
        return EVERYTHING.getString(address, StandardCharsets.UTF_8);
    }

    @Override
    public long addressOf(ByteBuffer direct) {
        if (!direct.isDirect()) {
            throw new IllegalArgumentException("a heap buffer has no native address");
        }
        return MemorySegment.ofBuffer(direct).address();
    }

    @Override
    public void writeBytes(long address, long offset, byte[] bytes, int from, int length) {
        MemorySegment.copy(bytes, from, EVERYTHING, JAVA_BYTE, address + offset, length);
    }

    @Override
    public void keepReachable(Object object) {
        Reference.reachabilityFence(object);
    }

    @Override
    public Object addressed(long address, long size) {
        return MemorySegment.ofAddress(address).reinterpret(size);
    }

    @Override
    public long addressOf(Object addressed) {
        if (!(addressed instanceof MemorySegment segment)) {
            throw new IllegalArgumentException("a " + addressed.getClass().getName() + " carries no native address");
        }
        return segment.address();
    }

    @Override
    public NativeFunction function(long address, NativeSignature signature) {
        if (signature.parameters().size() > MOST_PARAMETERS) {
            throw new IllegalArgumentException("a function pointer of more than " + MOST_PARAMETERS + " parameters");
        }
        return new FfmFunction(FfmLibrary.adapted(LINKER.downcallHandle(MemorySegment.ofAddress(address),
                FfmLibrary.descriptorOf(signature)), signature), signature.parameters().size());
    }

    private static MethodHandle standardFunction(String symbol, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(LINKER.defaultLookup().find(symbol).get(), descriptor);
    }

    private static RuntimeException rethrown(Throwable thrown) {
        if (thrown instanceof RuntimeException runtime) {
            return runtime;
        }
        if (thrown instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(thrown);
    }

    /** A library the foreign function and memory API opened, held open by its own arena. */
    private static final class FfmLibrary implements OpenLibrary {
        private final Path path;
        private final Arena arena;
        private final SymbolLookup lookup;

        FfmLibrary(Path path, Arena arena, SymbolLookup lookup) {
            this.path = path;
            this.arena = arena;
            this.lookup = lookup;
        }

        @Override
        public boolean has(String symbol) {
            requireOpen();
            return lookup.find(symbol).isPresent();
        }

        @Override
        public NativeFunction function(String symbol, NativeSignature signature) {
            requireOpen();
            if (signature.parameters().size() > MOST_PARAMETERS) {
                throw new IllegalArgumentException(symbol + " takes more than " + MOST_PARAMETERS + " parameters");
            }
            Optional<MemorySegment> found = lookup.find(symbol);
            if (found.isEmpty()) {
                throw new IllegalArgumentException("the library exports no function " + symbol);
            }
            return new FfmFunction(adapted(LINKER.downcallHandle(found.get(), descriptorOf(signature)), signature),
                    signature.parameters().size());
        }

        private void requireOpen() {
            if (!arena.scope().isAlive()) {
                throw new IllegalStateException("the library " + path + " is closed");
            }
        }

        @Override
        public void close() {
            arena.close();
        }

        /**
         * Adapts a downcall handle to take every argument as a {@code long} and answer a {@code long}: an {@code int}
         * narrowed, an {@code int} answer widened, a {@code void} answered 0.
         */
        private static MethodHandle adapted(MethodHandle downcall, NativeSignature signature) {
            MethodHandle adapted = downcall;
            List<NativeType> parameters = signature.parameters();
            Class<?>[] longs = new Class<?>[parameters.size()];
            Arrays.fill(longs, long.class);
            return MethodHandles.explicitCastArguments(adapted, MethodType.methodType(long.class, longs));
        }

        private static FunctionDescriptor descriptorOf(NativeSignature signature) {
            MemoryLayout[] parameters = signature.parameters().stream().map(FfmLibrary::layoutOf)
                    .toArray(MemoryLayout[]::new);
            return signature.returned() == NativeType.VOID
                    ? FunctionDescriptor.ofVoid(parameters)
                    : FunctionDescriptor.of(layoutOf(signature.returned()), parameters);
        }

        /**
         * The layout a type crosses as.
         *
         * @implNote A pointer crosses as its 64-bit address, which every 64-bit C calling convention the layer runs on
         *     passes and answers as it does a pointer. Crossing it as a {@code MemorySegment} made one segment a
         *     call, gone only where the JIT inlined the whole call: on macOS runners it was not, and each pointer
         *     argument cost an allocation.
         */
        private static MemoryLayout layoutOf(NativeType type) {
            return switch (type) {
                case INT -> JAVA_INT;
                case LONG -> JAVA_LONG;
                case ADDRESS -> JAVA_LONG;
                case VOID -> throw new IllegalArgumentException("void is no parameter");
            };
        }
    }

    /**
     * A downcall handle taking one {@code long} per parameter, called with {@code invokeExact} through the call of its
     * arity, so no call allocates.
     */
    private static final class FfmFunction implements NativeFunction {
        private final MethodHandle handle;
        private final int arity;

        FfmFunction(MethodHandle handle, int arity) {
            this.handle = handle;
            this.arity = arity;
        }

        @Override
        public long call(long... arguments) {
            switch (arguments.length) {
                case 0:
                    return call();
                case 1:
                    return call(arguments[0]);
                case 2:
                    return call(arguments[0], arguments[1]);
                case 3:
                    return call(arguments[0], arguments[1], arguments[2]);
                case 4:
                    return call(arguments[0], arguments[1], arguments[2], arguments[3]);
                case 5:
                    return call(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4]);
                case 6:
                    return call(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5]);
                case 7:
                    return call(arguments[0], arguments[1], arguments[2], arguments[3], arguments[4], arguments[5],
                            arguments[6]);
                default:
                    throw wrongCount(arguments.length);
            }
        }

        @Override
        public long call() {
            requireArity(0);
            try {
                return (long) handle.invokeExact();
            } catch (Throwable thrown) {
                throw rethrown(thrown);
            }
        }

        @Override
        public long call(long first) {
            requireArity(1);
            try {
                return (long) handle.invokeExact(first);
            } catch (Throwable thrown) {
                throw rethrown(thrown);
            }
        }

        @Override
        public long call(long first, long second) {
            requireArity(2);
            try {
                return (long) handle.invokeExact(first, second);
            } catch (Throwable thrown) {
                throw rethrown(thrown);
            }
        }

        @Override
        public long call(long first, long second, long third) {
            requireArity(3);
            try {
                return (long) handle.invokeExact(first, second, third);
            } catch (Throwable thrown) {
                throw rethrown(thrown);
            }
        }

        @Override
        public long call(long first, long second, long third, long fourth) {
            requireArity(4);
            try {
                return (long) handle.invokeExact(first, second, third, fourth);
            } catch (Throwable thrown) {
                throw rethrown(thrown);
            }
        }

        @Override
        public long call(long first, long second, long third, long fourth, long fifth) {
            requireArity(5);
            try {
                return (long) handle.invokeExact(first, second, third, fourth, fifth);
            } catch (Throwable thrown) {
                throw rethrown(thrown);
            }
        }

        @Override
        public long call(long first, long second, long third, long fourth, long fifth, long sixth) {
            requireArity(6);
            try {
                return (long) handle.invokeExact(first, second, third, fourth, fifth, sixth);
            } catch (Throwable thrown) {
                throw rethrown(thrown);
            }
        }

        @Override
        public long call(long first, long second, long third, long fourth, long fifth, long sixth, long seventh) {
            requireArity(7);
            try {
                return (long) handle.invokeExact(first, second, third, fourth, fifth, sixth, seventh);
            } catch (Throwable thrown) {
                throw rethrown(thrown);
            }
        }

        private void requireArity(int count) {
            if (count != arity) {
                throw wrongCount(count);
            }
        }

        private IllegalArgumentException wrongCount(int count) {
            return new IllegalArgumentException("a function of " + arity + " parameters was handed " + count
                    + " arguments");
        }
    }
}
