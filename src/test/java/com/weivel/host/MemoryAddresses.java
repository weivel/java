package com.weivel.host;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Where Java memory lies, read without the layer: a heap array's first element and a direct buffer's first byte, so
 * a test can compare them with the address the echo library reports it was handed.
 *
 * @implNote A heap address is read as an uncompressed reference through {@code sun.misc.Unsafe}, so the test JVM
 * runs with {@code -XX:-UseCompressedOops}, which {@link #requireUncompressedReferences()} checks; the array may move
 * between the read and a call, so a caller compares over several tries.
 */
final class MemoryAddresses {
    private static final Object UNSAFE = theUnsafe();

    private MemoryAddresses() {
    }

    static long firstElementOf(byte[] array) {
        requireUncompressedReferences();
        Object[] holder = {array};
        long reference = (Long) invoke("getLong", new Class<?>[] {Object.class, long.class}, holder,
                (long) baseOffset(Object[].class));
        return reference + baseOffset(byte[].class);
    }

    static long firstByteOf(ByteBuffer direct) {
        try {
            Field address = Buffer.class.getDeclaredField("address");
            long offset = (Long) invoke("objectFieldOffset", new Class<?>[] {Field.class}, address);
            return (Long) invoke("getLong", new Class<?>[] {Object.class, long.class}, direct, offset);
        } catch (NoSuchFieldException absent) {
            throw new AssertionError(absent);
        }
    }

    private static void requireUncompressedReferences() {
        boolean uncompressed = ManagementFactory.getRuntimeMXBean().getInputArguments()
                .contains("-XX:-UseCompressedOops");
        if (!uncompressed) {
            throw new IllegalStateException("the test JVM must run with -XX:-UseCompressedOops");
        }
    }

    private static int baseOffset(Class<?> arrayClass) {
        return (Integer) invoke("arrayBaseOffset", new Class<?>[] {Class.class}, arrayClass);
    }

    private static Object invoke(String name, Class<?>[] parameters, Object... arguments) {
        try {
            Method method = UNSAFE.getClass().getMethod(name, parameters);
            return method.invoke(UNSAFE, arguments);
        } catch (ReflectiveOperationException unreachable) {
            throw new AssertionError(unreachable);
        }
    }

    private static Object theUnsafe() {
        try {
            Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException unreachable) {
            throw new AssertionError(unreachable);
        }
    }
}
