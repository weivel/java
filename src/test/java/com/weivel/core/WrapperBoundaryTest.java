package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Only the wrapper reaches the core's functions: no source of the {@code core} package but the wrapper's names the
 * table of downcall handles, a call's crossing, or the binary's functions, or invokes a method handle, so every
 * crossing into the core goes through the one method of the wrapper that makes it.
 *
 * <p>The check reads identifiers, with comments and literals taken out, so it cannot see a handle reached by
 * reflection or by a name built at run time, nor a source outside this package; it holds the boundary against the
 * plain mistake of calling the table from the wrong class.
 */
class WrapperBoundaryTest {
    private static final Set<String> WRAPPER_SOURCES = Set.of("CoreFunction.java", "CoreFunctions.java",
            "CoreBinary.java", "Wrapper.java", "CoreCalls.java", "CoreCall.java");
    private static final Set<String> FORBIDDEN = Set.of("CoreFunction", "CoreFunctions", "MethodHandle",
            "MethodHandles", "invoke", "invokeExact", "invokeWithArguments", "downcallHandle", "functions",
            "crossing", "cross");
    private static final Pattern COMMENTS_AND_LITERALS = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*|\"\"\"(?s:.*?)\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'", Pattern.DOTALL);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    @Test
    void noSourceButTheWrappersNamesTheTableOrInvokesAHandle() throws IOException {
        List<Path> sources = coreSources();
        List<String> offences = new ArrayList<>();
        for (Path source : sources) {
            if (!WRAPPER_SOURCES.contains(source.getFileName().toString())) {
                identifiersOf(source).stream().filter(FORBIDDEN::contains).distinct()
                        .forEach(name -> offences.add(source.getFileName() + " names " + name));
            }
        }

        assertTrue(sources.stream().anyMatch(source -> source.endsWith("Core.java")), "no source was read");
        assertEquals(List.of(), offences);
    }

    @Test
    void theCheckFindsAnInvocationOutsideTheWrapper() {
        String offending = "class Elsewhere { int call(Object table) throws Throwable { "
                + "return (int) table.handle(\"x\").invokeExact(); } }";

        String bypassing = "class Elsewhere { Object call(CoreBinary binary, CoreCall<?> call) throws Throwable { "
                + "return call.crossing().cross(binary.functions(), null, null, null); } }";

        assertTrue(identifiersIn(offending).contains("invokeExact"));
        assertTrue(identifiersIn(bypassing).containsAll(List.of("crossing", "cross", "functions")));
        assertTrue(identifiersIn("/* invokeExact */ String text = \"invokeExact\";").stream()
                .noneMatch(FORBIDDEN::contains));
    }

    private static List<Path> coreSources() throws IOException {
        String location = System.getProperty("weivel.test.coreSources");
        if (location == null) {
            throw new IllegalStateException("weivel.test.coreSources is not set. Run the tests through Gradle.");
        }
        try (Stream<Path> files = Files.list(Path.of(location))) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<String> identifiersOf(Path source) throws IOException {
        return identifiersIn(Files.readString(source, StandardCharsets.UTF_8));
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
