package com.weivel.core;

import static com.weivel.core.CoreProbes.ok;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A C library's bytes functions through the native seam, against the core's echo library. */
class LibraryFunctionTest {
    @Test
    void aHeapArrayIsCopiedOnceAndTheCopyIsWhatTheLibraryIsHanded() {
        RecordedCopies copies = new RecordedCopies();
        try (LibraryBinary echo = ok(LibraryBinary.open(NativeFiles.echoBinary(), "echo_release"))) {
            long seen = addressIn(ok(echo.function("echo_where", copies)).call(new byte[16]));
            byte[] echoed = ok(echo.function("echo_echo", copies)).call(bytes("copied")).body();

            assertEquals(2, copies.inputs.size());
            assertEquals(copies.inputs.get(0).longValue(), seen);
            assertArrayEquals(bytes("copied"), echoed);
        }
    }

    @Test
    void aDirectBufferIsLentAtTheBackendsOwnAddressOfIt() {
        RecordedCopies copies = new RecordedCopies();
        try (LibraryBinary echo = ok(LibraryBinary.open(NativeFiles.echoBinary(), "echo_release"))) {
            ByteBuffer input = ByteBuffer.allocateDirect(32);
            input.position(8);

            long seen = addressIn(ok(echo.function("echo_where", copies)).call(input));

            assertEquals(NativeBackends.current().addressOf(input), seen);
            assertEquals(8, input.position());
            assertEquals(Collections.emptyList(), copies.inputs);
        }
    }

    @Test
    void aDirectBufferLentFromAPositionHandsTheLibraryExactlyTheBytesFromThere() {
        try (LibraryBinary echo = ok(LibraryBinary.open(NativeFiles.echoBinary(), "echo_release"))) {
            ByteBuffer input = ByteBuffer.allocateDirect(16);
            input.put(bytes("skipped-"));
            input.put(bytes("lent"));
            input.flip();
            input.position(8);

            byte[] echoed = ok(echo.function("echo_echo", new RecordedCopies())).call(input).body();

            assertArrayEquals(bytes("lent"), echoed);
        }
    }

    @Test
    void aBodyWhoseCopyFailsIsStillReleasedExactlyOnce() {
        RecordedCopies copies = new RecordedCopies();
        try (LibraryBinary echo = ok(LibraryBinary.open(NativeFiles.echoBinary(), "echo_release"))) {
            LibraryFunction released = ok(echo.function("echo_released", copies));
            LibraryFunction echoed = ok(echo.function("echo_echo", copies));
            long before = addressIn(released.call(new byte[0]));

            copies.failTheNextBodyCopy = true;
            assertThrows(IllegalStateException.class, () -> echoed.call(bytes("lost")));
            long after = addressIn(released.call(new byte[0]));

            assertEquals(2, after - before, "the first reading's body and the failed copy's, each released once");
        }
    }

    private static long addressIn(LibraryOutput output) {
        assertEquals(0, output.status());
        return ByteBuffer.wrap(output.body()).order(ByteOrder.nativeOrder()).getLong();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static final class RecordedCopies implements LibraryCopies {
        final List<Long> inputs = new ArrayList<>();
        boolean failTheNextBodyCopy;

        @Override
        public void inputCopied(long address, int length) {
            inputs.add(address);
        }

        @Override
        public void beforeTheBodyIsCopied() {
            if (failTheNextBodyCopy) {
                failTheNextBodyCopy = false;
                throw new IllegalStateException("the copy failed, as a test makes it");
            }
        }
    }
}
