package com.weivel.core;

import java.util.concurrent.atomic.AtomicReference;

/**
 * One reusable arena that the described calls into one library take and give back, as {@link SpareBlock} keeps one
 * block for its bytes calls: a call takes it, writes its inputs and empty answers into it, and gives it back reset,
 * so once the arena has grown to hold a call, a call allocates no native memory at all. A call that finds it taken
 * makes an arena of its own, so calls on any number of threads never share memory.
 *
 * @implNote One arena rather than an {@code Arena} of the foreign function API per call: that would allocate and free
 *     its native memory on every call, which is the cost a bound call exists to avoid.
 */
final class SpareArena {
    private final NativeAccess access;
    private final AtomicReference<NativeArena> spare = new AtomicReference<>();
    private volatile boolean closed;

    SpareArena(NativeAccess access) {
        this.access = access;
    }

    NativeArena take() {
        NativeArena arena = spare.getAndSet(null);
        return arena != null ? arena : NativeArena.reusable(access);
    }

    /** Gives back an arena {@link #take} answered, its memory made free again: kept as the spare, or closed. */
    void give(NativeArena arena) {
        arena.reset();
        if (!spare.compareAndSet(null, arena)) {
            arena.close();
            return;
        }
        if (closed) {
            closeTheSpare();
        }
    }

    /** Closes the spare; an arena given back afterwards is closed as it comes. */
    void close() {
        closed = true;
        closeTheSpare();
    }

    private void closeTheSpare() {
        NativeArena arena = spare.getAndSet(null);
        if (arena != null) {
            arena.close();
        }
    }
}
