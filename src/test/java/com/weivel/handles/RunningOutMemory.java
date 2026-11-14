package com.weivel.handles;

/** A table's memory that counts every array it hands out and, once told to, runs out after so many more. */
final class RunningOutMemory implements TableMemory {
    private int allocations;
    private int allowedBeforeRunningOut = Integer.MAX_VALUE;

    /** Lets {@code allowed} more arrays be had, and throws {@link OutOfMemoryError} for the one after. */
    void runOutAfter(int allowed) {
        allowedBeforeRunningOut = allowed;
    }

    void neverRunOut() {
        allowedBeforeRunningOut = Integer.MAX_VALUE;
    }

    int allocations() {
        return allocations;
    }

    @Override
    public long[] longs(int length) {
        take();
        return new long[length];
    }

    @Override
    public int[] ints(int length) {
        take();
        return new int[length];
    }

    @Override
    public Object[] objects(int length) {
        take();
        return new Object[length];
    }

    @Override
    public HandleEntry[] entries(int length) {
        take();
        return new HandleEntry[length];
    }

    private void take() {
        if (allowedBeforeRunningOut == 0) {
            throw new OutOfMemoryError("a memory that runs out on purpose");
        }
        allowedBeforeRunningOut--;
        allocations++;
    }
}
