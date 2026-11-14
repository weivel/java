package com.weivel.handles;

import java.util.ArrayList;
import java.util.List;

/**
 * A hash table keyed by a pair of {@code long}s, with open addressing and linear probing, so a lookup boxes nothing
 * and allocates nothing. A slot is empty when its value is null, and a removal shifts the entries behind it back, so no
 * slot is ever marked dead. Room is taken ahead by {@link #ensureRoom}; {@link #put} never grows the table.
 *
 * @param <V> the values, never null
 * @implNote Not thread-safe: {@link Handles} calls it only under its own lock.
 */
final class PairTable<V> {
    private static final int SMALLEST_CAPACITY = 16;
    private static final int LARGEST_COUNT = 1 << 29;

    private final TableMemory memory;
    private long[] firsts;
    private long[] seconds;
    private Object[] values;
    private int size;

    PairTable() {
        this(TableMemory.HEAP);
    }

    PairTable(TableMemory memory) {
        this.memory = memory;
        firsts = memory.longs(SMALLEST_CAPACITY);
        seconds = memory.longs(SMALLEST_CAPACITY);
        values = memory.objects(SMALLEST_CAPACITY);
    }

    int size() {
        return size;
    }

    /**
     * Grows the table now when holding {@code count} values would fill it past half.
     *
     * @throws OutOfMemoryError when the room cannot be had, the table left as it was
     */
    void ensureRoom(int count) {
        if ((long) count * 2 <= values.length) {
            return;
        }
        if (count > LARGEST_COUNT) {
            throw new OutOfMemoryError("a table of the layer cannot hold " + count + " values");
        }
        int capacity = values.length;
        while ((long) count * 2 > capacity) {
            capacity *= 2;
        }
        rehash(capacity);
    }

    @SuppressWarnings("unchecked")
    V get(long first, long second) {
        int slot = slotOf(first, second);
        return slot < 0 ? null : (V) values[slot];
    }

    /**
     * Puts a value in room {@link #ensureRoom} took: answers the value it replaced, or null.
     *
     * @throws IllegalStateException when no room was taken for it
     */
    @SuppressWarnings("unchecked")
    V put(long first, long second, V value) {
        int mask = values.length - 1;
        int slot = hash(first, second) & mask;
        while (values[slot] != null) {
            if (firsts[slot] == first && seconds[slot] == second) {
                V replaced = (V) values[slot];
                values[slot] = value;
                return replaced;
            }
            slot = (slot + 1) & mask;
        }
        if ((long) (size + 1) * 2 > values.length) {
            throw new IllegalStateException("a defect of the layer: a value was put in a table that took no room");
        }
        firsts[slot] = first;
        seconds[slot] = second;
        values[slot] = value;
        size++;
        return null;
    }

    @SuppressWarnings("unchecked")
    V remove(long first, long second) {
        int slot = slotOf(first, second);
        if (slot < 0) {
            return null;
        }
        V removed = (V) values[slot];
        shiftBackInto(slot);
        size--;
        return removed;
    }

    /** Removes every value {@code matches} accepts, and answers them. */
    @SuppressWarnings("unchecked")
    List<V> removeAll(Match<V> matches) {
        List<long[]> keys = new ArrayList<>();
        for (int slot = 0; slot < values.length; slot++) {
            if (values[slot] != null && matches.matches(firsts[slot], seconds[slot], (V) values[slot])) {
                keys.add(new long[] {firsts[slot], seconds[slot]});
            }
        }
        List<V> removed = new ArrayList<>(keys.size());
        for (long[] key : keys) {
            removed.add(remove(key[0], key[1]));
        }
        return removed;
    }

    /** Which entries {@link #removeAll} removes. */
    @FunctionalInterface
    interface Match<V> {
        boolean matches(long first, long second, V value);
    }

    private int slotOf(long first, long second) {
        int mask = values.length - 1;
        int slot = hash(first, second) & mask;
        while (values[slot] != null) {
            if (firsts[slot] == first && seconds[slot] == second) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
        return -1;
    }

    /** Empties {@code emptied} and moves back every entry behind it whose probe would otherwise pass the gap. */
    private void shiftBackInto(int emptied) {
        int mask = values.length - 1;
        int gap = emptied;
        int slot = (gap + 1) & mask;
        while (values[slot] != null) {
            int home = hash(firsts[slot], seconds[slot]) & mask;
            if (((slot - home) & mask) >= ((slot - gap) & mask)) {
                firsts[gap] = firsts[slot];
                seconds[gap] = seconds[slot];
                values[gap] = values[slot];
                gap = slot;
            }
            slot = (slot + 1) & mask;
        }
        values[gap] = null;
        firsts[gap] = 0;
        seconds[gap] = 0;
    }

    /**
     * Moves every value into arrays of {@code capacity}.
     *
     * @implNote All three arrays are had before any field changes, so an {@link OutOfMemoryError} leaves the table as
     * it was.
     */
    private void rehash(int capacity) {
        long[] grownFirsts = memory.longs(capacity);
        long[] grownSeconds = memory.longs(capacity);
        Object[] grownValues = memory.objects(capacity);
        for (int slot = 0; slot < values.length; slot++) {
            if (values[slot] != null) {
                place(grownFirsts, grownSeconds, grownValues, firsts[slot], seconds[slot], values[slot]);
            }
        }
        firsts = grownFirsts;
        seconds = grownSeconds;
        values = grownValues;
    }

    private static void place(long[] firsts, long[] seconds, Object[] values, long first, long second,
                              Object value) {
        int mask = values.length - 1;
        int slot = hash(first, second) & mask;
        while (values[slot] != null) {
            slot = (slot + 1) & mask;
        }
        firsts[slot] = first;
        seconds[slot] = second;
        values[slot] = value;
    }

    /** Mixes the first before the second joins it, so a pair and the same pair swapped hash apart. */
    static int hash(long first, long second) {
        long mixed = mix(mix(first) + second);
        return (int) (mixed ^ (mixed >>> 32));
    }

    private static long mix(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }
}
