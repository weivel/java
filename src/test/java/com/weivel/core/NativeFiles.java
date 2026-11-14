package com.weivel.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** The files the build hands the tests, each named by the system property Gradle sets for it. */
final class NativeFiles {
    private NativeFiles() {
    }

    static Path coreBinary() {
        return existing("weivel.test.coreBinary");
    }

    static Path standardBinary() {
        return existing("weivel.test.standardBinary");
    }

    static Path echoBinary() {
        return existing("weivel.test.echoBinary");
    }

    static Path coreLayouts() {
        return existing("weivel.test.coreLayouts");
    }

    static Path coreHeader() {
        return existing("weivel.test.coreHeader");
    }

    private static Path existing(String property) {
        String location = System.getProperty(property);
        if (location == null) {
            throw new IllegalStateException(property + " is not set. Run the tests through Gradle, which sets it.");
        }
        Path path = Paths.get(location);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(property + " names " + path + ", which does not exist.");
        }
        return path;
    }
}
