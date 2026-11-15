package com.weivel.host;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Who holds a C library's binary open: the host until it unloads the library, each call running in it, and each
 * definition service until the core reports that nothing holds it any more. Once the library is closed to calls no
 * call enters, and the binary closes when the last holder lets go, so nothing ever calls into a closed binary and an
 * unload never waits for a call.
 *
 * @implNote One atomic word carries both: its high bit says the library is closed to calls, the rest counts the
 * holders, so entering, leaving and closing to calls take no lock, and exactly one of them sees the count reach none.
 */
final class LibraryLifetime {
    private static final long CLOSED_TO_CALLS = 1L << 62;

    private final AtomicLong state = new AtomicLong(1);
    private final Runnable closeBinary;

    LibraryLifetime(Runnable closeBinary) {
        this.closeBinary = closeBinary;
    }

    /** Counts a call, or a definition service about to be registered, in, unless the library is closed to calls. */
    boolean enter() {
        long current;
        do {
            current = state.get();
            if ((current & CLOSED_TO_CALLS) != 0) {
                return false;
            }
        } while (!state.compareAndSet(current, current + 1));
        return true;
    }

    void leave() {
        if (state.decrementAndGet() == CLOSED_TO_CALLS) {
            closeBinary.run();
        }
    }

    /**
     * Closes the library to calls and lets go of the host's hold, closing the binary now when no call runs in it.
     *
     * @return false, doing nothing, when it was closed to calls already
     */
    boolean closeToCalls() {
        long current;
        do {
            current = state.get();
            if ((current & CLOSED_TO_CALLS) != 0) {
                return false;
            }
        } while (!state.compareAndSet(current, current | CLOSED_TO_CALLS));
        leave();
        return true;
    }
}
