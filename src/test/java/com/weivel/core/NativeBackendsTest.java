package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NativeBackendsTest {
    private static final List<String> BACKEND_SOURCES = Arrays.asList(
            "java/com/weivel/core/JnaAccess.java", "java22/com/weivel/core/FfmAccess.java");

    @Test
    void theBackendThisJavaSelectsIsTheOneTheBuildExpects() {
        assertEquals(property("weivel.test.backend"), NativeBackends.current().name());
    }

    @Test
    void onlyTheBackendsNameTheForeignFunctionApiOrJna() throws IOException {
        Path mainSources = Paths.get(property("weivel.test.mainSources"));
        List<Path> sources;
        try (Stream<Path> files = Files.walk(mainSources)) {
            sources = files.filter(file -> file.toString().endsWith(".java")).collect(Collectors.toList());
        }
        List<String> offences = new ArrayList<>();
        for (Path source : sources) {
            String relative = mainSources.relativize(source).toString().replace('\\', '/');
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            boolean namesABackend = text.contains("java.lang.foreign") || text.contains("com.sun.jna");
            if (namesABackend && !BACKEND_SOURCES.contains(relative)) {
                offences.add(relative);
            }
        }

        assertTrue(sources.size() > BACKEND_SOURCES.size(), "no source was read");
        assertEquals(new ArrayList<String>(), offences);
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        if (value == null) {
            throw new IllegalStateException(name + " is not set. Run the tests through Gradle, which sets it.");
        }
        return value;
    }
}
