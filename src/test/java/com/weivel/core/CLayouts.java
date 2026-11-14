package com.weivel.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Every struct a C {@code layouts} program prints, as the C compiler laid it out.
 *
 * @param structs each struct by name
 */
public record CLayouts(Map<String, CStruct> structs) {
    private static final long PROGRAM_TIMEOUT_SECONDS = 60;

    /**
     * One struct's fields by name, each a dotted name for a member of a union field, and its size.
     *
     * @param fields each field by name
     * @param size the struct's {@code sizeof}
     */
    public record CStruct(Map<String, CField> fields, long size) {
    }

    /**
     * Where one field lies in its struct.
     *
     * @param offset its offset in bytes
     * @param size its size in bytes
     */
    public record CField(long offset, long size) {
    }

    public static CLayouts printedBy(Path program) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(program.toString()).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(PROGRAM_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException(program + " did not print its layouts: " + output);
        }
        return parse(output);
    }

    private static CLayouts parse(String output) {
        Map<String, Map<String, CField>> fields = new LinkedHashMap<>();
        Map<String, Long> sizes = new LinkedHashMap<>();
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                readLine(line.trim().split(" "), fields, sizes);
            }
        }
        Map<String, CStruct> structs = new LinkedHashMap<>();
        fields.forEach((name, structFields) -> structs.put(name, new CStruct(structFields, sizes.getOrDefault(name, -1L))));
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
}
