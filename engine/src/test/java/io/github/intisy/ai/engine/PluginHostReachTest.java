package io.github.intisy.ai.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginHostReachTest {

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

    private static PluginSession session(PluginHost host, String pluginId, List<String> consumes, List<String> provides) {
        ManifestFacts facts = new ManifestFacts(pluginId, 1, null, null, null).withServices(consumes, provides);
        return host.sessionFor(facts, new RecordingBus());
    }

    private static List<String> none() {
        return Collections.<String>emptyList();
    }

    private static List<List<String>> refusals(PluginHost host) {
        List<List<String>> pairs = new ArrayList<List<String>>();
        for (ServiceRefusal refusal : host.getLedger().refusals()) {
            pairs.add(Arrays.asList(refusal.getPluginId(), refusal.getServiceId()));
        }
        return pairs;
    }

    private static ServiceHub.Listener recordingInto(final List<Object> heard) {
        return new ServiceHub.Listener() {
            @Override
            public void changed(Object service, boolean registered) {
                if (registered) {
                    heard.add(service);
                }
            }
        };
    }

    @Test
    void answersNothingToAnUndeclaredGetAndRecordsTheRefusalOnce() {
        PluginHost host = host();
        session(host, "calendar", none(), Arrays.asList("calendar:events")).getServices().register("calendar:events", new Object());
        PluginSession notes = session(host, "notes", none(), none());

        assertNull(notes.getServices().get("calendar:events"));
        assertNull(notes.getServices().get("calendar:events"));

        assertEquals(Arrays.asList(Arrays.asList("notes", "calendar:events")), refusals(host));
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("services.consumes"), diagnostics.get(0));
        assertTrue(diagnostics.get(0).contains("calendar:events"), diagnostics.get(0));
        assertTrue(diagnostics.get(0).contains("notes"), diagnostics.get(0));
        assertEquals(new ArrayList<String>(), host.getLedger().entry("notes").getServicesConsumed());
    }

    @Test
    void refusesAHostServiceThePluginDidNotDeclare() {
        PluginHost host = host();
        Object clock = new Object();
        host.provideService("clock", clock);

        assertNull(session(host, "notes", none(), none()).getServices().get("clock"));
        assertSame(clock, session(host, "agenda", Arrays.asList("clock"), none()).getServices().get("clock"));
        assertEquals(Arrays.asList(Arrays.asList("notes", "clock")), refusals(host));
    }

    @Test
    void servesWhatAPluginConsumesOrProvidesAndReadsADeclaredIdNothingProvidesAsAbsence() {
        PluginHost host = host();
        PluginSession calendar = session(host, "calendar", none(), Arrays.asList("calendar:events"));
        PluginSession agenda = session(host, "agenda", Arrays.asList("calendar:events", "weather:forecast"), none());
        Settled<Object> awaited = Settled.of(agenda.getServices().want("calendar:events"));
        List<Object> heard = new ArrayList<Object>();
        calendar.getServices().watch("calendar:events", recordingInto(heard));
        Object events = new Object();
        calendar.getServices().register("calendar:events", events);

        assertSame(events, calendar.getServices().get("calendar:events"));
        assertSame(events, agenda.getServices().get("calendar:events"));
        assertSame(events, awaited.value());
        assertEquals(Arrays.asList(events), heard);
        assertNull(agenda.getServices().get("weather:forecast"));
        assertEquals(0, Settled.of(agenda.getServices().want("weather:forecast")).settlements());
        assertEquals(new ArrayList<List<String>>(), refusals(host));
        assertEquals(new ArrayList<String>(), diagnostics);
    }

    @Test
    void listsOnlyTheRegisteredIdsAPluginMayReachWithoutReportingTheRest() {
        PluginHost host = host();
        host.provideService("clock", new Object());
        host.provideService("locale", new Object());
        PluginSession calendar = session(host, "calendar", Arrays.asList("clock", "weather:forecast"), Arrays.asList("calendar:events"));
        PluginSession idle = session(host, "idle", none(), none());
        calendar.getServices().register("calendar:events", new Object());

        List<String> listed = new ArrayList<String>(calendar.getServices().ids());
        Collections.sort(listed);
        assertEquals(Arrays.asList("calendar:events", "clock"), listed);
        assertEquals(new ArrayList<String>(), idle.getServices().ids());
        assertEquals(new ArrayList<List<String>>(), refusals(host));
        assertEquals(new ArrayList<String>(), diagnostics);
    }

    @Test
    void keepsAnUndeclaredWatchInert() {
        PluginHost host = host();
        PluginSession calendar = session(host, "calendar", none(), Arrays.asList("calendar:events"));
        List<Object> heard = new ArrayList<Object>();
        session(host, "notes", none(), none()).getServices().watch("calendar:events", recordingInto(heard));
        calendar.getServices().register("calendar:events", new Object());

        assertEquals(new ArrayList<Object>(), heard);
        assertEquals(Arrays.asList(Arrays.asList("notes", "calendar:events")), refusals(host));
    }

    @Test
    void rejectsAnUndeclaredWantWithTheFixRatherThanTheLateWordingInEveryForm() {
        PluginHost host = host();
        session(host, "calendar", none(), Arrays.asList("calendar:events")).getServices().register("calendar:events", new Object());
        ServiceHub.Registry notes = session(host, "notes", none(), none()).getServices();

        List<Settled<Object>> refused = Arrays.asList(
                Settled.of(notes.want("calendar:events")),
                Settled.of(notes.want("calendar:events", 1000L)),
                Settled.of(notes.want(ServiceRequirement.of("calendar:events"), Budget.of(1000L, 100L))));

        for (Settled<Object> each : refused) {
            assertNull(each.value());
            assertNotNull(each.failure());
            assertEquals("notes", each.failure().getPluginId());
            assertEquals("reached for service \"calendar:events\", which it does not declare", each.failure().getDetail());
            assertTrue(each.failure().getFix().contains("services.consumes"), each.failure().getFix());
        }
        assertEquals(Arrays.asList(Arrays.asList("notes", "calendar:events")), refusals(host));
    }

    @Test
    void refusesARevokedPluginsUndeclaredCallAsUndeclaredAndOnlyItsDeclaredWantAsLate() {
        PluginHost host = host();
        PluginSession agenda = session(host, "agenda", Arrays.asList("clock"), none());
        host.release("agenda");

        Settled<Object> undeclared = Settled.of(agenda.getServices().want("calendar:events"));
        agenda.getServices().watch("locale", recordingInto(new ArrayList<Object>()));
        Settled<Object> declared = Settled.of(agenda.getServices().want("clock"));

        assertEquals(Arrays.asList(Arrays.asList("agenda", "calendar:events"), Arrays.asList("agenda", "locale")), refusals(host));
        assertTrue(undeclared.failure().getFix().contains("services.consumes"), undeclared.failure().getFix());
        assertTrue(diagnostics.get(1).contains("services.consumes"), diagnostics.get(1));
        assertEquals("stopped while waiting for service \"clock\"", declared.failure().getDetail());
    }
}
