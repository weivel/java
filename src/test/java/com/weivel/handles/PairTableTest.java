package com.weivel.handles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The hash table keyed by a pair of handles, on its own. */
class PairTableTest {
    private static final int HALF_OF_THE_SMALLEST_CAPACITY = 8;

    @Test
    void aTableThatRunsOutOfMemoryWhileGrowingKeepsEveryValueItHeld() {
        RunningOutMemory memory = new RunningOutMemory();
        PairTable<String> table = new PairTable<>(memory);
        table.ensureRoom(HALF_OF_THE_SMALLEST_CAPACITY);
        for (long first = 1; first <= HALF_OF_THE_SMALLEST_CAPACITY; first++) {
            table.put(first, first * 10, "value-" + first);
        }

        memory.runOutAfter(1);
        assertThrows(OutOfMemoryError.class, () -> table.ensureRoom(HALF_OF_THE_SMALLEST_CAPACITY + 1));
        memory.neverRunOut();

        for (long first = 1; first <= HALF_OF_THE_SMALLEST_CAPACITY; first++) {
            assertEquals("value-" + first, table.get(first, first * 10));
        }
        table.ensureRoom(HALF_OF_THE_SMALLEST_CAPACITY + 1);
        table.put(99, 990, "value-99");
        assertEquals("value-99", table.get(99, 990));
        assertEquals("value-1", table.remove(1, 10));
        assertEquals(HALF_OF_THE_SMALLEST_CAPACITY, table.size());
    }

    @Test
    void aPairAndTheSamePairSwappedHashApart() {
        int apart = 0;
        for (long first = 1; first <= 1000; first++) {
            long second = first * 7919 + 1;
            if (PairTable.hash(first, second) != PairTable.hash(second, first)) {
                apart++;
            }
        }

        assertTrue(apart > 990, apart + " of 1000 swapped pairs hashed apart");
    }
}
