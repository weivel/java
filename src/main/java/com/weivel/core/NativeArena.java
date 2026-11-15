package com.weivel.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Native memory held for one owner and freed all at once when it closes: a call's arguments, or a wrapper's event
 * buffer. Only one thread at a time may allocate in it.
 *
 * <p>A {@link #reusable} arena hands out memory from one block it keeps, zeroed as it is handed out, and
 * {@link #reset} makes the whole block free again, so an owner that resets it after each use allocates nothing once
 * the block is large enough. What does not fit is allocated on its own, freed at the reset, and the block grows at
 * the reset to hold everything that use asked for.
 */
final class NativeArena implements AutoCloseable {
    private static final long ALIGNMENT = 16;
    private static final long FIRST_BLOCK_SIZE = 256;

    private final NativeAccess access;
    private final List<Long> allocations = new ArrayList<>();
    private final boolean reusable;
    private volatile boolean alive = true;
    private long block;
    private long blockSize;
    private long used;
    private long asked;
    private String lastText;
    private byte[] lastEncoding;

    NativeArena(NativeAccess access) {
        this(access, false);
    }

    private NativeArena(NativeAccess access, boolean reusable) {
        this.access = access;
        this.reusable = reusable;
        if (reusable) {
            this.block = access.allocate(FIRST_BLOCK_SIZE);
            this.blockSize = FIRST_BLOCK_SIZE;
        }
    }

    /** An arena whose memory {@link #reset} makes free again, for an owner that uses it once at a time. */
    static NativeArena reusable(NativeAccess access) {
        return new NativeArena(access, true);
    }

    NativeAccess access() {
        return access;
    }

    /** Zeroed memory of {@code size} bytes, freed when this arena closes, or for a reusable one at its reset. */
    NativeRegion allocate(long size) {
        return new NativeRegion(access, allocateAddress(size), size, this);
    }

    /**
     * The address of zeroed memory of {@code size} bytes, as {@link #allocate(long)} holds it: for a crossing that
     * only hands the address on, so no region is made.
     */
    long allocateAddress(long size) {
        checkAlive();
        if (reusable) {
            long aligned = (size + ALIGNMENT - 1) & -ALIGNMENT;
            asked += aligned;
            if (used + aligned <= blockSize) {
                long address = block + used;
                used += aligned;
                access.clear(address, 0, size);
                return address;
            }
        }
        long address = access.allocate(size);
        allocations.add(address);
        return address;
    }

    /**
     * Makes a reusable arena's memory free again: what did not fit in its block is freed, and the block grows to hold
     * all that was asked since the last reset. A block that cannot grow stays as it was.
     *
     * @throws IllegalStateException for an arena that is not reusable, or is closed
     */
    void reset() {
        checkAlive();
        if (!reusable) {
            throw new IllegalStateException("only a reusable arena is reset");
        }
        freeAllocations();
        if (asked > blockSize) {
            grow(Math.max(asked, blockSize * 2));
        }
        used = 0;
        asked = 0;
    }

    private void grow(long size) {
        long grown;
        try {
            grown = access.allocate(size);
        } catch (OutOfMemoryError none) {
            return;
        }
        access.free(block);
        block = grown;
        blockSize = size;
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
        byte[] encoded = encoded(text);
        return new NativeRegion(access, placed(encoded), encoded.length + 1L, this);
    }

    /**
     * The address of {@code text} laid out as {@link #text} lays it out: for a crossing that only hands the address
     * on, so no region is made.
     */
    long textAddress(String text) {
        return placed(encoded(text));
    }

    /**
     * {@code text} encoded as UTF-8.
     *
     * @implNote The last text encoded is kept with its encoding and known again by identity, so a caller passing the
     * same text again, as a plugin passes its ids, costs one comparison and allocates nothing; any other text is
     * encoded as it always was.
     */
    private byte[] encoded(String text) {
        if (text != lastText) {
            lastEncoding = text.getBytes(StandardCharsets.UTF_8);
            lastText = text;
        }
        return lastEncoding;
    }

    private long placed(byte[] encoded) {
        long address = allocateAddress(encoded.length + 1L);
        access.writeBytes(address, 0, encoded);
        return address;
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
        freeAllocations();
        if (reusable) {
            access.free(block);
        }
    }

    /** @implNote Indexed, so a reset with nothing to free makes no iterator. */
    private void freeAllocations() {
        for (int index = 0; index < allocations.size(); index++) {
            access.free(allocations.get(index));
        }
        allocations.clear();
    }

    private void checkAlive() {
        if (!alive) {
            throw new IllegalStateException("the arena is closed already");
        }
    }
}
