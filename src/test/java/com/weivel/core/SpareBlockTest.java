package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** The one spare block a library's calls share: never handed to two calls at once, and freed with the library. */
class SpareBlockTest {
    private final CountingAccess access = new CountingAccess();
    private final SpareBlock spare = new SpareBlock(access);

    @Test
    void handsTheSpareToOneCallAtATimeAndAllocatesForTheOthers() {
        spare.give(spare.take(16), 16);

        long first = spare.take(16);
        long second = spare.take(16);

        assertNotEquals(first, second);
        spare.give(first, 16);
        spare.give(second, 16);
        assertEquals(2, access.allocations());
        assertEquals(1, access.frees());
        assertEquals(first, spare.take(16));
        assertEquals(2, access.allocations());
    }

    @Test
    void zeroesWhatItHandsOutAgain() {
        long block = spare.take(16);
        access.putLong(block, 8, 42);
        spare.give(block, 16);

        assertEquals(0, access.getLong(spare.take(16), 8));
    }

    @Test
    void keepsNoBlockLargerThanItsSize() {
        long large = spare.take(SpareBlock.SIZE + 1);
        spare.give(large, SpareBlock.SIZE + 1);

        assertEquals(1, access.frees());
        spare.take(16);
        assertEquals(2, access.allocations());
    }

    @Test
    void freesTheSpareAtCloseAndEveryBlockGivenBackAfterIt() {
        long kept = spare.take(16);
        long held = spare.take(16);
        spare.give(kept, 16);

        spare.close();
        spare.give(held, 16);

        assertEquals(access.allocations(), access.frees());
    }
}
