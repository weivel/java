package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every {@link DescribedHandle} one library's described definitions answered, and every {@link KeptAnswer} kept for a
 * definition it carried, that is not freed yet, so the unload releases each one still held while the binary is open,
 * the binary's close frees every one left, answered after the unload or released while the gate was shut, and an
 * input handle is known for one of this library.
 *
 * <p>What was got through a definition an answer carried is held by a {@link #child()}, which that answer's end
 * releases; the library's holdings still know every handle a child holds, so a handle is taken back by every
 * definition of the library that answered it, and only its release is the child's.
 */
final class DescribedHandles {
    private final DescribedHandles library;
    private final Set<DescribedHandle> held = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<KeptAnswer> kept = Collections.synchronizedSet(new LinkedHashSet<>());
    /** The library's alone: every handle any of its holdings holds. */
    private final Set<DescribedHandle> everyHandle;
    /** The library's alone: every answer any of its holdings keeps, in the order they were answered. */
    private final Set<KeptAnswer> everyKept;
    private volatile CallGate gate = CallGate.OPEN;

    DescribedHandles() {
        this.library = this;
        this.everyHandle = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.everyKept = Collections.synchronizedSet(new LinkedHashSet<>());
    }

    private DescribedHandles(DescribedHandles library) {
        this.library = library;
        this.everyHandle = null;
        this.everyKept = null;
    }

    /** Holdings of the same library whose {@link #releaseAll} releases only what they hold. */
    DescribedHandles child() {
        return new DescribedHandles(library);
    }

    /** Sets who decides whether a release may still enter the binary. */
    void gate(CallGate chosen) {
        library.gate = chosen;
    }

    DescribedHandle hold(long pointer, String type, ReleasePlan release) {
        DescribedHandle handle = new DescribedHandle(this, pointer, type, release);
        held.add(handle);
        library.everyHandle.add(handle);
        return handle;
    }

    /** Keeps an answer that carried a definition until its release, the unload or the close. */
    void keep(KeptAnswer answer) {
        kept.add(answer);
        library.everyKept.add(answer);
    }

    /** Whether any holdings of the library still keep an answer not freed yet. */
    boolean keepsAnyAnswer() {
        return !library.everyKept.isEmpty();
    }

    /**
     * A handle handed in as an input, counted in use until the call that was handed it lets go of it, so no release
     * frees it under the provider.
     *
     * @throws IllegalArgumentException for anything but a live handle of this library and of {@code type}
     */
    DescribedHandle use(Object value, String type, CallPath where) {
        if (!(value instanceof DescribedHandle)) {
            throw new IllegalArgumentException(where + " is a handle of " + type + ", written as the DescribedHandle "
                    + "an operation answered, not " + Scalars.described(value));
        }
        DescribedHandle handle = (DescribedHandle) value;
        if (!library.everyHandle.contains(handle) && !handle.isReleased()) {
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
        CallGate shared = library.gate;
        if (!shared.enter()) {
            return;
        }
        try {
            if (handle.takeTheFree()) {
                forget(handle);
                runRelease(handle);
            }
        } finally {
            shared.leave();
        }
    }

    /**
     * Frees a kept answer whose every carried definition has ended, unless another caller freed it; when the gate is
     * shut, the binary's close frees it instead.
     */
    void free(KeptAnswer answer) {
        CallGate shared = library.gate;
        if (!shared.enter()) {
            return;
        }
        try {
            if (answer.takeTheFree()) {
                forget(answer);
                answer.free();
            }
        } finally {
            shared.leave();
        }
    }

    /**
     * Releases every answer these holdings keep, and with it what was got through what it carried, then every handle
     * they hold, as the unload does while the binary is open, and as a carried definition's end does with its own.
     */
    void releaseAll() {
        List<KeptAnswer> answers;
        synchronized (kept) {
            answers = new ArrayList<>(kept);
        }
        for (KeptAnswer answer : answers) {
            answer.release();
        }
        for (DescribedHandle handle : new ArrayList<>(held)) {
            handle.release();
        }
    }

    /**
     * Frees every handle and then every kept answer not freed yet, the last kept first, as the binary closes, once
     * nothing else can call into it; called on the library's holdings.
     */
    void releaseAtTheClose() {
        for (DescribedHandle handle : new ArrayList<>(everyHandle)) {
            if (handle.releaseAtTheClose()) {
                handle.holdings().forget(handle);
                runRelease(handle);
            }
        }
        List<KeptAnswer> answers;
        synchronized (everyKept) {
            answers = new ArrayList<>(everyKept);
        }
        Collections.reverse(answers);
        for (KeptAnswer answer : answers) {
            if (answer.releaseAtTheClose()) {
                answer.holdings().forget(answer);
                answer.free();
            }
        }
    }

    private void forget(DescribedHandle handle) {
        held.remove(handle);
        library.everyHandle.remove(handle);
    }

    private void forget(KeptAnswer answer) {
        kept.remove(answer);
        library.everyKept.remove(answer);
    }

    private static void runRelease(DescribedHandle handle) {
        if (handle.releasePlan() != null) {
            handle.releasePlan().free(handle.pointer());
        }
    }
}
