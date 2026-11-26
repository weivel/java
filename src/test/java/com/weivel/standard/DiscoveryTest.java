package com.weivel.standard;

import static com.weivel.standard.StandardFixture.refusal;
import static com.weivel.standard.StandardFixture.standard;
import static com.weivel.standard.StandardFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.CoreProbes;
import com.weivel.core.Manifest;
import com.weivel.host.Entry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryTest {
    private static final String JAR_ENTRY = "com.weivel.host.jar.GreetingEntry";

    @Test
    void aPluginLaidOutOnDiskIsFoundWithItsIdentityAndEntry(@TempDir Path directory) throws IOException {
        layOutGreeting(directory);

        Discovery found = value(standard().discover(directory, ValidationOptions.defaults()));

        assertEquals(1, found.plugins().size(), found.toString());
        DiscoveredPlugin greeting = found.plugins().get(0);
        assertEquals("greeting", greeting.manifest().importName());
        assertEquals(Optional.of("forge:42/greeting"), greeting.manifest().identity());
        assertEquals(Optional.of("greeting-repository"), greeting.repositoryName());
        assertEquals(Optional.of(new Identity(Identity.Kind.FORGE, Optional.<String>empty(), Optional.of("forge"),
                Optional.<String>empty(), BigInteger.valueOf(42), Collections.singletonList("greeting"))),
                greeting.identity());
        assertEquals(Optional.of(directory.resolve("greeting.jar").toString().replace('\\', '/')),
                greeting.entryPath().map(path -> path.toString().replace('\\', '/')));
        assertTrue(greeting.entryPresent());
        assertEquals("java", greeting.language());
    }

    @Test
    void aDiscoveredPluginIsRegisteredByItsCallerWithAnEntryLoadedByTheNameTheCallerGives(@TempDir Path directory)
            throws Exception {
        layOutGreeting(directory);
        DiscoveredPlugin greeting = value(standard().discover(directory, ValidationOptions.defaults()))
                .plugins().get(0);

        try (StandardFixture.TestHost fixture = new StandardFixture.TestHost()) {
            Entry entry = CoreProbes.ok(fixture.host.loadEntry(greeting.entryPath().get(), JAR_ENTRY));
            long plugin = CoreProbes.ok(fixture.host.register(greeting.manifest(), entry));

            Manifest registered = fixture.host.manifest(plugin).get();
            assertEquals(greeting.manifest(), registered);
            assertEquals(Optional.of("forge:42/greeting"), registered.identity());
        }
    }

    @Test
    void aDiscoveredPluginLoadsNoCodeItselfSinceNoModuleConventionIsTheLayersToAssume() {
        List<String> loading = new ArrayList<>();
        for (Method method : DiscoveredPlugin.class.getMethods()) {
            if (Arrays.stream(method.getParameterTypes()).anyMatch(type -> type.getName().startsWith("com.weivel.host"))
                    || method.getReturnType().getName().startsWith("com.weivel.host")) {
                loading.add(method.getName());
            }
        }

        assertEquals(Collections.<String>emptyList(), loading);
    }

    @Test
    void aPluginWhoseEntryIsAbsentIsFoundSayingSoAndLoadsNothing(@TempDir Path directory) throws Exception {
        write(directory, "absent.json",
                "{\"importName\": \"absent\", \"api\": 1, \"language\": \"c\", \"entry\": \"absent.jar\"}");
        DiscoveredPlugin absent = value(standard().discover(directory, ValidationOptions.defaults()))
                .plugins().get(0);

        try (StandardFixture.TestHost fixture = new StandardFixture.TestHost()) {
            assertFalse(absent.entryPresent());
            assertEquals("c", absent.language());
            assertInstanceOf(Answer.Mistake.class, fixture.host.loadEntry(absent.entryPath().get(), JAR_ENTRY));
        }
    }

    @Test
    void aFileThatIsNoPluginIsAFailureNamingItAndHidesNoOther(@TempDir Path directory) throws IOException {
        layOutGreeting(directory);
        write(directory, "broken.json", "{\"importName\": ");

        Discovery found = value(standard().discover(directory, ValidationOptions.defaults()));

        assertEquals(1, found.plugins().size());
        assertEquals(1, found.failures().size());
        assertTrue(found.failures().get(0).file().endsWith("broken.json"), found.toString());
        assertEquals(StandardRefusal.MALFORMED_JSON, found.failures().get(0).refusal().code());
    }

    @Test
    void aDirectoryThatDoesNotExistIsTheStandardsNotFound(@TempDir Path directory) {
        StandardRefusal refused = refusal(standard().discover(directory.resolve("absent"),
                ValidationOptions.defaults()));

        assertEquals(StandardRefusal.NOT_FOUND, refused.code());
    }

    private static void layOutGreeting(Path directory) throws IOException {
        write(directory, "greeting.json", "{\"importName\": \"greeting\", \"api\": 1, \"language\": \"java\","
                + " \"entry\": \"greeting.jar\","
                + " \"services\": {\"provides\": [\"greeting:hello\"]}}");
        write(directory, "greeting_identity.json",
                "{\"id\": \"forge:42/greeting\", \"name\": \"greeting-repository\"}");
        Files.copy(StandardFixture.file("weivel.test.pluginsJar"), directory.resolve("greeting.jar"));
    }

    private static void write(Path directory, String name, String text) throws IOException {
        Files.write(directory.resolve(name), text.getBytes(StandardCharsets.UTF_8));
    }
}
