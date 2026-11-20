package com.weivel.core;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** The native seam through JNA, for Java 8 to 21: functions called with primitive arguments, no structure mapped. */
final class JnaAccess implements NativeAccess {
    static final JnaAccess INSTANCE = new JnaAccess();

    private static final String DISTINCT_LOAD_OPTION = "weivel.distinct-load";
    /**
     * The pointer every access of memory goes through, at the address as its offset.
     *
     * @implNote JNA hands the pointer itself to its natives, so it always escapes, and a pointer made per access is a
     * heap allocation per access; at address zero, the offset is the address.
     */
    private static final Pointer ABSOLUTE = new Pointer(0);

    private final AtomicLong loads = new AtomicLong();
    private volatile Object reachable;

    private JnaAccess() {
    }

    @Override
    public String name() {
        return "jna";
    }

    @Override
    public long addressSize() {
        return Native.POINTER_SIZE;
    }

    @Override
    public String toString() {
        return name();
    }

    /**
     * @implNote JNA keeps one {@link NativeLibrary} per name and options, so each open names itself in an option no
     * JNA code reads: two opens of one path then hold the library twice, and closing one leaves the other loaded.
     */
    @Override
    public OpenLibrary open(Path path) {
        Map<String, Object> options = new HashMap<>();
        options.put(DISTINCT_LOAD_OPTION, loads.incrementAndGet());
        try {
            return new JnaLibrary(path, NativeLibrary.getInstance(path.toString(), options));
        } catch (UnsatisfiedLinkError notALibrary) {
            throw new IllegalArgumentException(String.valueOf(notALibrary.getMessage()), notALibrary);
        }
    }

    @Override
    public long allocate(long size) {
        long address = Native.malloc(Math.max(1, size));
        if (address == 0) {
            throw new OutOfMemoryError("no native memory for " + size + " bytes");
        }
        ABSOLUTE.setMemory(address, Math.max(1, size), (byte) 0);
        return address;
    }

    @Override
    public void free(long address) {
        Native.free(address);
    }

    @Override
    public int getInt(long address, long offset) {
        return ABSOLUTE.getInt(address + offset);
    }

    @Override
    public void putInt(long address, long offset, int value) {
        ABSOLUTE.setInt(address + offset, value);
    }

    @Override
    public long getLong(long address, long offset) {
        return ABSOLUTE.getLong(address + offset);
    }

    @Override
    public void putLong(long address, long offset, long value) {
        ABSOLUTE.setLong(address + offset, value);
    }

    @Override
    public double getDouble(long address, long offset) {
        return ABSOLUTE.getDouble(address + offset);
    }

    @Override
    public void putDouble(long address, long offset, double value) {
        ABSOLUTE.setDouble(address + offset, value);
    }

    @Override
    public byte[] readBytes(long address, long offset, int length) {
        return ABSOLUTE.getByteArray(address + offset, length);
    }

    @Override
    public void writeBytes(long address, long offset, byte[] bytes) {
        ABSOLUTE.write(address + offset, bytes, 0, bytes.length);
    }

    @Override
    public void clear(long address, long offset, long length) {
        if (length < 0) {
            throw new IndexOutOfBoundsException("a clear of " + length + " bytes");
        }
        ABSOLUTE.setMemory(address + offset, length, (byte) 0);
    }

    @Override
    public String readText(long address) {
        return ABSOLUTE.getString(address, StandardCharsets.UTF_8.name());
    }

    @Override
    public long addressOf(ByteBuffer direct) {
        if (!direct.isDirect()) {
            throw new IllegalArgumentException("a heap buffer has no native address");
        }
        return Pointer.nativeValue(Native.getDirectBufferPointer(direct)) + direct.position();
    }

    @Override
    public void writeBytes(long address, long offset, byte[] bytes, int from, int length) {
        ABSOLUTE.write(address + offset, bytes, from, length);
    }

    /**
     * @implNote Java 8 has no {@code Reference.reachabilityFence}, so the object is written to a volatile field,
     * which no compiler may drop or move before the native call, and the field is cleared again.
     */
    @Override
    public void keepReachable(Object object) {
        reachable = object;
        reachable = null;
    }

    @Override
    public Object addressed(long address, long size) {
        return new Pointer(address);
    }

    @Override
    public long addressOf(Object addressed) {
        if (!(addressed instanceof Pointer)) {
            throw new IllegalArgumentException("a " + addressed.getClass().getName() + " carries no native address");
        }
        return Pointer.nativeValue((Pointer) addressed);
    }

    @Override
    public NativeFunction function(long address, NativeSignature signature) {
        return new JnaFunction(null, Function.getFunction(new Pointer(address)), signature);
    }

    @Override
    public FfiStruct.Placement layOut(FfiStruct struct) {
        throw new DescriptionsNotBound("a described definition is not yet bound on JNA: this backend lays out no "
                + "described struct, so " + struct.name() + " cannot be checked against its description");
    }

    /**
     * A library JNA opened, which this object alone holds and closes.
     *
     * @implNote {@code calls} counts the calls under way, and is -1 once closed, so a call never reaches a library
     * already unloaded and a close never unloads one a call is still in.
     */
    static final class JnaLibrary implements OpenLibrary {
        private static final int CLOSED = -1;

        private final Path path;
        private final NativeLibrary library;
        private final AtomicInteger calls = new AtomicInteger();

        JnaLibrary(Path path, NativeLibrary library) {
            this.path = path;
            this.library = library;
        }

        @Override
        public boolean has(String symbol) {
            requireOpen();
            try {
                library.getFunction(symbol);
                return true;
            } catch (UnsatisfiedLinkError missing) {
                return false;
            }
        }

        @Override
        public NativeFunction function(String symbol, NativeSignature signature) {
            requireOpen();
            Function function;
            try {
                function = library.getFunction(symbol);
            } catch (UnsatisfiedLinkError missing) {
                throw new IllegalArgumentException("the library exports no function " + symbol, missing);
            }
            return new JnaFunction(this, function, signature);
        }

        private void requireOpen() {
            if (calls.get() == CLOSED) {
                throw new IllegalStateException("the library " + path + " is closed");
            }
        }

        void enter() {
            int current;
            do {
                current = calls.get();
                if (current == CLOSED) {
                    throw new IllegalStateException("the library " + path + " is closed");
                }
            } while (!calls.compareAndSet(current, current + 1));
        }

        void leave() {
            calls.decrementAndGet();
        }

        @Override
        public void close() {
            if (!calls.compareAndSet(0, CLOSED)) {
                throw new IllegalStateException(calls.get() == CLOSED
                        ? "the library " + path + " is closed already"
                        : "a call into the library " + path + " is under way");
            }
            library.close();
        }
    }

    /** A function JNA calls, counted into and out of its library; a function pointer has no library to count in. */
    private static final class JnaFunction implements NativeFunction {
        private final JnaLibrary library;
        private final Function function;
        private final NativeSignature signature;

        JnaFunction(JnaLibrary library, Function function, NativeSignature signature) {
            this.library = library;
            this.function = function;
            this.signature = signature;
        }

        @Override
        public long call(long... arguments) {
            Object[] boxed = boxed(arguments);
            if (library == null) {
                return answered(boxed);
            }
            library.enter();
            try {
                return answered(boxed);
            } finally {
                library.leave();
            }
        }

        private Object[] boxed(long[] arguments) {
            List<NativeType> parameters = signature.parameters();
            if (arguments.length != parameters.size()) {
                throw new IllegalArgumentException("a function of " + parameters.size() + " parameters was handed "
                        + arguments.length + " arguments");
            }
            Object[] boxed = new Object[arguments.length];
            for (int index = 0; index < arguments.length; index++) {
                boxed[index] = boxed(parameters.get(index), arguments[index]);
            }
            return boxed;
        }

        private static Object boxed(NativeType type, long argument) {
            if (type == NativeType.INT) {
                return Integer.valueOf((int) argument);
            }
            if (type == NativeType.DOUBLE) {
                return Double.valueOf(Double.longBitsToDouble(argument));
            }
            return Long.valueOf(argument);
        }

        private long answered(Object[] arguments) {
            switch (signature.returned()) {
                case INT:
                    return function.invokeInt(arguments);
                case LONG:
                    return function.invokeLong(arguments);
                case ADDRESS:
                    return Pointer.nativeValue(function.invokePointer(arguments));
                case DOUBLE:
                    return Double.doubleToRawLongBits(function.invokeDouble(arguments));
                case VOID:
                    function.invokeVoid(arguments);
                    return 0;
                default:
                    throw new IllegalStateException("a return type the seam does not know: " + signature.returned());
            }
        }
    }
}
