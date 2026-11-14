package com.weivel.handles;

/**
 * Where the table's arrays come from: the heap, or, in a test, a memory that runs out on purpose, so that a table left
 * as it was by an {@link OutOfMemoryError} while it grows can be shown.
 */
interface TableMemory {
    /** The heap, which every table outside a test takes its arrays from. */
    TableMemory HEAP = new TableMemory() {
        @Override
        public long[] longs(int length) {
            return new long[length];
        }

        @Override
        public int[] ints(int length) {
            return new int[length];
        }

        @Override
        public Object[] objects(int length) {
            return new Object[length];
        }

        @Override
        public HandleEntry[] entries(int length) {
            return new HandleEntry[length];
        }
    };

    long[] longs(int length);

    int[] ints(int length);

    Object[] objects(int length);

    HandleEntry[] entries(int length);
}
