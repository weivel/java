package com.weivel.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The host works without the standard: no class of {@code core}, {@code handles}, {@code timer}, {@code context} or
 * {@code host} depends on a class of {@code standard}, {@code conformance} or {@code benchmarks}, as {@code jdeps}
 * reads the built jar's class files, the base classes and the Java 22 ones alike.
 *
 * @implNote {@code jdeps} is run as the JDK 25 toolchain's executable, whichever JVM runs the test, so the check runs
 * on the JNA backend's Java 8 suite too, where no tool provider exists.
 */
class LayerIndependenceTest {
    private static final List<String> LAYER = Arrays.asList("core", "handles", "timer", "context", "host");
    private static final List<String> OPTIONAL = Arrays.asList("standard", "conformance", "benchmarks");
    private static final long JDEPS_TIMEOUT_SECONDS = 120;

    @Test
    void noModuleTheHostNeedsDependsOnAnOptionalOne() throws IOException, InterruptedException {
        List<String[]> edges = new ArrayList<>();
        edges.addAll(classEdges("base"));
        edges.addAll(classEdges("22"));

        List<String> offending = new ArrayList<>();
        for (String[] edge : edges) {
            if (LAYER.contains(moduleOf(edge[0])) && OPTIONAL.contains(moduleOf(edge[1]))) {
                offending.add(edge[0] + " -> " + edge[1]);
            }
        }

        assertTrue(edges.stream().anyMatch(edge -> edge[0].equals("com.weivel.standard.Standard")
                && edge[1].equals("com.weivel.host.LibraryDeclaration")), "jdeps reported none of the edges it should");
        assertTrue(edges.stream().anyMatch(edge -> edge[0].equals("com.weivel.core.NativeBackends")
                && edge[1].equals("com.weivel.core.FfmAccess")), "jdeps read no Java 22 class");
        assertEquals(Collections.<String>emptyList(), offending);
    }

    private static String moduleOf(String className) {
        String[] parts = className.split("\\.");
        return parts.length > 3 && parts[0].equals("com") && parts[1].equals("weivel") ? parts[2] : "";
    }

    /** Every edge between two of the layer's classes {@code jdeps} reports, as {source, target}. */
    private static List<String[]> classEdges(String release) throws IOException, InterruptedException {
        Path jar = StandardFixture.file("weivel.test.jar");
        Process process = new ProcessBuilder(StandardFixture.file("weivel.test.jdeps").toString(),
                "--multi-release", release, "-verbose:class", "-filter:none", jar.toString())
                .redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(allBytesOf(stream), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(JDEPS_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("jdeps did not read " + jar + ": " + output);
        }
        List<String[]> edges = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String[] words = line.trim().split("\\s+");
            if (words.length >= 3 && words[1].equals("->")) {
                String source = unversioned(words[0]);
                String target = unversioned(words[2]);
                if (source.startsWith("com.weivel.") && target.startsWith("com.weivel.")) {
                    edges.add(new String[] {source, target});
                }
            }
        }
        return edges;
    }

    /** A class name without the {@code 22/} prefix jdeps gives a class of a multi-release jar's versioned part. */
    private static String unversioned(String className) {
        return className.replaceFirst("^[0-9]+/", "");
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
}
