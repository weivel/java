package com.weivel.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Every struct a C {@code layouts} program prints, as the C compiler laid it out.
 */
public final class CLayouts {
    private final Map<String, CStruct> structs;

    /**
     * Makes the value.
     *
     * @param structs each struct by name
     */
    public CLayouts(Map<String, CStruct> structs) {
        this.structs = structs;
    }

    /**
     * Answers {@code structs}.
     *
     * @return each struct by name
     */
    public Map<String, CStruct> structs() {
        return structs;
    }

    private static final long PROGRAM_TIMEOUT_SECONDS = 60;

    /**
     * One struct's fields by name, each a dotted name for a member of a union field, and its size.
     */
    public static final class CStruct {
        private final Map<String, CField> fields;
        private final long size;

        /**
         * Makes the value.
         *
         * @param fields each field by name
         * @param size the struct's {@code sizeof}
         */
        public CStruct(Map<String, CField> fields, long size) {
            this.fields = fields;
            this.size = size;
        }

        /**
         * Answers {@code fields}.
         *
         * @return each field by name
         */
        public Map<String, CField> fields() {
            return fields;
        }

        /**
         * Answers {@code size}.
         *
         * @return the struct's {@code sizeof}
         */
        public long size() {
            return size;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CStruct)) {
                return false;
            }
            CStruct that = (CStruct) other;
            return Objects.equals(fields, that.fields)
                    && size == that.size;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(fields);
            hash = 31 * hash + Long.hashCode(size);
            return hash;
        }

        @Override
        public String toString() {
            return "CStruct[fields=" + fields + ", size=" + size + "]";
        }
    }

    /**
     * Where one field lies in its struct.
     */
    public static final class CField {
        private final long offset;
        private final long size;

        /**
         * Makes the value.
         *
         * @param offset its offset in bytes
         * @param size its size in bytes
         */
        public CField(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }

        /**
         * Answers {@code offset}.
         *
         * @return its offset in bytes
         */
        public long offset() {
            return offset;
        }

        /**
         * Answers {@code size}.
         *
         * @return its size in bytes
         */
        public long size() {
            return size;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CField)) {
                return false;
            }
            CField that = (CField) other;
            return offset == that.offset
                    && size == that.size;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(offset);
            hash = 31 * hash + Long.hashCode(size);
            return hash;
        }

        @Override
        public String toString() {
            return "CField[offset=" + offset + ", size=" + size + "]";
        }
    }

    public static CLayouts printedBy(Path program) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(program.toString()).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(allBytesOf(stream), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(PROGRAM_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException(program + " did not print its layouts: " + output);
        }
        return parse(output);
    }

    private static byte[] allBytesOf(InputStream stream) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            bytes.write(chunk, 0, read);
        }
        return bytes.toByteArray();
    }

    private static CLayouts parse(String output) {
        Map<String, Map<String, CField>> fields = new LinkedHashMap<>();
        Map<String, Long> sizes = new LinkedHashMap<>();
        for (String line : output.split("\\R")) {
            if (!line.trim().isEmpty()) {
                readLine(line.trim().split(" "), fields, sizes);
            }
        }
        Map<String, CStruct> structs = new LinkedHashMap<>();
        fields.forEach((name, structFields) -> structs.put(name, new CStruct(structFields, sizes.getOrDefault(name,
                -1L))));
        return new CLayouts(structs);
    }

    private static void readLine(String[] words, Map<String, Map<String, CField>> fields, Map<String, Long> sizes) {
        if (words.length == 3 && words[1].equals("sizeof")) {
            sizes.put(words[0], Long.parseLong(words[2]));
        } else if (words.length == 4) {
            fields.computeIfAbsent(words[0], name -> new LinkedHashMap<>())
                    .put(words[1], new CField(Long.parseLong(words[2]), Long.parseLong(words[3])));
        } else {
            throw new IllegalStateException("a layouts line the test cannot read: " + String.join(" ", words));
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CLayouts)) {
            return false;
        }
        CLayouts that = (CLayouts) other;
        return Objects.equals(structs, that.structs);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(structs);
        return hash;
    }

    @Override
    public String toString() {
        return "CLayouts[structs=" + structs + "]";
    }
}
