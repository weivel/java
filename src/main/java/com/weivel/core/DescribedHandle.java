package com.weivel.core;

/**
 * An opaque handle a described operation answered, such as an open walk, which lives until it is released: by its
 * holder through {@link #release()}, or by the layer when the library is unloaded, or at the latest when its binary
 * closes, whichever comes first. It is freed exactly once, and never while a call that was handed it is still in its
 * provider: a release then waits for that call to return, and the call frees it. Any thread may release it.
 *
 * @implNote {@code released} says no call may hand it on any more, {@code freed} that its native release has run or
 *     is running inside the binary's gate. A release that finds the gate shut leaves {@code freed} unset, so the
 *     binary's close, the one caller left, frees it.
 */
public final class DescribedHandle {
    private final DescribedHandles holdings;
    private final long pointer;
    private final String type;
    private final ReleasePlan release;
    private int users;
    private boolean released;
    private boolean freed;

    DescribedHandle(DescribedHandles holdings, long pointer, String type, ReleasePlan release) {
        this.holdings = holdings;
        this.pointer = pointer;
        this.type = type;
        this.release = release;
    }

    /**
     * Answers {@code type}.
     *
     * @return the opaque type the description names it by
     */
    public String type() {
        return type;
    }

    /**
     * Whether it has been released.
     *
     * @return whether it has, by its holder, the unload or the binary's close
     */
    public synchronized boolean isReleased() {
        return released;
    }

    /**
     * Frees it through the release its description names, once no call handed it is still running; a later call, or
     * one after the binary closed, does nothing.
     */
    public void release() {
        boolean unused;
        synchronized (this) {
            if (released) {
                return;
            }
            released = true;
            unused = users == 0;
        }
        if (unused) {
            holdings.free(this);
        }
    }

    /**
     * Counts a call that hands the handle to its provider in.
     *
     * @return false for a handle released already, which no call may hand on
     */
    synchronized boolean use() {
        if (released) {
            return false;
        }
        users++;
        return true;
    }

    /** Counts a call {@link #use} let in out again, freeing the handle when it was released meanwhile. */
    void leave() {
        boolean last;
        synchronized (this) {
            users--;
            last = released && users == 0;
        }
        if (last) {
            holdings.free(this);
        }
    }

    /**
     * Takes the one free there is, for a caller about to run the native release.
     *
     * @return false when it was freed already, or a call still holds it
     */
    synchronized boolean takeTheFree() {
        if (freed || users > 0) {
            return false;
        }
        freed = true;
        return true;
    }

    /**
     * Marks it released as its binary closes, with nothing left to call in it.
     *
     * @return whether this close frees it, which it does for every handle not freed yet, released or not
     */
    synchronized boolean releaseAtTheClose() {
        released = true;
        if (freed) {
            return false;
        }
        freed = true;
        return true;
    }

    ReleasePlan releasePlan() {
        return release;
    }

    long pointer() {
        return pointer;
    }

    @Override
    public String toString() {
        return "DescribedHandle[" + type + (isReleased() ? ", released]" : "]");
    }
}
