package com.weivel.core;

/** A C type a native function takes or answers, as the native seam carries it: in a {@code long}, or as nothing. */
public enum NativeType {
    /** No value: a function's return of {@code void}, carried back as 0. */
    VOID,
    /** A 32-bit integer, narrowed from the {@code long} it is handed in and widened back. */
    INT,
    /** A 64-bit integer. */
    LONG,
    /** A pointer, carried as its address; 0 is {@code NULL}. */
    ADDRESS
}
