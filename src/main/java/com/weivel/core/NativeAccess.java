package com.weivel.core;

import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * The one seam through which the layer reaches native code: opening a library, and reading and writing native memory
 * by address. {@link NativeBackends#current()} answers the one this Java runs on, and nothing outside this package
 * reaches native code another way.
 *
 * @implNote Memory is read and written without alignment checks, as C reads it, so both backends answer alike.
 */
interface NativeAccess {
    /** The backend's name, {@code ffm} or {@code jna}. */
    String name();

    /** How many bytes an address takes in this JVM. */
    long addressSize();

    /**
     * Opens the shared library at {@code path}, a path its caller chose.
     *
     * @throws IllegalArgumentException with the platform loader's reason, when the path names no library it could open
     */
    OpenLibrary open(Path path);

    /**
     * Allocates {@code size} bytes of native memory, zeroed, aligned for any primitive.
     *
     * @throws OutOfMemoryError when the platform has none to give
     */
    long allocate(long size);

    /** Frees memory {@link #allocate} answered. */
    void free(long address);

    int getInt(long address, long offset);

    void putInt(long address, long offset, int value);

    long getLong(long address, long offset);

    void putLong(long address, long offset, long value);

    double getDouble(long address, long offset);

    void putDouble(long address, long offset, double value);

    byte[] readBytes(long address, long offset, int length);

    void writeBytes(long address, long offset, byte[] bytes);

    /** Writes {@code length} bytes of {@code bytes} from {@code from} at {@code address} plus {@code offset}. */
    void writeBytes(long address, long offset, byte[] bytes, int from, int length);

    /** Sets {@code length} bytes from {@code address} plus {@code offset} to zero. */
    void clear(long address, long offset, long length);

    /** Reads the UTF-8 text starting at {@code address}, up to its terminating zero byte. */
    String readText(long address);

    /**
     * The address of the byte at a direct buffer's position.
     *
     * @throws IllegalArgumentException for a buffer that is not direct
     */
    long addressOf(ByteBuffer direct);

    /**
     * Keeps {@code object} reachable up to this call, so memory it owns, as a direct buffer owns its native memory,
     * is not freed by its cleaner while native code still reads it through a raw address.
     */
    void keepReachable(Object object);

    /**
     * The language's own object carrying a native address, never a copy of what is there: a {@code MemorySegment} of
     * {@code size} bytes on the foreign function API, a JNA {@code Pointer} on JNA.
     */
    Object addressed(long address, long size);

    /**
     * The native address an object {@link #addressed} answered carries.
     *
     * @throws IllegalArgumentException for an object of another kind
     */
    long addressOf(Object addressed);

    /** The function at a native address, as a function pointer a library answered, called as {@code signature} says. */
    NativeFunction function(long address, NativeSignature signature);

    /**
     * Lays a struct out by this backend's own rules for C structs, its embedded structs included, so the offsets a
     * description states can be held against them.
     *
     * @throws DescriptionsNotBound saying so, on a backend that does not bind descriptions yet
     */
    FfiStruct.Placement layOut(FfiStruct struct);
}
