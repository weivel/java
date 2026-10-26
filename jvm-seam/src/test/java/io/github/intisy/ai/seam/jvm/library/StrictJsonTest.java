package io.github.intisy.ai.seam.jvm.library;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class StrictJsonTest {

    private static final byte[] BYTE_ORDER_MARK = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};

    private static byte[] withByteOrderMark(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] marked = new byte[BYTE_ORDER_MARK.length + body.length];
        System.arraycopy(BYTE_ORDER_MARK, 0, marked, 0, BYTE_ORDER_MARK.length);
        System.arraycopy(body, 0, marked, BYTE_ORDER_MARK.length, body.length);
        return marked;
    }

    @Test
    void refusesALibraryAnswerThatStartsWithAByteOrderMarkAsNotJson() {
        assertThrows(IOException.class, () -> StrictJson.parse(StrictJson.decodeUtf8(withByteOrderMark("{}"))));
    }
}
