package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The native seam, held to the same sentences on every backend this Java can run. */
class NativeAccessTest {
    private static final int ECHO_OK = 0;
    private static final NativeSignature ECHO = NativeSignature.of(NativeType.INT, NativeType.ADDRESS, NativeType.LONG,
            NativeType.ADDRESS, NativeType.ADDRESS);
    private static final NativeSignature RELEASE = NativeSignature.of(NativeType.VOID, NativeType.ADDRESS);

    static Stream<NativeAccess> backends() {
        List<NativeAccess> backends = new ArrayList<>();
        backends.add(NativeBackends.current());
        if (NativeBackends.current() != JnaAccess.INSTANCE) {
            backends.add(JnaAccess.INSTANCE);
        }
        return backends.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void aFunctionIsCalledWithAddressesAndLengthsAndAnswersItsStatus(NativeAccess access) {
        byte[] input = "echo me".getBytes(StandardCharsets.UTF_8);
        try (OpenLibrary echo = access.open(NativeFiles.echoBinary())) {
            long buffer = access.allocate(input.length);
            long outputSlot = access.allocate(8);
            long lengthSlot = access.allocate(8);
            try {
                access.writeBytes(buffer, 0, input);

                long status = echo.function("echo_echo", ECHO).call(buffer, input.length, outputSlot, lengthSlot);

                assertEquals(ECHO_OK, status);
                long output = access.getLong(outputSlot, 0);
                assertNotEquals(0, output);
                assertEquals(input.length, access.getLong(lengthSlot, 0));
                assertArrayEquals(input, access.readBytes(output, 0, input.length));
                assertEquals(0, echo.function("echo_release", RELEASE).call(output));
            } finally {
                access.free(buffer);
                access.free(outputSlot);
                access.free(lengthSlot);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void aMissingSymbolFailsNamingIt(NativeAccess access) {
        try (OpenLibrary echo = access.open(NativeFiles.echoBinary())) {
            assertTrue(echo.has("echo_echo"));
            assertFalse(echo.has("echo_nothing"));

            IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                    () -> echo.function("echo_nothing", RELEASE));

            assertTrue(missing.getMessage().contains("echo_nothing"), missing.getMessage());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void aFileThatIsNoLibraryFailsToOpen(NativeAccess access) {
        assertThrows(IllegalArgumentException.class, () -> access.open(NativeFiles.coreHeader()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void aFunctionOfAClosedLibraryRefusesToBeCalledAndASecondCloseIsRefused(NativeAccess access) {
        OpenLibrary echo = access.open(NativeFiles.echoBinary());
        NativeFunction release = echo.function("echo_release", RELEASE);

        echo.close();

        assertThrows(IllegalStateException.class, () -> release.call(0));
        assertThrows(IllegalStateException.class, echo::close);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void aCallWithTheWrongNumberOfArgumentsIsRefused(NativeAccess access) {
        try (OpenLibrary echo = access.open(NativeFiles.echoBinary())) {
            NativeFunction release = echo.function("echo_release", RELEASE);

            assertThrows(IllegalArgumentException.class, () -> release.call(0, 0));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void primitivesWrittenAtAnOffsetReadBackAndAllocationStartsZeroed(NativeAccess access) {
        long address = access.allocate(32);
        try {
            assertEquals(0, access.getLong(address, 0));
            assertEquals(0, access.getLong(address, 24));

            access.putInt(address, 4, -7);
            access.putLong(address, 8, Long.MIN_VALUE + 3);
            access.putDouble(address, 16, 2.5);

            assertEquals(-7, access.getInt(address, 4));
            assertEquals(Long.MIN_VALUE + 3, access.getLong(address, 8));
            assertEquals(2.5, access.getDouble(address, 16));
            access.clear(address, 8, 8);
            assertEquals(0, access.getLong(address, 8));
            assertEquals(-7, access.getInt(address, 4));
        } finally {
            access.free(address);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void textIsReadAsUtf8UpToItsTerminator(NativeAccess access) {
        byte[] encoded = "grüße\u0000".getBytes(StandardCharsets.UTF_8);
        long address = access.allocate(encoded.length + 4);
        try {
            access.writeBytes(address, 2, encoded);

            assertEquals("grüße", access.readText(address + 2));
        } finally {
            access.free(address);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void anArenasRegionsAreFreedAndRefuseAccessOnceItCloses(NativeAccess access) {
        NativeArena arena = new NativeArena(access);
        NativeRegion region = arena.allocate(16);
        NativeRegion text = arena.text("named");
        region.putLong(8, 41);

        assertEquals(41, region.getLong(8));
        assertEquals("named", access.readText(text.address()));
        assertThrows(IndexOutOfBoundsException.class, () -> region.getLong(12));
        arena.close();

        assertFalse(region.isAlive());
        assertThrows(IllegalStateException.class, () -> region.getLong(8));
        assertThrows(IllegalStateException.class, arena::close);
    }
}
