package com.weivel.core;

import com.weivel.host.LibraryDeclaration;
import com.weivel.standard.Standard;
import com.weivel.standard.StandardAnswer;
import com.weivel.standard.StandardBinary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The standard's described test library and its declarations, read by the standard's own reader from the files the
 * standard ships beside it, so no test names a type of the library; and the standard's description check.
 */
public final class DescribedFixture {
    private static final Standard STANDARD = Standard.of(CoreProbes.ok(StandardBinary.load(
            NativeFiles.standardBinary())));

    private DescribedFixture() {
    }

    /**
     * The described library's binary.
     *
     * @return its path
     */
    public static Path binary() {
        return NativeFiles.describedBinary();
    }

    /**
     * The standard the check comes from.
     *
     * @return it, loaded once for the test run
     */
    public static Standard standard() {
        return STANDARD;
    }

    /**
     * Whether the suite runs on JNA, which binds no description yet.
     *
     * @return whether it does
     */
    public static boolean onJna() {
        return "jna".equals(System.getProperty("weivel.test.backend"));
    }

    /**
     * The description with its structs listed in the reverse order, so a struct embedding another comes before it
     * whenever the library listed it after.
     *
     * @param description a description as read
     * @return the same description, its types reversed
     */
    public static Description withTypesReversed(Description description) {
        List<Description.Type> types = new ArrayList<>(description.types());
        Collections.reverse(types);
        return new Description(description.definition(), types, description.enums(), description.operations());
    }

    /**
     * One of the library's declarations, as the standard reads it.
     *
     * @param file its file name beside the library's sources, {@code library.json} or a broken variant
     * @return the declaration
     */
    public static LibraryDeclaration declaration(String file) {
        Path path = Paths.get(System.getProperty("weivel.test.describedDirectory")).resolve(file);
        String json;
        try {
            json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        StandardAnswer<LibraryDeclaration> read = STANDARD.readLibrary(json);
        return read.value().orElseThrow(() -> new IllegalStateException("the standard refused " + path + ": "
                + read));
    }
}
