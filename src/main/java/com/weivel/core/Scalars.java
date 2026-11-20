package com.weivel.core;

import java.nio.charset.StandardCharsets;

/**
 * The described scalars and texts as Java hands and takes them. Every integer kind is a {@link Long}: a U32 from 0 to
 * 2^32 - 1, an I64 as itself, and a U64 or a SIZE as its 64 bits, so one at or above 2^63 reads negative and is read
 * with {@link Long#toUnsignedString(long)}. An F64 is a {@link Double}. Each is checked before it is written, so no
 * value is wrapped or rounded on its way to C.
 */
final class Scalars {
    private static final long LARGEST_U32 = 0xFFFFFFFFL;
    private static final long LONGEST_EXACT = 1L << 53;

    private Scalars() {
    }

    /**
     * The bits a scalar of {@code kind} is written as: an integer as its value, an F64 as its raw IEEE 754 bits.
     *
     * @throws IllegalArgumentException for a value of another type, a U32 outside its range, or an integer handed for
     *     an F64 that a double does not hold exactly
     */
    static long bits(int kind, Object value, String where) {
        if (kind == DescribedKind.F64) {
            return Double.doubleToRawLongBits(real(value, where));
        }
        long integer = integer(value, where);
        if (kind == DescribedKind.U32 && (integer < 0 || integer > LARGEST_U32)) {
            throw new IllegalArgumentException(where + " is a u32, which holds 0 to " + LARGEST_U32 + ", and "
                    + integer + " lies outside");
        }
        return integer;
    }

    private static long integer(Object value, String where) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException(where + " takes an integer as a Long or an Integer, not "
                + described(value));
    }

    private static double real(Object value, String where) {
        if (value instanceof Double || value instanceof Float) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            long integer = ((Number) value).longValue();
            if (integer > LONGEST_EXACT || integer < -LONGEST_EXACT) {
                throw new IllegalArgumentException(where + " is an f64, which holds " + integer + " only rounded");
            }
            return integer;
        }
        throw new IllegalArgumentException(where + " is an f64, so it takes a Double, not " + described(value));
    }

    /** The Java value of the scalar of {@code kind} at {@code address}. */
    static Object read(NativeAccess access, int kind, long address) {
        switch (kind) {
            case DescribedKind.U32:
                return Integer.toUnsignedLong(access.getInt(address, 0));
            case DescribedKind.F64:
                return access.getDouble(address, 0);
            default:
                return access.getLong(address, 0);
        }
    }

    /** Writes the scalar of {@code kind}, its {@link #bits} already checked, at {@code address}. */
    static void write(NativeAccess access, int kind, long address, long bits) {
        if (kind == DescribedKind.U32) {
            access.putInt(address, 0, (int) bits);
        } else {
            access.putLong(address, 0, bits);
        }
    }

    /**
     * {@code text} as UTF-8.
     *
     * @throws IllegalArgumentException for a text holding a NUL, which no C text can carry
     */
    static byte[] utf8(String text, String where) {
        if (text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(where + " holds a NUL character, which no C text can carry");
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A count the provider answered, as a Java length.
     *
     * @throws IllegalStateException for one longer than a Java array holds
     */
    static int count(long count) {
        if (count < 0 || count > Integer.MAX_VALUE - 8) {
            throw new IllegalStateException("the provider answered " + Long.toUnsignedString(count)
                    + " elements, more than a Java array holds");
        }
        return (int) count;
    }

    static String described(Object value) {
        return value == null ? "null" : "a " + value.getClass().getName();
    }
}
