package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Reads exactly one JSON value, refusing anything RFC 8259 does not allow.
 *
 * @implNote {@code JsonParser.parseString} and {@code Gson.fromJson} both switch the reader to
 * lenient mode, which accepts unquoted strings, single quotes and trailing text, so text a library
 * or a release got wrong would read as some other value. The adapter reads with the reader's own
 * setting, which is strict.
 */
final class StrictJson {

    private static final TypeAdapter<JsonElement> ELEMENT = new Gson().getAdapter(JsonElement.class);
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private StrictJson() {
    }

    /** {@code new String(bytes, UTF_8)} replaces a malformed sequence with U+FFFD; this refuses it instead. */
    static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    /**
     * @implNote Gson's reader skips a leading byte order mark on its own (measured), and TypeScript's
     * {@code TextDecoder} strips one by default, so a marked answer or {@code library.json} is
     * refused here explicitly, as TypeScript refuses it by keeping the mark: RFC 8259 forbids a
     * producer to add one, and both hosts must read the same bytes the same way.
     */
    static JsonElement parse(String text) throws IOException {
        if (text.startsWith(BYTE_ORDER_MARK)) {
            throw new MalformedJsonException("a byte order mark precedes the JSON value");
        }
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(false);
        try {
            JsonElement element = ELEMENT.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new MalformedJsonException("text follows the JSON value at " + reader.getPath());
            }
            return element;
        } catch (IllegalStateException | NumberFormatException malformed) {
            throw new MalformedJsonException(malformed.getMessage());
        }
    }
}
