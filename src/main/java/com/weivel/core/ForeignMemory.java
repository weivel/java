package com.weivel.core;

import java.util.Objects;

/**
 * Native memory a {@link ForeignLibrary}'s caller holds for its calls, freed all at once when it closes, and reads of
 * the memory such a library answers with, which stays the library's to free. Only one thread at a time may use one.
 * Once closed it refuses every access, its own memory and the library's alike, with an {@link IllegalStateException}.
 *
 * <p>It is public for the layer's own packages, {@code standard}, {@code conformance} and {@code benchmarks}: an
 * address it is handed is read or written unchecked, so a caller outside them can corrupt any native memory through
 * it.
 */
public final class ForeignMemory implements AutoCloseable {
    private final NativeArena arena;
    private final NativeAccess access;
    private boolean closed;

    ForeignMemory(NativeArena arena) {
        this.arena = arena;
        this.access = arena.access();
    }

    /**
     * Memory of the layer's one native seam, held by no library.
     *
     * @return memory of its own, freed when it is closed
     */
    public static ForeignMemory open() {
        return new ForeignMemory(new NativeArena(NativeBackends.current()));
    }

    /**
     * The native address a definition's object carries, as a host registers a library's definition.
     *
     * @param addressed a {@code MemorySegment} on the foreign function API, a JNA {@code Pointer} on JNA
     * @return its address
     * @throws IllegalArgumentException for an object of another kind
     */
    public static long addressOf(Object addressed) {
        return NativeBackends.current().addressOf(Objects.requireNonNull(addressed, "addressed"));
    }

    /**
     * Zeroed memory, freed when this closes.
     *
     * @param size how many bytes
     * @return its address
     */
    public long allocate(long size) {
        requireOpen();
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
        requireOpen();
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
        requireOpen();
        long address = allocate(bytes.length);
        access.writeBytes(address, 0, bytes);
        return address;
    }

    /**
     * Writes {@code bytes} at an address, into memory of this or of the library's.
     *
     * @param address where the memory starts
     * @param offset where in it the bytes go
     * @param bytes the bytes
     */
    public void write(long address, long offset, byte[] bytes) {
        requireOpen();
        access.writeBytes(address, offset, bytes);
    }

    /**
     * Reads a 32-bit integer.
     *
     * @param address where the struct or value starts
     * @param offset the field's offset
     * @return the value
     */
    public int getInt(long address, long offset) {
        requireOpen();
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
        requireOpen();
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
        requireOpen();
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
        requireOpen();
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
        requireOpen();
        return access.readBytes(address, offset, length);
    }

    /**
     * Reads the UTF-8 text at an address up to its terminating zero byte.
     *
     * @param address the text's address, never 0
     * @return the text
     */
    public String readText(long address) {
        requireOpen();
        if (address == 0) {
            throw new IllegalArgumentException("a NULL pointer holds no text");
        }
        return access.readText(address);
    }

    /**
     * Copies the core's {@code weivel_manifest} at an address into a manifest value, the memory left to its owner. It
     * is read up to the {@code size} it declares, as the core reads one: a field past an older producer's size is
     * absent and takes the default core.h states, and what a newer producer laid out past this layer's shape is
     * ignored; a list of growing elements is stepped at its first element's {@code size}.
     *
     * @param address the struct's address, laid out as the core's header declares it
     * @return the value
     * @throws IllegalArgumentException for a manifest with no import name, which the core requires, or a manifest or
     *     element whose size is below its first published shape, naming the struct
     */
    public Manifest manifest(long address) {
        requireOpen();
        if (address == 0) {
            throw new IllegalArgumentException("a NULL pointer holds no manifest");
        }
        long declared = Integer.toUnsignedLong(access.getInt(address, 0));
        return ManifestCopy.read(new NativeRegion(access, address, declared, null));
    }

    /**
     * Frees everything this memory allocated.
     *
     * @throws IllegalStateException when it was closed already
     */
    @Override
    public void close() {
        requireOpen();
        closed = true;
        arena.close();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("the foreign memory is closed");
        }
    }
}
