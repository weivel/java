package com.weivel.core;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CoreFunctionTest {
    private static final Pattern DECLARATION =
            Pattern.compile("WEIVEL_API\\s+weivel_status\\s+(weivel_\\w+)\\s*\\(([^)]*)\\)\\s*;");

    private record HeaderFunction(String symbol, List<String> parameterTypes) {
    }

    @Test
    void everyFunctionTheHeaderDeclaresIsInTheTableInTheHeadersOrder() throws IOException {
        List<String> declared = headerFunctions().stream().map(HeaderFunction::symbol).toList();
        List<String> tabled = Arrays.stream(CoreFunction.values()).map(CoreFunction::symbol).toList();

        assertFalse(declared.isEmpty(), "the header declared no function the test could read");
        assertEquals(declared, tabled);
    }

    @Test
    void everyFunctionAnswersAStatusAndTakesTheParametersTheHeaderDeclares() throws IOException {
        for (HeaderFunction function : headerFunctions()) {
            CoreFunction tabled = tabledAs(function.symbol());
            List<MemoryLayout> expected =
                    function.parameterTypes().stream().<MemoryLayout>map(CoreFunctionTest::carrierOf).toList();

            assertEquals(Optional.of(JAVA_INT), tabled.descriptor().returnLayout(), function.symbol());
            assertEquals(expected, tabled.descriptor().argumentLayouts(), function.symbol());
        }
    }

    private static CoreFunction tabledAs(String symbol) {
        return Arrays.stream(CoreFunction.values())
                .filter(function -> function.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError(symbol + " is not in the table"));
    }

    private static List<HeaderFunction> headerFunctions() throws IOException {
        String header = Files.readString(NativeFiles.coreHeader(), StandardCharsets.UTF_8);
        Matcher matcher = DECLARATION.matcher(header);
        List<HeaderFunction> functions = new ArrayList<>();
        while (matcher.find()) {
            functions.add(new HeaderFunction(matcher.group(1), parameterTypes(matcher.group(2))));
        }
        return functions;
    }

    private static List<String> parameterTypes(String parameters) {
        return Arrays.stream(parameters.split(","))
                .map(parameter -> parameter.trim().replaceAll("\\s+", " "))
                .map(CoreFunctionTest::withoutName)
                .toList();
    }

    private static String withoutName(String parameter) {
        int lastSpace = parameter.lastIndexOf(' ');
        String type = parameter.substring(0, lastSpace);
        String name = parameter.substring(lastSpace + 1);
        return name.startsWith("*") ? type + " " + name.replaceAll("[^*]", "") : type;
    }

    private static ValueLayout carrierOf(String parameterType) {
        if (parameterType.contains("*")) {
            return ADDRESS;
        }
        return switch (parameterType) {
            case "uint32_t" -> JAVA_INT;
            case "int64_t", "uint64_t", "weivel_handle" -> JAVA_LONG;
            default -> throw new AssertionError("the test knows no carrier for the C type " + parameterType);
        };
    }
}
