package com.weivel.core;

/**
 * A span of native memory read and written by offset: memory an arena holds, which refuses access once the arena
 * closes, or memory the core holds, which the core's own answer says how long to read.
 */
final class NativeRegion {
    private static final long INT_SIZE = 4;
    private static final long WORD_SIZE = 8;

    private final NativeAccess access;
    private final long address;
    private final long size;
    private final NativeArena owner;

    NativeRegion(NativeAccess access, long address, long size, NativeArena owner) {
        this.access = access;
        this.address = address;
        this.size = size;
        this.owner = owner;
    }

    /** {@code size} bytes at {@code address} of memory no arena holds, read through the same backend as this one. */
    NativeRegion at(long otherAddress, long otherSize) {
        return new NativeRegion(access, otherAddress, otherSize, null);
    }

    /** The {@code length} bytes of this region from {@code offset}, held by the same arena. */
    NativeRegion slice(long offset, long length) {
        checkBounds(offset, length);
        return new NativeRegion(access, address + offset, length, owner);
    }

    long address() {
        return address;
    }

    long byteSize() {
        return size;
    }

    boolean isAlive() {
        return owner == null || owner.isAlive();
    }

    int getInt(long offset) {
        checkAccess(offset, INT_SIZE);
        return access.getInt(address, offset);
    }

    void putInt(long offset, int value) {
        checkAccess(offset, INT_SIZE);
        access.putInt(address, offset, value);
    }

    long getLong(long offset) {
        checkAccess(offset, WORD_SIZE);
        return access.getLong(address, offset);
    }

    void putLong(long offset, long value) {
        checkAccess(offset, WORD_SIZE);
        access.putLong(address, offset, value);
    }

    double getDouble(long offset) {
        checkAccess(offset, WORD_SIZE);
        return access.getDouble(address, offset);
    }

    void putDouble(long offset, double value) {
        checkAccess(offset, WORD_SIZE);
        access.putDouble(address, offset, value);
    }

    /** The pointer at {@code offset}, as its address. */
    long getAddress(long offset) {
        return getLong(offset);
    }

    void putAddress(long offset, long pointer) {
        putLong(offset, pointer);
    }

    /** Reads the UTF-8 text at {@code otherAddress}, memory no arena holds. */
    String textAt(long otherAddress) {
        return access.readText(otherAddress);
    }

    void clear() {
        checkAccess(0, size);
        access.clear(address, 0, size);
    }

    private void checkAccess(long offset, long length) {
        if (!isAlive()) {
            throw new IllegalStateException("the memory's arena is closed");
        }
        checkBounds(offset, length);
    }

    private void checkBounds(long offset, long length) {
        if (offset < 0 || length < 0 || offset > size - length) {
            throw new IndexOutOfBoundsException("bytes " + offset + " to " + (offset + length)
                    + " of a region of " + size);
        }
    }
}
