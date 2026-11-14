package com.weivel.core;

/**
 * Native memory a {@link ForeignLibrary}'s caller holds for its calls, freed all at once when it closes, and reads of
 * the memory such a library answers with, which stays the library's to free. Only one thread at a time may use one.
 */
public final class ForeignMemory implements AutoCloseable {
    private final NativeArena arena;
    private final NativeAccess access;

    ForeignMemory(NativeArena arena) {
        this.arena = arena;
        this.access = arena.access();
    }

    /**
     * Zeroed memory, freed when this closes.
     *
     * @param size how many bytes
     * @return its address
     */
    public long allocate(long size) {
        return arena.allocate(size).address();
    }

    /**
     * A text as UTF-8 terminated by a zero byte, freed when this closes.
     *
     * @param text the text
     * @return its address
     * @throws IllegalArgumentException for a text holding a NUL character, which C would read cut short
     */
    public long text(String text) {
        if (text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("a text holding a NUL character cannot cross as C text");
        }
        return arena.text(text).address();
    }

    /**
     * A copy of {@code bytes}, freed when this closes; one byte is allocated for none, so the address is never 0.
     *
     * @param bytes the bytes
     * @return the copy's address
     */
    public long bytes(byte[] bytes) {
        long address = allocate(bytes.length);
        access.writeBytes(address, 0, bytes);
        return address;
    }

    /**
     * Reads a 32-bit integer.
     *
     * @param address where the struct or value starts
     * @param offset the field's offset
     * @return the value
     */
    public int getInt(long address, long offset) {
        return access.getInt(address, offset);
    }

    /**
     * Writes a 32-bit integer.
     *
     * @param address where the struct or value starts
     * @param offset the field's offset
     * @param value the value
     */
    public void putInt(long address, long offset, int value) {
        access.putInt(address, offset, value);
    }

    /**
     * Reads a 64-bit integer or a pointer.
     *
     * @param address where the struct or value starts
     * @param offset the field's offset
     * @return the value
     */
    public long getLong(long address, long offset) {
        return access.getLong(address, offset);
    }

    /**
     * Writes a 64-bit integer or a pointer.
     *
     * @param address where the struct or value starts
     * @param offset the field's offset
     * @param value the value
     */
    public void putLong(long address, long offset, long value) {
        access.putLong(address, offset, value);
    }

    /**
     * Reads bytes.
     *
     * @param address where they start
     * @param offset an offset from there
     * @param length how many
     * @return a copy
     */
    public byte[] readBytes(long address, long offset, int length) {
        return access.readBytes(address, offset, length);
    }

    /**
     * Reads the UTF-8 text at an address up to its terminating zero byte.
     *
     * @param address the text's address, never 0
     * @return the text
     */
    public String readText(long address) {
        if (address == 0) {
            throw new IllegalArgumentException("a NULL pointer holds no text");
        }
        return access.readText(address);
    }

    /**
     * Copies the core's {@code weivel_manifest} at an address into a manifest value, the memory left to its owner.
     *
     * @param address the struct's address, laid out as the core's header declares it
     * @return the value
     * @throws IllegalArgumentException for a manifest with no import name, which the core requires
     */
    public Manifest manifest(long address) {
        if (address == 0) {
            throw new IllegalArgumentException("a NULL pointer holds no manifest");
        }
        return ManifestCopy.read(new NativeRegion(access, address, CoreLayouts.MANIFEST.byteSize(), null));
    }

    /**
     * Frees everything this memory allocated.
     *
     * @throws IllegalStateException when it was closed already
     */
    @Override
    public void close() {
        arena.close();
    }
}
