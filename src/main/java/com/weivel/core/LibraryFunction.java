package com.weivel.core;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * One function of a {@link LibraryBinary}, of the standard's bytes kind: the caller's bytes in, lent for the length of
 * the call wherever Java can lend them, and the library's body out, copied once into a Java array and handed to the
 * library's release symbol before the call answers. Any thread may call, while the binary is open.
 */
public final class LibraryFunction {
    private static final int LARGEST_BODY = Integer.MAX_VALUE - 8;

    private final NativeAccess access;
    private final NativeBytesFunction function;
    private final NativeFunction release;

    LibraryFunction(NativeAccess access, NativeBytesFunction function, NativeFunction release) {
        this.access = access;
        this.function = function;
        this.release = release;
    }

    /**
     * Calls the function with a heap array: lent in place on the foreign function API, copied once into native memory
     * for the call on JNA, which can lend C only native memory.
     *
     * @param input the bytes, never changed
     * @return the function's status and body
     * @throws IllegalStateException when the binary is closed, or the body is longer than a Java array holds, which is
     *     released all the same
     */
    public LibraryOutput call(byte[] input) {
        Objects.requireNonNull(input, "input");
        return call(input, 0, input.length);
    }

    /**
     * Calls the function with a buffer's remaining bytes: a direct buffer's lent in place on every backend, an array's
     * as {@link #call(byte[])} lends them, and a read-only heap buffer's, whose array Java does not hand out, copied
     * once. The buffer's position and limit are left as they were.
     *
     * @param input the bytes, never changed
     * @return the function's status and body
     * @throws IllegalStateException as {@link #call(byte[])} throws it
     */
    public LibraryOutput call(ByteBuffer input) {
        Objects.requireNonNull(input, "input");
        if (input.isDirect()) {
            long address = access.addressOf(input);
            return answered((output, outputLength) -> function.call(address, input.remaining(), output,
                    outputLength));
        }
        if (input.hasArray()) {
            return call(input.array(), input.arrayOffset() + input.position(), input.remaining());
        }
        byte[] copy = new byte[input.remaining()];
        input.duplicate().get(copy);
        return call(copy);
    }

    private LibraryOutput call(byte[] input, int offset, int length) {
        return answered((output, outputLength) -> function.call(input, offset, length, output, outputLength));
    }

    private LibraryOutput answered(Crossing crossing) {
        try (NativeArena arena = new NativeArena(access)) {
            NativeRegion output = arena.allocate(Layout.LONG);
            NativeRegion outputLength = arena.allocate(Layout.LONG);
            int status = crossing.cross(output.address(), outputLength.address());
            return new LibraryOutput(status, copiedThenReleased(output.getAddress(0), outputLength.getLong(0)));
        }
    }

    private byte[] copiedThenReleased(long body, long length) {
        if (body == 0) {
            return new byte[0];
        }
        try {
            if (length < 0 || length > LARGEST_BODY) {
                throw new IllegalStateException("the library answered " + Long.toUnsignedString(length)
                        + " bytes, more than a Java array holds");
            }
            return access.readBytes(body, 0, (int) length);
        } finally {
            release.call(body);
        }
    }

    @FunctionalInterface
    private interface Crossing {
        int cross(long output, long outputLength);
    }
}
