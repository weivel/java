package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every {@link DescribedHandle} one library's described definitions answered that is not freed yet, so the unload
 * releases each one still held while the binary is open, the binary's close frees every one left, answered after the
 * unload or released while the gate was shut, and an input handle is known for one of this library.
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
     * A handle handed in as an input, counted in use until the call that was handed it lets go of it, so no release
     * frees it under the provider.
     *
     * @throws IllegalArgumentException for anything but a live handle of this library and of {@code type}
     */
    DescribedHandle use(Object value, String type, String where) {
        if (!(value instanceof DescribedHandle)) {
            throw new IllegalArgumentException(where + " is a handle of " + type + ", written as the DescribedHandle "
                    + "an operation answered, not " + Scalars.described(value));
        }
        DescribedHandle handle = (DescribedHandle) value;
        if (!held.contains(handle) && !handle.isReleased()) {
            throw new IllegalArgumentException(where + " is a handle of another library");
        }
        if (!handle.type().equals(type)) {
            throw new IllegalArgumentException(where + " is a handle of " + type + ", and was handed one of "
                    + handle.type());
        }
        if (!handle.use()) {
            throw new IllegalArgumentException(where + " is a handle released already");
        }
        return handle;
    }

    /**
     * Frees a released handle through its release, unless a call still holds it or another caller freed it; when the
     * gate is shut, the binary is closing, and its close frees the handle instead.
     */
    void free(DescribedHandle handle) {
        if (!gate.enter()) {
            return;
        }
        try {
            if (handle.takeTheFree()) {
                held.remove(handle);
                runRelease(handle);
            }
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

    /** Frees every handle not freed yet as the binary closes, once nothing else can call into it. */
    void releaseAtTheClose() {
        for (DescribedHandle handle : new ArrayList<>(held)) {
            if (handle.releaseAtTheClose()) {
                held.remove(handle);
                runRelease(handle);
            }
        }
    }

    private static void runRelease(DescribedHandle handle) {
        if (handle.releasePlan() != null) {
            handle.releasePlan().free(handle.pointer());
        }
    }
}
