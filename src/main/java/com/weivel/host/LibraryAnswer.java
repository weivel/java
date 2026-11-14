package com.weivel.host;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * What one call through a {@link LibraryService} answered: the function's result, its failure, or that the library
 * was unloaded before the call, so nothing was called. Each case is a final nested class.
 */
public abstract class LibraryAnswer {
    private LibraryAnswer() {
    }

    static LibraryAnswer of(int status, byte[] body) {
        return status == 0 ? new Returned(body) : new Failed(status, body);
    }

    /** Status 0: the function's result. */
    public static final class Returned extends LibraryAnswer {
        private final byte[] bytes;

        private Returned(byte[] bytes) {
            this.bytes = bytes;
        }

        /**
         * Answers {@code bytes}.
         *
         * @return the result, the one copy made of the library's, which the caller now owns; never inspected
         */
        public byte[] bytes() {
            return bytes;
        }

        @Override
        public String toString() {
            return "Returned[" + bytes.length + " bytes]";
        }
    }

    /** Any other status: a failure, carrying the status and the body the library answered with it. */
    public static final class Failed extends LibraryAnswer {
        private final int status;
        private final byte[] body;
        private final Optional<String> text;

        Failed(int status, byte[] body) {
            this.status = status;
            this.body = Objects.requireNonNull(body, "body");
            this.text = utf8(body);
        }

        /**
         * Answers {@code status}.
         *
         * @return the status the function answered, never 0
         */
        public int status() {
            return status;
        }

        /**
         * Answers {@code body}.
         *
         * @return the body as bytes, the caller's own; empty when the library answered none
         */
        public byte[] body() {
            return body;
        }

        /**
         * Answers {@code text}.
         *
         * @return the body read as UTF-8, the English text saying why; empty when the body is not well-formed UTF-8,
         *     so it is only bytes; an empty body is well-formed and reads as an empty text
         */
        public Optional<String> text() {
            return text;
        }

        @Override
        public String toString() {
            return "Failed[status=" + status + ", text=" + text + "]";
        }

        private static Optional<String> utf8(byte[] body) {
            try {
                return Optional.of(StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(body)).toString());
            } catch (CharacterCodingException notUtf8) {
                return Optional.empty();
            }
        }
    }

    /** The library was unloaded, by its unload or by the host's destroy, before the call: nothing was called. */
    public static final class Unloaded extends LibraryAnswer {
        Unloaded() {
        }

        @Override
        public String toString() {
            return "Unloaded";
        }
    }
}
