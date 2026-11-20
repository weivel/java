package com.weivel.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An opaque handle a described operation answered, such as an open walk, which lives until it is released: by its
 * holder through {@link #release()}, or by the layer when the library is unloaded, while its binary is still open,
 * whichever comes first. It is released exactly once; any thread may release it.
 */
public final class DescribedHandle {
    private final DescribedHandles holdings;
    private final long pointer;
    private final String type;
    private final ReleasePlan release;
    private final AtomicBoolean released = new AtomicBoolean();

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
     * @return whether it has, by its holder or by the unload
     */
    public boolean isReleased() {
        return released.get();
    }

    /** Frees it through the release its description names; a later call, or one after the unload, does nothing. */
    public void release() {
        if (!released.compareAndSet(false, true)) {
            return;
        }
        holdings.forget(this);
        holdings.free(release, pointer);
    }

    long pointer() {
        return pointer;
    }

    @Override
    public String toString() {
        return "DescribedHandle[" + type + (isReleased() ? ", released]" : "]");
    }
}
