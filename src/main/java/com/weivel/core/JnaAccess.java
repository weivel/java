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

    private final AtomicLong loads = new AtomicLong();

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
        new Pointer(address).setMemory(0, Math.max(1, size), (byte) 0);
        return address;
    }

    @Override
    public void free(long address) {
        Native.free(address);
    }

    @Override
    public int getInt(long address, long offset) {
        return new Pointer(address).getInt(offset);
    }

    @Override
    public void putInt(long address, long offset, int value) {
        new Pointer(address).setInt(offset, value);
    }

    @Override
    public long getLong(long address, long offset) {
        return new Pointer(address).getLong(offset);
    }

    @Override
    public void putLong(long address, long offset, long value) {
        new Pointer(address).setLong(offset, value);
    }

    @Override
    public double getDouble(long address, long offset) {
        return new Pointer(address).getDouble(offset);
    }

    @Override
    public void putDouble(long address, long offset, double value) {
        new Pointer(address).setDouble(offset, value);
    }

    @Override
    public byte[] readBytes(long address, long offset, int length) {
        return new Pointer(address).getByteArray(offset, length);
    }

    @Override
    public void writeBytes(long address, long offset, byte[] bytes) {
        new Pointer(address).write(offset, bytes, 0, bytes.length);
    }

    @Override
    public void clear(long address, long offset, long length) {
        new Pointer(address).setMemory(offset, length, (byte) 0);
    }

    @Override
    public String readText(long address) {
        return new Pointer(address).getString(0, StandardCharsets.UTF_8.name());
    }

    @Override
    public long addressOf(ByteBuffer direct) {
        if (!direct.isDirect()) {
            throw new IllegalArgumentException("a heap buffer has no native address");
        }
        return Pointer.nativeValue(Native.getDirectBufferPointer(direct)) + direct.position();
    }

    /** @implNote JNA hands C a pointer only to native memory, so a heap array is copied for each call. */
    @Override
    public boolean lendsTheHeap() {
        return false;
    }

    /**
     * A library JNA opened, which this object alone holds and closes.
     *
     * @implNote {@code calls} counts the calls under way, and is -1 once closed, so a call never reaches a library
     * already unloaded and a close never unloads one a call is still in.
     */
    static final class JnaLibrary implements OpenLibrary {
        private static final int CLOSED = -1;
        private static final NativeSignature BYTES_SIGNATURE = NativeSignature.of(NativeType.INT, NativeType.ADDRESS,
                NativeType.LONG, NativeType.ADDRESS, NativeType.ADDRESS);

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

        @Override
        public NativeBytesFunction bytesFunction(String symbol) {
            return new JnaBytesFunction(function(symbol, BYTES_SIGNATURE));
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
                boxed[index] = parameters.get(index) == NativeType.INT
                        ? Integer.valueOf((int) arguments[index])
                        : Long.valueOf(arguments[index]);
            }
            return boxed;
        }

        private long answered(Object[] arguments) {
            switch (signature.returned()) {
                case INT:
                    return function.invokeInt(arguments);
                case LONG:
                    return function.invokeLong(arguments);
                case ADDRESS:
                    return Pointer.nativeValue(function.invokePointer(arguments));
                case VOID:
                    function.invokeVoid(arguments);
                    return 0;
                default:
                    throw new IllegalStateException("a return type the seam does not know: " + signature.returned());
            }
        }
    }

    /** A bytes function whose heap input is copied into native memory freed once the call returns. */
    private static final class JnaBytesFunction implements NativeBytesFunction {
        private final NativeFunction function;

        JnaBytesFunction(NativeFunction function) {
            this.function = function;
        }

        @Override
        public int call(long input, long inputLength, long output, long outputLength) {
            return (int) function.call(input, inputLength, output, outputLength);
        }

        @Override
        public int call(byte[] input, int offset, int length, long output, long outputLength) {
            long copy = INSTANCE.allocate(length);
            try {
                new Pointer(copy).write(0, input, offset, length);
                return (int) function.call(copy, length, output, outputLength);
            } finally {
                INSTANCE.free(copy);
            }
        }
    }
}
