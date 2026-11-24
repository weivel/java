package com.weivel.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ServicesTest {
    private static final Pattern OPERATION = Pattern.compile("\\(\\s*\\*\\s*(\\w+)\\s*\\)\\s*\\(");

    @Test
    void eachDefinitionCarriesExactlyTheOperationsServicesHDefines() throws IOException {
        String header = withoutComments(new String(Files.readAllBytes(
                StandardFixture.file("weivel.test.servicesHeader")), StandardCharsets.UTF_8));

        assertEquals(headerOperations(header, "weivel_standard_config"), operationsOf(Config.class));
        assertEquals(headerOperations(header, "weivel_standard_secrets"), operationsOf(Secrets.class));
        assertEquals(headerOperations(header, "weivel_standard_paths"), operationsOf(Paths.class));
    }

    @Test
    void anOperationAppendedAfterTheFirstShapeIsRefusedByAProviderThatDoesNotImplementIt() {
        Config firstShape = new Config() {
            @Override
            public Map<String, SettingValue> all() {
                return Collections.emptyMap();
            }

            @Override
            public Optional<SettingValue> get(String key) {
                return Optional.empty();
            }

            @Override
            public void set(String key, SettingValue value) {
            }
        };

        assertEquals("the config provider does not offer get_tree",
                assertThrows(ServiceRefusal.class, () -> firstShape.getTree("theme")).detail());
        assertEquals("the config provider does not offer settings_file",
                assertThrows(ServiceRefusal.class, firstShape::settingsFile).detail());
    }

    @Test
    void aSettingHoldsTextAFiniteNumberOrABooleanAndNothingElse() {
        assertEquals("on", SettingValue.text("on").asText());
        assertEquals(1.5, SettingValue.number(1.5).asNumber());
        assertEquals(true, SettingValue.bool(true).asBoolean());
        assertThrows(IllegalArgumentException.class, () -> SettingValue.number(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> SettingValue.number(Double.POSITIVE_INFINITY));
        assertThrows(IllegalStateException.class, () -> SettingValue.text("on").asNumber());
    }

    @Test
    void aRefusalCarriesTheDetailAndTheFixTheHeadersRefusalCarries() {
        ServiceRefusal refusal = new ServiceRefusal("the settings file is read-only",
                "make the settings file writable");

        assertEquals(Arrays.asList("the settings file is read-only", "make the settings file writable",
                "the settings file is read-only"), Arrays.asList(refusal.detail(), refusal.fix(),
                refusal.getMessage()));
    }

    private static String withoutComments(String header) {
        return header.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    /** The operations a definition struct of services.h carries: its function pointers, its releases aside. */
    private static Set<String> headerOperations(String header, String struct) {
        Matcher body = Pattern.compile("(?s)typedef struct " + struct + " \\{(.*?)\\} " + struct + ";")
                .matcher(header);
        if (!body.find()) {
            throw new AssertionError("services.h declares no " + struct);
        }
        Set<String> operations = new TreeSet<>();
        Matcher operation = OPERATION.matcher(body.group(1));
        while (operation.find()) {
            if (!operation.group(1).startsWith("release_")) {
                operations.add(javaName(operation.group(1)));
            }
        }
        return operations;
    }

    /** A header's {@code get_tree} as Java names it, {@code getTree}. */
    private static String javaName(String headerName) {
        StringBuilder name = new StringBuilder();
        boolean upper = false;
        for (char character : headerName.toCharArray()) {
            if (character == '_') {
                upper = true;
            } else {
                name.append(upper ? Character.toUpperCase(character) : character);
                upper = false;
            }
        }
        return name.toString();
    }

    private static Set<String> operationsOf(Class<?> definition) {
        Set<String> operations = new TreeSet<>();
        for (Method method : definition.getDeclaredMethods()) {
            operations.add(method.getName());
        }
        return operations;
    }
}
