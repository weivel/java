package com.weivel.handles;

/** What the tests of the layer's other packages read of a table that its package keeps to itself. */
public final class HandlesProbes {
    private HandlesProbes() {
    }

    /**
     * Whether the calling thread holds the table's own lock.
     *
     * @param handles the table
     * @return whether it does
     */
    public static boolean isLockHeldByCurrentThread(Handles handles) {
        return handles.isLockHeldByCurrentThread();
    }
}
