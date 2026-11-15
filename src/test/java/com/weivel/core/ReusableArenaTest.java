package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The wrapper's scratch memory: reused after each reset, grown to what one use asked, zeroed as it is handed out. */
class ReusableArenaTest {
    private final CountingAccess access = new CountingAccess();

    @Test
    void growsAtTheResetToHoldWhatOneUseAskedAndAllocatesNothingAfter() {
        NativeArena arena = NativeArena.reusable(access);
        arena.allocate(200);
        arena.allocate(200);
        arena.reset();
        long afterGrowing = access.allocations();

        for (int use = 0; use < 10; use++) {
            arena.allocate(200);
            arena.allocate(200);
            arena.reset();
        }

        assertEquals(afterGrowing, access.allocations());
        arena.close();
        assertEquals(access.allocations(), access.frees());
    }

    @Test
    void zeroesWhatItHandsOutAgain() {
        NativeArena arena = NativeArena.reusable(access);
        arena.allocate(Layout.LONG).putLong(0, 42);
        arena.reset();

        assertEquals(0, arena.allocate(Layout.LONG).getLong(0));
        arena.close();
    }

    @Test
    void writesEachTextAsStringEncodesItInUtf8() {
        NativeArena arena = NativeArena.reusable(access);
        String[] texts = {"provider:arithmetic", "", "café", "€中", "😀 pair",
                "lone \ud83d high", "lone \ude00 low", "high at the end \ud83d", "\ude00\ud83d", "a"};
        for (String text : texts) {
            byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
            byte[] terminated = Arrays.copyOf(encoded, encoded.length + 1);

            NativeRegion region = arena.text(text);

            assertArrayEquals(terminated, access.readBytes(region.address(), 0, terminated.length), text);
            arena.reset();
        }
        arena.close();
    }

    @Test
    void holdsNoTextItEncodedOnceClosed() throws InterruptedException {
        NativeArena arena = NativeArena.reusable(access);
        WeakReference<String> text = encodedOnce(arena);

        arena.close();

        assertTrue(TestCores.collected(text), "the closed arena still held the last text it encoded");
    }

    private static WeakReference<String> encodedOnce(NativeArena arena) {
        String text = new String("forgotten".toCharArray());
        arena.text(text);
        arena.reset();
        return new WeakReference<>(text);
    }

    @Test
    void refusesAResetOfAnArenaThatIsNotReusable() {
        try (NativeArena arena = new NativeArena(access)) {
            assertThrows(IllegalStateException.class, arena::reset);
        }
    }
}
