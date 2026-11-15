package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void refusesAResetOfAnArenaThatIsNotReusable() {
        try (NativeArena arena = new NativeArena(access)) {
            assertThrows(IllegalStateException.class, arena::reset);
        }
    }
}
