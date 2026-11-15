package com.weivel.core;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * One function of a {@link LibraryBinary}, of the standard's bytes kind: the caller's bytes in, and the library's
 * body out, copied once into a Java array and handed to the library's release symbol before the call answers. A
 * direct buffer is lent to the library in place; a heap array is copied once into native memory for the call. Any
 * thread may call, while the binary is open.
 *
 * @implNote The heap is never lent: the foreign function API lends it only through a critical downcall, which holds
 * off garbage collection for the whole C call, and nothing bounds how long a bytes function runs (rev 195).
 */
public final class LibraryFunction {
    private static final int LARGEST_BODY = Integer.MAX_VALUE - 8;
    /** The body's address and its length, laid out ahead of a copied input in the call's one block. */
    private static final long ANSWER_CELLS = Layout.ADDRESS.byteSize() + Layout.LONG.byteSize();

    private final NativeAccess access;
    private final NativeFunction function;
    private final NativeFunction release;
    private final SpareBlock spare;
    private final LibraryCopies copies;

    LibraryFunction(NativeAccess access, NativeFunction function, NativeFunction release, SpareBlock spare,
                    LibraryCopies copies) {
        this.access = access;
        this.function = function;
        this.release = release;
        this.spare = spare;
        this.copies = copies;
    }

    /**
     * Calls the function with a heap array, copied once into native memory for the call.
     *
     * @param input the bytes, never changed
     * @return the function's status and body
     * @throws IllegalStateException when the binary is closed, or the body is longer than a Java array holds, which is
     *     released all the same
     */
    public LibraryOutput call(byte[] input) {
        Objects.requireNonNull(input, "input");
        return copied(input, 0, input.length);
    }

    /**
     * Calls the function with a buffer's remaining bytes: a direct buffer's lent in place, a heap buffer's copied once
     * as {@link #call(byte[])} copies them. The buffer's position and limit are left as they were.
     *
     * @param input the bytes, never changed
     * @return the function's status and body
     * @throws IllegalStateException as {@link #call(byte[])} throws it
     */
    public LibraryOutput call(ByteBuffer input) {
        Objects.requireNonNull(input, "input");
        if (input.isDirect()) {
            return lent(input);
        }
        if (input.hasArray()) {
            return copied(input.array(), input.arrayOffset() + input.position(), input.remaining());
        }
        byte[] bytes = new byte[input.remaining()];
        input.duplicate().get(bytes);
        return copied(bytes, 0, bytes.length);
    }

    private LibraryOutput lent(ByteBuffer direct) {
        long cells = spare.take(ANSWER_CELLS);
        try {
            return answered(cells, access.addressOf(direct), direct.remaining());
        } finally {
            spare.give(cells, ANSWER_CELLS);
            access.keepReachable(direct);
        }
    }

    private LibraryOutput copied(byte[] input, int offset, int length) {
        long size = ANSWER_CELLS + length;
        long cells = spare.take(size);
        try {
            long copy = cells + ANSWER_CELLS;
            access.writeBytes(copy, 0, input, offset, length);
            copies.inputCopied(copy, length);
            return answered(cells, copy, length);
        } finally {
            spare.give(cells, size);
        }
    }

    private LibraryOutput answered(long cells, long input, long inputLength) {
        long outputLength = cells + Layout.ADDRESS.byteSize();
        int status = (int) function.call(input, inputLength, cells, outputLength);
        return new LibraryOutput(status, copiedThenReleased(access.getLong(cells, 0), access.getLong(outputLength, 0)));
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
            copies.beforeTheBodyIsCopied();
            return access.readBytes(body, 0, (int) length);
        } finally {
            release.call(body);
        }
    }
}
