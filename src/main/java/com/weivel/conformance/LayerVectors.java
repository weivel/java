package com.weivel.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads every layer vector under a directory, at any depth, through the standard's JSON reader. */
final class LayerVectors {
    private static final String SUFFIX = ".json";

    private LayerVectors() {
    }

    static List<LayerVector> read(StandardJson json, Path directory) {
        List<LayerVector> vectors = new ArrayList<>();
        for (Path file : vectorFiles(directory)) {
            vectors.add(vector(json, file, idOf(directory, file)));
        }
        vectors.sort(Comparator.comparing(LayerVector::id));
        return Collections.unmodifiableList(vectors);
    }

    private static List<Path> vectorFiles(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith(SUFFIX))
                    .collect(Collectors.toList());
        } catch (IOException unread) {
            throw new UncheckedIOException("the layer vectors under " + directory + " could not be listed", unread);
        }
    }

    /** A vector's id is its path under the directory without {@code .json}, its separators written {@code /}. */
    private static String idOf(Path directory, Path file) {
        List<String> parts = new ArrayList<>();
        for (Path part : directory.relativize(file)) {
            parts.add(part.toString());
        }
        String joined = String.join("/", parts);
        return joined.substring(0, joined.length() - SUFFIX.length());
    }

    private static LayerVector vector(StandardJson json, Path file, String id) {
        byte[] document;
        try {
            document = Files.readAllBytes(file);
        } catch (IOException unread) {
            throw new UncheckedIOException("the layer vector " + file + " could not be read", unread);
        }
        String where = "the layer vector " + id;
        Map<String, Object> vector = Shapes.object(where, json.read(document, where));
        Shapes.refuseUnknownKeys(where, vector, "id", "op", "covers", "given", "then");
        String named = Shapes.text(where, vector, "id");
        if (!named.equals(id)) {
            throw new IllegalArgumentException(where + " names itself " + named + ", which is not its path");
        }
        return new LayerVector(id, Shapes.text(where, vector, "op"), Shapes.object(where, vector, "given"),
                Shapes.object(where, vector, "then"));
    }
}
