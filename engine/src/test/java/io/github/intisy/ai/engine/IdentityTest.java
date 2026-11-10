package io.github.intisy.ai.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class IdentityTest {

    @Test
    void localIdentityNamesTheImport() {
        Identity identity = Identity.local("calendar");
        assertEquals("local:calendar", identity.getId());
        assertEquals("calendar", identity.getName());
    }

    @Test
    void acceptsLocalAndForgeIds() {
        assertTrue(Identity.isValidId("local:calendar"));
        assertTrue(Identity.isValidId("github:123"));
        assertTrue(Identity.isValidId("gitea@git.example.com:12/runtime"));
    }

    @Test
    void refusesABareNameAnEmptyIdAndAnUppercaseForge() {
        assertFalse(Identity.isValidId("calendar"));
        assertFalse(Identity.isValidId("github:"));
        assertFalse(Identity.isValidId("GitHub:1"));
    }

    @Test
    void refusesAnIdEndingInANewline() {
        assertFalse(Identity.isValidId("github:1\n"));
    }

    @Test
    void unboundServicesSkipsBareIdsAndIdsBoundByAProvider() {
        Object manifest = new TestJson("{\"services\": {\"consumes\": [\"clock\", \"calendar:events\","
                + " {\"id\": \"mail:send\", \"provider\": \"github:2\"}, {\"id\": \"mail:read\"}]}}").read();
        assertEquals(Arrays.asList("calendar:events", "mail:read"), Identity.unboundServices(manifest));
    }

    @Test
    void unboundServicesIsEmptyForAManifestWithNoServices() {
        assertEquals(Collections.<String>emptyList(), Identity.unboundServices(new TestJson("{}").read()));
    }
}
