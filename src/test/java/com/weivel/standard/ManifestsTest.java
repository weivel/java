package com.weivel.standard;

import static com.weivel.standard.StandardFixture.refusal;
import static com.weivel.standard.StandardFixture.standard;
import static com.weivel.standard.StandardFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.weivel.core.Choice;
import com.weivel.core.CoreProbes;
import com.weivel.core.Manifest;
import com.weivel.core.NamedNumber;
import com.weivel.host.Entry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ManifestsTest {
    static final String FULL_MANIFEST = "{\"importName\": \"greeter\", \"api\": 1, \"entry\": \"dist/greeter.jar\","
            + " \"targets\": [\"app.one\", \"app.two\"],"
            + " \"capabilities\": {\"provides\": [\"screens\", \"menus\"], \"consumes\": [\"provider\"]},"
            + " \"services\": {"
            + "   \"provides\": [\"greeter:hello\", {\"id\": \"greeter:fast\","
            + "                  \"attributes\": {\"latency\": 5, \"throughput\": 1.5}}],"
            + "   \"consumes\": [\"clock\", {\"id\": \"store\", \"optional\": false, \"provider\": \"local:store\","
            + "                  \"degradable\": true, \"constraints\": {\"latency\": 10}}]},"
            + " \"permissions\": [\"network\", \"files\"],"
            + " \"dependencies\": [{\"importName\": \"store\", \"id\": \"local:store\"}]}";

    static final Manifest FULL = new Manifest("greeter", 1, Optional.of("dist/greeter.jar"),
            Arrays.asList("app.one", "app.two"), Arrays.asList("screens", "menus"),
            Collections.singletonList("provider"),
            Arrays.asList(new Manifest.ServiceOffer("greeter:hello", Collections.<NamedNumber>emptyList()),
                    new Manifest.ServiceOffer("greeter:fast",
                            Arrays.asList(new NamedNumber("latency", 5), new NamedNumber("throughput", 1.5)))),
            Arrays.asList(new Manifest.ServiceWant("clock", Optional.<String>empty(), Choice.DEFAULT, Choice.DEFAULT,
                            Collections.<NamedNumber>emptyList()),
                    new Manifest.ServiceWant("store", Optional.of("local:store"), Choice.NO, Choice.YES,
                            Collections.singletonList(new NamedNumber("latency", 10)))),
            Arrays.asList("network", "files"), Optional.<String>empty());

    @Test
    void aJsonManifestIsReadIntoTheCoresManifestValue() {
        Manifest read = value(standard().loadManifest(FULL_MANIFEST, ValidationOptions.defaults()));

        assertEquals(FULL, read);
    }

    @Test
    void aManifestReadIsRegisteredWithTheHostAsItWasRead() throws Exception {
        Manifest read = value(standard().loadManifest(FULL_MANIFEST, ValidationOptions.defaults()));

        try (StandardFixture.TestHost fixture = new StandardFixture.TestHost()) {
            Entry entry = context -> CompletableFuture.completedFuture(null);
            long plugin = CoreProbes.ok(fixture.host.register(read, entry));

            assertEquals(Optional.of(FULL), fixture.host.manifest(plugin));
        }
    }

    @Test
    void anInvalidManifestIsRefusedNamingEveryWrongField() {
        StandardAnswer<Manifest> loaded = standard().loadManifest(
                "{\"importName\": \"Bad Name\", \"api\": \"two\"}", ValidationOptions.defaults());

        assertEquals("importName", refusal(loaded).path());
        assertEquals(Arrays.asList("importName", "api"), loaded.paths());
        assertEquals(Optional.<Manifest>empty(), loaded.value());
    }

    @Test
    void theHostsWellKnownServicesAreHandedToTheValidation() {
        String providingABareId = "{\"importName\": \"clock\", \"api\": 1, \"services\": {\"provides\": [\"time\"]}}";

        assertEquals(StandardStatus.OK,
                standard().loadManifest(providingABareId, ValidationOptions.wellKnown(Arrays.asList("time")))
                        .status());
        assertEquals(StandardRefusal.NOT_WELL_KNOWN, refusal(standard().loadManifest(providingABareId,
                ValidationOptions.wellKnown(Collections.<String>emptyList()))).code());
    }

    @Test
    void malformedJsonIsRefusedAtTheWholeDocument() {
        StandardRefusal refused = refusal(standard().loadManifest("{\"importName\": ", ValidationOptions.defaults()));

        assertEquals(StandardRefusal.MALFORMED_JSON, refused.code());
        assertEquals("", refused.path());
    }

    @Test
    void aManifestIsReadWithoutValidationAsTheStandardConvertsIt() {
        Manifest read = value(standard().readManifest("{\"importName\": \"Bad Name\", \"ui\": {\"screens\": [1]}}"));

        assertEquals(Manifest.named("Bad Name"), read);
        assertEquals(FULL, value(standard().readManifest(FULL_MANIFEST)));
    }

    @Test
    void aManifestReadWithoutValidationIsRefusedForAWrongType() {
        StandardRefusal refused = refusal(standard().readManifest("{\"importName\": \"a\", \"api\": \"two\"}"));

        assertEquals(StandardRefusal.WRONG_TYPE, refused.code());
        assertEquals("api", refused.path());
    }

    @Test
    void aDocumentReadWithNoImportNameCannotBecomeAManifestValue() {
        assertThrows(IllegalArgumentException.class, () -> standard().readManifest("{\"api\": 1}"));
    }

    @Test
    void aManifestIsValidatedWithoutBeingRead() {
        assertEquals(StandardStatus.OK, standard().validate(FULL_MANIFEST, ValidationOptions.defaults()).status());
        StandardAnswer<Void> invalid = standard().validate("{\"importName\": \"Bad Name\", \"api\": \"two\"}",
                ValidationOptions.defaults());
        assertEquals("importName", refusal(invalid).path());
        assertEquals(Arrays.asList("importName", "api"), invalid.paths());
    }
}
