package com.weivel.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * One spare block of native memory that the calls into one library take and give back, so that a call needing no
 * more than {@link #SIZE} bytes allocates nothing while no other call holds the block. A call that finds it taken
 * allocates a block of its own and frees it, so calls on any number of threads never share memory, and the memory
 * kept is one block whatever the number of threads.
 *
 * @implNote Measured on JDK 25.0.3 on Windows through the foreign function API, the echo library's call ran 1.70 times
 *     the bare call with one allocation a call and 1.27 with this block; a block per thread ran 1.20, but keeps
 *     memory for every thread that ever called until the library closes, which grows with thread churn.
 */
final class SpareBlock {
    static final long SIZE = 256;

    private final NativeAccess access;
    private final AtomicLong spare = new AtomicLong();
    private volatile boolean closed;

    SpareBlock(NativeAccess access) {
        this.access = access;
    }

    /** At least {@code size} bytes, the first {@code size} of them zeroed; given back through {@link #give}. */
    long take(long size) {
        if (size > SIZE) {
            return access.allocate(size);
        }
        long block = spare.getAndSet(0);
        if (block == 0) {
            return access.allocate(SIZE);
        }
        access.clear(block, 0, size);
        return block;
    }

    /** Gives back a block {@link #take} answered for {@code size} bytes: kept as the spare, or freed. */
    void give(long block, long size) {
        if (size > SIZE || !spare.compareAndSet(0, block)) {
            access.free(block);
            return;
        }
        if (closed) {
            freeTheSpare();
        }
    }

    /** Frees the spare; a block given back afterwards is freed as it comes. */
    void close() {
        closed = true;
        freeTheSpare();
    }

    private void freeTheSpare() {
        long block = spare.getAndSet(0);
        if (block != 0) {
            access.free(block);
        }
    }
}
