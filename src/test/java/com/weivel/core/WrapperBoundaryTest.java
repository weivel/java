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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Only the wrapper reaches the core's functions: no source of the {@code core} package, Java 22 sources included, but
 * the wrapper's and the native seam's names the table of the core's functions, a call's crossing, the binary's
 * functions or the seam's types, or invokes a method handle, so every crossing into the core goes through the one
 * method of the wrapper that makes it.
 *
 * <p>The check reads identifiers, with comments and literals taken out, so it cannot see a handle reached by
 * reflection or by a name built at run time, nor a source outside this package; it holds the boundary against the
 * plain mistake of calling the table from the wrong class.
 */
class WrapperBoundaryTest {
    private static final Set<String> WRAPPER_SOURCES = setOf("CoreFunction.java", "CoreFunctions.java",
            "CoreBinary.java", "Wrapper.java", "CoreCalls.java", "CoreCall.java");
    private static final Set<String> SEAM_SOURCES = setOf("NativeAccess.java", "NativeBackends.java",
            "OpenLibrary.java", "NativeFunction.java", "NativeSignature.java", "NativeType.java", "NativeArena.java",
            "NativeRegion.java", "JnaAccess.java", "FfmAccess.java", "NativeBytesFunction.java", "LibraryBinary.java",
            "LibraryFunction.java");
    private static final Set<String> FORBIDDEN = setOf("CoreFunction", "CoreFunctions", "MethodHandle",
            "MethodHandles", "invoke", "invokeExact", "invokeWithArguments", "downcallHandle", "functions", "crossing",
            "cross", "NativeAccess", "NativeBackends", "OpenLibrary", "NativeFunction", "NativeSignature", "NativeType",
            "JnaAccess", "FfmAccess");
    private static final Pattern COMMENTS_AND_LITERALS = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*|\"\"\"(?s:.*?)\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'",
            Pattern.DOTALL);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    @Test
    void noSourceButTheWrappersAndTheSeamNamesTheTableTheSeamOrInvokesAHandle() throws IOException {
        List<Path> sources = coreSources();
        List<String> offences = new ArrayList<>();
        for (Path source : sources) {
            String fileName = source.getFileName().toString();
            if (!WRAPPER_SOURCES.contains(fileName) && !SEAM_SOURCES.contains(fileName)) {
                identifiersOf(source).stream().filter(FORBIDDEN::contains).distinct()
                        .forEach(name -> offences.add(source.getFileName() + " names " + name));
            }
        }

        assertTrue(sources.stream().anyMatch(source -> source.endsWith("Core.java")), "no source was read");
        assertTrue(sources.stream().anyMatch(source -> source.endsWith("FfmAccess.java")),
                "no Java 22 source was read");
        assertEquals(Collections.emptyList(), offences);
    }

    @Test
    void theCheckFindsAnInvocationOutsideTheWrapper() {
        String offending = "class Elsewhere { int call(Object table) throws Throwable { "
                + "return (int) table.handle(\"x\").invokeExact(); } }";

        String bypassing = "class Elsewhere { Object call(CoreBinary binary, CoreCall<?> call) throws Throwable { "
                + "return call.crossing().cross(binary.functions(), null, null, null); } }";

        assertTrue(identifiersIn(offending).contains("invokeExact"));
        assertTrue(identifiersIn(bypassing).containsAll(Arrays.asList("crossing", "cross", "functions")));
        assertTrue(identifiersIn("/* invokeExact */ String text = \"invokeExact\";").stream()
                .noneMatch(FORBIDDEN::contains));
    }

    private static List<Path> coreSources() throws IOException {
        List<Path> sources = new ArrayList<>(javaFilesIn(Paths.get(property("weivel.test.coreSources"))));
        sources.addAll(javaFilesIn(Paths.get(property("weivel.test.mainSources"), "java22", "com", "weivel", "core")));
        return sources;
    }

    private static List<Path> javaFilesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    private static String property(String name) {
        String location = System.getProperty(name);
        if (location == null) {
            throw new IllegalStateException(name + " is not set. Run the tests through Gradle.");
        }
        return location;
    }

    private static Set<String> setOf(String... names) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
    }

    private static List<String> identifiersOf(Path source) throws IOException {
        return identifiersIn(new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
    }

    private static List<String> identifiersIn(String text) {
        Matcher identifiers = IDENTIFIER.matcher(COMMENTS_AND_LITERALS.matcher(text).replaceAll(" "));
        List<String> found = new ArrayList<>();
        while (identifiers.find()) {
            found.add(identifiers.group());
        }
        return found;
    }
}
