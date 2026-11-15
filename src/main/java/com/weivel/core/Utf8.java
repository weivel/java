package com.weivel.core;

/**
 * Encodes a text as UTF-8 into an array its caller keeps, byte for byte as {@code String.getBytes(UTF_8)} would, a
 * surrogate without its pair written as {@code '?'}, so encoding a text allocates nothing once the array is large
 * enough.
 */
final class Utf8 {
    private static final byte UNPAIRED_SURROGATE = '?';

    private Utf8() {
    }

    /** The bytes {@code text} takes encoded. */
    static int encodedLength(String text) {
        int length = 0;
        for (int index = 0; index < text.length(); index++) {
            char unit = text.charAt(index);
            if (unit < 0x80) {
                length += 1;
            } else if (unit < 0x800) {
                length += 2;
            } else if (startsAPair(text, index)) {
                length += 4;
                index++;
            } else if (Character.isSurrogate(unit)) {
                length += 1;
            } else {
                length += 3;
            }
        }
        return length;
    }

    /** Writes {@code text} encoded from the start of {@code into}, which holds at least its encoded length. */
    static void encode(String text, byte[] into) {
        int at = 0;
        for (int index = 0; index < text.length(); index++) {
            char unit = text.charAt(index);
            if (unit < 0x80) {
                into[at++] = (byte) unit;
            } else if (unit < 0x800) {
                into[at++] = (byte) (0xC0 | unit >> 6);
                into[at++] = continuation(unit);
            } else if (startsAPair(text, index)) {
                int codePoint = Character.toCodePoint(unit, text.charAt(++index));
                into[at++] = (byte) (0xF0 | codePoint >> 18);
                into[at++] = continuation(codePoint >> 12);
                into[at++] = continuation(codePoint >> 6);
                into[at++] = continuation(codePoint);
            } else if (Character.isSurrogate(unit)) {
                into[at++] = UNPAIRED_SURROGATE;
            } else {
                into[at++] = (byte) (0xE0 | unit >> 12);
                into[at++] = continuation(unit >> 6);
                into[at++] = continuation(unit);
            }
        }
    }

    private static boolean startsAPair(String text, int index) {
        return Character.isHighSurrogate(text.charAt(index)) && index + 1 < text.length()
                && Character.isLowSurrogate(text.charAt(index + 1));
    }

    private static byte continuation(int bits) {
        return (byte) (0x80 | bits & 0x3F);
    }
}
