package com.weivel.core;

import static com.weivel.core.NativeType.ADDRESS;
import static com.weivel.core.NativeType.INT;
import static com.weivel.core.NativeType.LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CoreFunctionTest {
    private static final Pattern DECLARATION =
            Pattern.compile("WEIVEL_API\\s+weivel_status\\s+(weivel_\\w+)\\s*\\(([^)]*)\\)\\s*;");

    private static final class HeaderFunction {
        private final String symbol;
        private final List<String> parameterTypes;

        private HeaderFunction(String symbol, List<String> parameterTypes) {
            this.symbol = symbol;
            this.parameterTypes = parameterTypes;
        }

        public String symbol() {
            return symbol;
        }

        public List<String> parameterTypes() {
            return parameterTypes;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HeaderFunction)) {
                return false;
            }
            HeaderFunction that = (HeaderFunction) other;
            return Objects.equals(symbol, that.symbol)
                    && Objects.equals(parameterTypes, that.parameterTypes);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(symbol);
            hash = 31 * hash + Objects.hashCode(parameterTypes);
            return hash;
        }

        @Override
        public String toString() {
            return "HeaderFunction[symbol=" + symbol + ", parameterTypes=" + parameterTypes + "]";
        }
    }

    @Test
    void everyFunctionTheHeaderDeclaresIsInTheTableInTheHeadersOrder() throws IOException {
        List<String> declared = headerFunctions().stream().map(HeaderFunction::symbol).collect(Collectors.toList());
        List<String> tabled =
                Arrays.stream(CoreFunction.values()).map(CoreFunction::symbol).collect(Collectors.toList());

        assertFalse(declared.isEmpty(), "the header declared no function the test could read");
        assertEquals(declared, tabled);
    }

    @Test
    void everyFunctionAnswersAStatusAndTakesTheParametersTheHeaderDeclares() throws IOException {
        for (HeaderFunction function : headerFunctions()) {
            CoreFunction tabled = tabledAs(function.symbol());
            List<NativeType> expected =
                    function.parameterTypes().stream().map(CoreFunctionTest::carrierOf).collect(Collectors.toList());

            assertEquals(INT, tabled.signature().returned(), function.symbol());
            assertEquals(expected, tabled.signature().parameters(), function.symbol());
        }
    }

    private static CoreFunction tabledAs(String symbol) {
        return Arrays.stream(CoreFunction.values())
                .filter(function -> function.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError(symbol + " is not in the table"));
    }

    private static List<HeaderFunction> headerFunctions() throws IOException {
        String header = new String(Files.readAllBytes(NativeFiles.coreHeader()), StandardCharsets.UTF_8);
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
                .collect(Collectors.toList());
    }

    private static String withoutName(String parameter) {
        int lastSpace = parameter.lastIndexOf(' ');
        String type = parameter.substring(0, lastSpace);
        String name = parameter.substring(lastSpace + 1);
        return name.startsWith("*") ? type + " " + name.replaceAll("[^*]", "") : type;
    }

    private static NativeType carrierOf(String parameterType) {
        if (parameterType.contains("*")) {
            return ADDRESS;
        }
        switch (parameterType) {
            case "uint32_t":
                return INT;
            case "int64_t":
            case "uint64_t":
            case "weivel_handle":
                return LONG;
            default:
                throw new AssertionError("the test knows no carrier for the C type " + parameterType);
        }
    }
}
