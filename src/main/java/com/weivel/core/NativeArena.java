package com.weivel.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Native memory held for one owner and freed all at once when it closes: a call's arguments, or a wrapper's event
 * buffer. Only one thread at a time may allocate in it.
 */
final class NativeArena implements AutoCloseable {
    private final NativeAccess access;
    private final List<Long> allocations = new ArrayList<>();
    private volatile boolean alive = true;

    NativeArena(NativeAccess access) {
        this.access = access;
    }

    NativeAccess access() {
        return access;
    }

    /** Zeroed memory of {@code size} bytes, freed when this arena closes. */
    NativeRegion allocate(long size) {
        checkAlive();
        long address = access.allocate(size);
        allocations.add(address);
        return new NativeRegion(access, address, size, this);
    }

    NativeRegion allocate(Layout layout) {
        return allocate(layout.byteSize());
    }

    /** Zeroed memory for {@code count} elements of {@code element}, room for one when {@code count} is 0. */
    NativeRegion allocate(Layout element, long count) {
        return allocate(element.byteSize() * Math.max(1, count));
    }

    /** {@code text} encoded as UTF-8 and terminated by a zero byte. */
    NativeRegion text(String text) {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        NativeRegion region = allocate(encoded.length + 1L);
        access.writeBytes(region.address(), 0, encoded);
        return region;
    }

    boolean isAlive() {
        return alive;
    }

    /**
     * Frees everything allocated in this arena.
     *
     * @throws IllegalStateException when it was closed already
     */
    @Override
    public void close() {
        checkAlive();
        alive = false;
        for (long address : allocations) {
            access.free(address);
        }
        allocations.clear();
    }

    private void checkAlive() {
        if (!alive) {
            throw new IllegalStateException("the arena is closed already");
        }
    }
}
