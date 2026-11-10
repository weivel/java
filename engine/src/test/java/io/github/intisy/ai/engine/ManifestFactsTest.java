package io.github.intisy.ai.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestFactsTest {

    @Test
    void carriesTheTargetingItWasGiven() {
        ManifestFacts facts = new ManifestFacts("wakatime-sync", 1, null, null,
                Arrays.asList("forebay"), "payload");
        assertEquals(Arrays.asList("forebay"), facts.getTargets());
    }

    @Test
    void theShortConstructorTargetsNothingAndSoLoadsEverywhere() {
        ManifestFacts facts = new ManifestFacts("wakatime-sync", 1, null, null, "payload");
        assertTrue(facts.getTargets().isEmpty());
    }

    @Test
    void copiesTheTargetsSoLaterMutationOfTheArgumentDoesNothing() {
        List<String> given = new ArrayList<String>();
        given.add("forebay");
        ManifestFacts facts = new ManifestFacts("wakatime-sync", 1, null, null, given, "payload");
        given.add("studio");
        assertEquals(Arrays.asList("forebay"), facts.getTargets());
    }

    @Test
    void takesItsImportNameFromTheIdUnlessToldOtherwise() {
        ManifestFacts facts = new ManifestFacts("github:2", 1, null, null, "payload");
        assertEquals("github:2", facts.getImportName());
        assertEquals("calendar", facts.withImportName("calendar").getImportName());
    }

    @Test
    void readsTheProviderEachBoundRequirementNames() {
        ManifestFacts facts = new ManifestFacts("agenda", 1, null, null, "payload").withServices(
                Arrays.<Object>asList("clock", java.util.Collections.singletonMap("id", "mail:read"),
                        bound("mail:send", "github:2")),
                null);
        assertEquals(java.util.Collections.singletonMap("mail:send", "github:2"), facts.getBoundProviders());
    }

    private static java.util.Map<String, Object> bound(String id, String provider) {
        java.util.Map<String, Object> requirement = new java.util.LinkedHashMap<String, Object>();
        requirement.put("id", id);
        requirement.put("provider", provider);
        return requirement;
    }
}
