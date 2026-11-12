package io.github.intisy.ai.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class PluginHostBindingTest {

    private static final String EVENTS = "calendar:events";

    private final List<String> diagnostics = new ArrayList<String>();

    @BeforeEach
    void captureDiagnostics() {
        Diagnostics.setSink(new Diagnostics.Sink() {
            @Override
            public void accept(String message) {
                diagnostics.add(message);
            }
        });
    }

    @AfterEach
    void restoreDiagnostics() {
        Diagnostics.setSink(null);
    }

    private static PluginHost host() {
        return new PluginHost("claude", 1, Arrays.asList("tui"), new ManualScheduler());
    }

    private static PluginSession open(PluginHost host, String id, String importName, List<?> consumes, List<String> provides) {
        ManifestFacts facts = new ManifestFacts(id, 1, null, null, null)
                .withImportName(importName)
                .withServices(consumes, provides);
        return host.sessionFor(facts, new RecordingBus());
    }

    private static PluginSession providerOfEvents(PluginHost host, String id, String importName) {
        PluginSession session = open(host, id, importName, Collections.emptyList(), Arrays.asList(EVENTS));
        session.getServices().register(EVENTS, new Object());
        return session;
    }

    private static PluginSession consumerBoundTo(PluginHost host, String provider) {
        Map<String, Object> requirement = new LinkedHashMap<String, Object>();
        requirement.put("id", EVENTS);
        if (provider != null) {
            requirement.put("provider", provider);
        }
        return open(host, "agenda", "agenda", Arrays.<Object>asList(requirement), Collections.<String>emptyList());
    }

    private static List<List<String>> refusals(PluginHost host) {
        List<List<String>> pairs = new ArrayList<List<String>>();
        for (DeclarationRefusal refusal : host.getLedger().refusals()) {
            pairs.add(Arrays.asList(refusal.getPluginId(), refusal.getId()));
        }
        return pairs;
    }

    private static ServiceHub.Listener hearing(final List<String> heard) {
        return new ServiceHub.Listener() {
            @Override
            public void changed(Object service, boolean registered) {
                heard.add(registered ? "register" : "unregister");
            }
        };
    }

    @Test
    void servesTheProviderItNames() {
        PluginHost host = host();
        providerOfEvents(host, "github:2", "calendar");
        assertNotNull(consumerBoundTo(host, "github:2").getServices().get(EVENTS));
    }

    @Test
    void refusesAServiceAnotherProviderOwnsNamingBothIds() {
        PluginHost host = host();
        providerOfEvents(host, "github:2", "calendar");
        assertNull(consumerBoundTo(host, "github:1").getServices().get(EVENTS));
        assertEquals(Arrays.asList(Arrays.asList("agenda", EVENTS)), refusals(host));
        assertTrue(diagnostics.get(0).contains("github:1"), diagnostics.get(0));
        assertTrue(diagnostics.get(0).contains("github:2"), diagnostics.get(0));
    }

    @Test
    void readsAsAbsentNotRefusedWhenNobodyProvidesIt() {
        PluginHost host = host();
        assertNull(consumerBoundTo(host, "github:1").getServices().get(EVENTS));
        assertTrue(refusals(host).isEmpty());
    }

    @Test
    void servesFromAnyoneWhenTheConsumerNamesNoProvider() {
        PluginHost host = host();
        providerOfEvents(host, "github:2", "calendar");
        assertNotNull(consumerBoundTo(host, null).getServices().get(EVENTS));
        assertTrue(refusals(host).isEmpty());
    }

    @Test
    void doesNotDeliverAWrongProvidersServiceToAWantAndDeliversTheNamedOne() {
        PluginHost host = host();
        Settled<Object> asked = Settled.of(consumerBoundTo(host, "github:1").getServices().want(EVENTS));
        providerOfEvents(host, "github:2", "calendar");
        assertEquals(0, asked.settlements());
        assertEquals(Arrays.asList(Arrays.asList("agenda", EVENTS)), refusals(host));
        host.release("github:2");
        providerOfEvents(host, "github:1", "calendar");
        assertNotNull(asked.value());
    }

    @Test
    void doesNotDeliverAWrongProvidersServiceToAWatchAndDeliversTheNamedOne() {
        PluginHost host = host();
        List<String> heard = new ArrayList<String>();
        consumerBoundTo(host, "github:1").getServices().watch(EVENTS, hearing(heard));
        providerOfEvents(host, "github:2", "calendar");
        assertTrue(heard.isEmpty());
        assertTrue(refusals(host).isEmpty(), "a bound watcher skips a wrong provider silently");
        host.release("github:2");
        assertTrue(heard.isEmpty());
        providerOfEvents(host, "github:1", "calendar");
        assertEquals(Arrays.asList("register"), heard);
    }

    @Test
    void doesNotDeliverAFakeThatReplacesTheRealProvider() {
        PluginHost host = host();
        providerOfEvents(host, "github:1", "calendar");
        List<String> heard = new ArrayList<String>();
        consumerBoundTo(host, "github:1").getServices().watch(EVENTS, hearing(heard));
        host.release("github:1");
        providerOfEvents(host, "github:2", "calendar");
        assertEquals(Arrays.asList("unregister"), heard);
    }

    @Test
    void registersUnderTheImportNameAndNotTheIdentity() {
        PluginHost host = host();
        providerOfEvents(host, "github:2", "calendar");
        assertNotNull(host.service(EVENTS));
    }

    @Test
    void refusesARegistrationUnderAnotherImportNameAndSaysWhichToUse() {
        PluginHost host = host();
        final PluginSession session = open(host, "github:2", "calendar", Collections.emptyList(),
                Arrays.asList("github:2:events"));
        PluginException refusal = assertThrows(PluginException.class, new Executable() {
            @Override
            public void execute() {
                session.getServices().register("github:2:events", new Object());
            }
        });
        assertTrue(refusal.getFix().contains("calendar:"), refusal.getFix());
    }
}
