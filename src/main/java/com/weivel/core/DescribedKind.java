package com.weivel.core;

/**
 * The kinds a described member or parameter holds, numbered as the standard's {@code weivel/description.h} numbers
 * {@code weivel_standard_described_kind}, and how each reaches Java through a {@link BoundDefinition}.
 */
public final class DescribedKind {
    /** {@code uint32_t}, as a {@link Long} from 0 to 2^32 - 1, an enum's value included. */
    public static final int U32 = 1;
    /**
     * {@code uint64_t}, as a {@link Long} carrying its 64 bits: one at or above 2^63 reads negative, so it is read with
     * {@link Long#toUnsignedString(long)} or compared with {@link Long#compareUnsigned(long, long)}.
     */
    public static final int U64 = 2;
    /** {@code int64_t}, as a {@link Long}. */
    public static final int I64 = 3;
    /** {@code double}, as a {@link Double}. */
    public static final int F64 = 4;
    /** {@code size_t}, as a {@link Long} under the unsigned rule of {@link #U64}. */
    public static final int SIZE = 5;
    /** {@code const char *}, as a {@link String}, or null for NULL. */
    public static final int TEXT = 6;
    /** {@code const char *const *} with its count, as a {@code List} of strings and nulls. */
    public static final int TEXTS = 7;
    /** {@code const uint8_t *} with its count, as a {@code byte[]}. */
    public static final int BYTES = 8;
    /** A struct held by value, as a {@code Map} keyed by member name. */
    public static final int STRUCT = 9;
    /** {@code const T *} to one struct or value, as what it points at, or left out for NULL. */
    public static final int POINTER = 10;
    /** {@code const T *} with its count, as a {@code List} of its elements. */
    public static final int ARRAY = 11;
    /**
     * The standard's value tree, as the natural Java value: null, a {@link Boolean}, a {@link Double}, a
     * {@link String}, a {@code List} or a {@code Map} keyed by text; left out where it is of kind NONE.
     */
    public static final int VALUE = 12;
    /** An opaque {@code T *} the provider made, as a {@link DescribedHandle}. */
    public static final int HANDLE = 13;
    /** {@code void *}: the definition's own context, never written or read by a binding. */
    public static final int CONTEXT = 14;
    /** {@code void *}: what an answer's storage holds, never written or read by a binding. */
    public static final int STORAGE = 15;
    /**
     * {@code const T *} to a definition the description's {@code definitions} describe, as the
     * {@link BoundDefinition} bound for it, which lives as long as the answer that carried it.
     */
    public static final int DEFINITION = 16;
    /** The highest kind this binding knows; a description holding a higher one is refused whole. */
    public static final int LAST = DEFINITION;

    private DescribedKind() {
    }
}
