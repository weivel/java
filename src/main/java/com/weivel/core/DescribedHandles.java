package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every {@link DescribedHandle} one library's described definitions answered and nobody has released yet, so the
 * unload releases each one still held while the binary is open, and an input handle is known for one of this library.
 */
final class DescribedHandles {
    private final Set<DescribedHandle> held = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile CallGate gate = CallGate.OPEN;

    /** Sets who decides whether a release may still enter the binary. */
    void gate(CallGate chosen) {
        gate = chosen;
    }

    DescribedHandle hold(long pointer, String type, ReleasePlan release) {
        DescribedHandle handle = new DescribedHandle(this, pointer, type, release);
        held.add(handle);
        return handle;
    }

    /**
     * The pointer a handle handed in as an input carries.
     *
     * @throws IllegalArgumentException for anything but a live handle of this library and of {@code type}
     */
    long pointerOf(Object value, String type, String where) {
        if (!(value instanceof DescribedHandle)) {
            throw new IllegalArgumentException(where + " is a handle of " + type + ", written as the DescribedHandle "
                    + "an operation answered, not " + Scalars.described(value));
        }
        DescribedHandle handle = (DescribedHandle) value;
        if (!held.contains(handle)) {
            throw new IllegalArgumentException(where + " is a handle " + (handle.isReleased() ? "released already"
                    : "of another library"));
        }
        if (!handle.type().equals(type)) {
            throw new IllegalArgumentException(where + " is a handle of " + type + ", and was handed one of "
                    + handle.type());
        }
        return handle.pointer();
    }

    void forget(DescribedHandle handle) {
        held.remove(handle);
    }

    /** Frees a handle's pointer, unless the binary is closed already, after which the unload freed it. */
    void free(ReleasePlan release, long pointer) {
        if (release == null || !gate.enter()) {
            return;
        }
        try {
            release.free(pointer);
        } finally {
            gate.leave();
        }
    }

    /** Releases every handle still held, as the unload does while the binary is open. */
    void releaseAll() {
        for (DescribedHandle handle : new ArrayList<>(held)) {
            handle.release();
        }
    }
}
