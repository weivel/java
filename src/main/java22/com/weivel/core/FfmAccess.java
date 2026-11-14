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
    private static final MemoryLayout SIZE_T = LINKER.canonicalLayouts().get("size_t");
    private static final MethodHandle CALLOC = standardFunction("calloc", FunctionDescriptor.of(ADDRESS, SIZE_T,
            SIZE_T));
    private static final MethodHandle FREE = standardFunction("free", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle SEGMENT_AT = segmentAtHandle();
    private static final MethodHandle ADDRESS_OF = addressOfHandle();

    private FfmAccess() {
    }

    @Override
    public String name() {
        return "ffm";
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
            return new FfmLibrary(arena, SymbolLookup.libraryLookup(path, arena));
        } catch (IllegalArgumentException notALibrary) {
            arena.close();
            throw notALibrary;
        }
    }

    @Override
    public long allocate(long size) {
        MemorySegment allocated;
        try {
            allocated = (MemorySegment) CALLOC.invokeExact(1L, Math.max(1, size));
        } catch (Throwable thrown) {
            throw rethrown(thrown);
        }
        if (allocated.address() == 0) {
            throw new OutOfMemoryError("no native memory for " + size + " bytes");
        }
        return allocated.address();
    }

    @Override
    public void free(long address) {
        try {
            FREE.invokeExact(MemorySegment.ofAddress(address));
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
        EVERYTHING.asSlice(address + offset, length).fill((byte) 0);
    }

    @Override
    public String readText(long address) {
        return EVERYTHING.getString(address, StandardCharsets.UTF_8);
    }

    private static MethodHandle standardFunction(String symbol, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(LINKER.defaultLookup().find(symbol).get(), descriptor);
    }

    private static MethodHandle segmentAtHandle() {
        try {
            return MethodHandles.lookup().findStatic(MemorySegment.class, "ofAddress",
                    MethodType.methodType(MemorySegment.class, long.class));
        } catch (ReflectiveOperationException unreachable) {
            throw new AssertionError(unreachable);
        }
    }

    private static MethodHandle addressOfHandle() {
        try {
            return MethodHandles.lookup().findVirtual(MemorySegment.class, "address",
                    MethodType.methodType(long.class));
        } catch (ReflectiveOperationException unreachable) {
            throw new AssertionError(unreachable);
        }
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
        private final Arena arena;
        private final SymbolLookup lookup;

        FfmLibrary(Arena arena, SymbolLookup lookup) {
            this.arena = arena;
            this.lookup = lookup;
        }

        @Override
        public boolean has(String symbol) {
            return lookup.find(symbol).isPresent();
        }

        @Override
        public NativeFunction function(String symbol, NativeSignature signature) {
            Optional<MemorySegment> found = lookup.find(symbol);
            if (found.isEmpty()) {
                throw new IllegalArgumentException("the library exports no function " + symbol);
            }
            return new FfmFunction(spreading(LINKER.downcallHandle(found.get(), descriptorOf(signature)), signature));
        }

        @Override
        public void close() {
            arena.close();
        }

        /**
         * Adapts a downcall handle to take every argument from one {@code long[]} and answer a {@code long}: a pointer
         * made from its address, an {@code int} narrowed, an {@code int} answer widened, a {@code void} answered 0.
         */
        private static MethodHandle spreading(MethodHandle downcall, NativeSignature signature) {
            MethodHandle adapted = downcall;
            List<NativeType> parameters = signature.parameters();
            for (int index = 0; index < parameters.size(); index++) {
                if (parameters.get(index) == NativeType.ADDRESS) {
                    adapted = MethodHandles.filterArguments(adapted, index, SEGMENT_AT);
                }
            }
            if (signature.returned() == NativeType.ADDRESS) {
                adapted = MethodHandles.filterReturnValue(adapted, ADDRESS_OF);
            }
            Class<?>[] longs = new Class<?>[parameters.size()];
            Arrays.fill(longs, long.class);
            adapted = MethodHandles.explicitCastArguments(adapted, MethodType.methodType(long.class, longs));
            return adapted.asSpreader(long[].class, parameters.size());
        }

        private static FunctionDescriptor descriptorOf(NativeSignature signature) {
            MemoryLayout[] parameters = signature.parameters().stream().map(FfmLibrary::layoutOf)
                    .toArray(MemoryLayout[]::new);
            return signature.returned() == NativeType.VOID
                    ? FunctionDescriptor.ofVoid(parameters)
                    : FunctionDescriptor.of(layoutOf(signature.returned()), parameters);
        }

        private static MemoryLayout layoutOf(NativeType type) {
            return switch (type) {
                case INT -> JAVA_INT;
                case LONG -> JAVA_LONG;
                case ADDRESS -> ADDRESS;
                case VOID -> throw new IllegalArgumentException("void is no parameter");
            };
        }
    }

    private static final class FfmFunction implements NativeFunction {
        private final MethodHandle handle;

        FfmFunction(MethodHandle handle) {
            this.handle = handle;
        }

        @Override
        public long call(long... arguments) {
            try {
                return (long) handle.invokeExact(arguments);
            } catch (Throwable thrown) {
                throw rethrown(thrown);
            }
        }
    }
}
