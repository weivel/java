package io.github.intisy.ai.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginHostConsumeTest {

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

    private static PluginSession session(PluginHost host, String pluginId, List<String> provides, List<String> consumes) {
        ManifestFacts facts = new ManifestFacts(pluginId, 1, provides, null, null).withCapabilitiesConsumed(consumes);
        return host.sessionFor(facts, new RecordingBus());
    }

    private static List<String> none() {
        return Collections.<String>emptyList();
    }

    private static List<String> screens() {
        return Arrays.asList("screens");
    }

    private static void verify(PluginHost host, PluginSession session) {
        PluginException refusal = host.verifyActivation(session.getFacts());
        if (refusal != null) {
            host.markBroken(session.getFacts().getId(), refusal);
        }
    }

    private static List<List<String>> capabilityRefusals(PluginHost host) {
        List<List<String>> pairs = new ArrayList<List<String>>();
        for (DeclarationRefusal refusal : host.getLedger().refusals()) {
            if (refusal.getKind() == DeclarationKind.CAPABILITY) {
                pairs.add(Arrays.asList(refusal.getPluginId(), refusal.getId()));
            }
        }
        return pairs;
    }

    /** Records each change as the implementation's own string and the event word. */
    private static ConsumedCapabilities.Listener recordingInto(final List<String> heard) {
        return new ConsumedCapabilities.Listener() {
            @Override
            public void changed(Object implementation, boolean provided) {
                heard.add(implementation + (provided ? " provide" : " withdraw"));
            }
        };
    }

    @Test
    void answersAnImplementationOnlyOnceItsActivationIsVerified() {
        PluginHost host = host();
        PluginSession calendar = session(host, "calendar", screens(), none());
        PluginSession agenda = session(host, "agenda", none(), screens());

        calendar.provide("screens", "calendar-screens");
        assertEquals(Collections.emptyList(), agenda.getCapabilities().all("screens"));

        verify(host, calendar);
        assertEquals(Arrays.<Object>asList("calendar-screens"), agenda.getCapabilities().all("screens"));
    }

    @Test
    void neverAnswersOrAnnouncesAProviderWhoseActivationWasRefused() {
        PluginHost host = host();
        PluginSession agenda = session(host, "agenda", none(), screens());
        List<String> heard = new ArrayList<String>();
        agenda.getCapabilities().watch("screens", recordingInto(heard));
        PluginSession flaky = session(host, "flaky", Arrays.asList("screens", "extras"), none());

        flaky.provide("screens", "flaky-screens");
        verify(host, flaky);

        assertEquals(Collections.emptyList(), agenda.getCapabilities().all("screens"));
        assertEquals(Collections.<String>emptyList(), heard);
    }

    @Test
    void withdrawsAQuarantinedProviderExactlyOnceWithTheObjectItProvided() {
        PluginHost host = host();
        PluginSession agenda = session(host, "agenda", none(), screens());
        final List<Object> withdrawn = new ArrayList<Object>();
        agenda.getCapabilities().watch("screens", new ConsumedCapabilities.Listener() {
            @Override
            public void changed(Object implementation, boolean provided) {
                if (!provided) {
                    withdrawn.add(implementation);
                }
            }
        });
        PluginSession calendar = session(host, "calendar", screens(), none());
        Object implementation = new Object();
        calendar.provide("screens", implementation);
        verify(host, calendar);

        host.markBroken("calendar", new PluginException("calendar", "failed", "fix it"));
        host.release("calendar");

        assertEquals(1, withdrawn.size());
        assertSame(implementation, withdrawn.get(0));
        assertEquals(Collections.emptyList(), agenda.getCapabilities().all("screens"));
    }

    @Test
    void answersInTheOrderActivationsWereVerifiedNotTheOrderTheyProvided() {
        PluginHost host = host();
        PluginSession alpha = session(host, "alpha", screens(), none());
        PluginSession zulu = session(host, "zulu", screens(), none());
        PluginSession reader = session(host, "reader", none(), screens());

        zulu.provide("screens", "zulu");
        alpha.provide("screens", "alpha");
        verify(host, alpha);
        verify(host, zulu);

        assertEquals(Arrays.<Object>asList("alpha", "zulu"), reader.getCapabilities().all("screens"));
    }

    @Test
    void watchHearsOnlyWhatChangesAfterItIsInstalled() {
        PluginHost host = host();
        PluginSession calendar = session(host, "calendar", screens(), none());
        calendar.provide("screens", "calendar");
        verify(host, calendar);
        PluginSession agenda = session(host, "agenda", none(), screens());
        List<String> heard = new ArrayList<String>();
        agenda.getCapabilities().watch("screens", recordingInto(heard));

        PluginSession notes = session(host, "notes", screens(), none());
        notes.provide("screens", "notes");
        verify(host, notes);
        host.release("notes");

        assertEquals(Arrays.asList("notes provide", "notes withdraw"), heard);
    }

    @Test
    void aThrowingWatcherIsReportedAndTheOthersStillHear() {
        PluginHost host = host();
        PluginSession bad = session(host, "bad", none(), screens());
        bad.getCapabilities().watch("screens", new ConsumedCapabilities.Listener() {
            @Override
            public void changed(Object implementation, boolean provided) {
                throw new IllegalStateException("boom");
            }
        });
        PluginSession agenda = session(host, "agenda", none(), screens());
        List<String> heard = new ArrayList<String>();
        agenda.getCapabilities().watch("screens", recordingInto(heard));
        PluginSession calendar = session(host, "calendar", screens(), none());

        calendar.provide("screens", "calendar");
        verify(host, calendar);

        assertEquals(Arrays.asList("calendar provide"), heard);
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("threw"), diagnostics.get(0));
        assertEquals(Collections.emptyList(), capabilityRefusals(host));
    }

    @Test
    void refusesAnUndeclaredAllWithAnEmptyAnswerAndReportsItOnce() {
        PluginHost host = host();
        PluginSession calendar = session(host, "calendar", screens(), none());
        calendar.provide("screens", "calendar");
        verify(host, calendar);
        PluginSession others = session(host, "others", none(), none());

        assertEquals(Collections.emptyList(), others.getCapabilities().all("screens"));
        assertEquals(Collections.emptyList(), others.getCapabilities().all("screens"));

        assertEquals(Arrays.asList(Arrays.asList("others", "screens")), capabilityRefusals(host));
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("capabilities.consumes"), diagnostics.get(0));
        assertTrue(diagnostics.get(0).contains("screens"), diagnostics.get(0));
    }

    @Test
    void refusesAnUndeclaredWatchAsInertAndReportsIt() {
        PluginHost host = host();
        PluginSession others = session(host, "others", none(), none());
        List<String> heard = new ArrayList<String>();
        others.getCapabilities().watch("screens", recordingInto(heard));
        PluginSession calendar = session(host, "calendar", screens(), none());

        calendar.provide("screens", "calendar");
        verify(host, calendar);

        assertEquals(Collections.<String>emptyList(), heard);
        assertEquals(Arrays.asList(Arrays.asList("others", "screens")), capabilityRefusals(host));
    }

    @Test
    void keepsACapabilityRefusalApartFromARefusedServiceOfTheSameId() {
        PluginHost host = host();
        PluginSession others = session(host, "others", none(), none());

        assertEquals(null, others.getServices().get("screens"));
        others.getCapabilities().all("screens");

        assertEquals(2, host.getLedger().refusals().size());
        assertEquals(Arrays.asList(Arrays.asList("others", "screens")), capabilityRefusals(host));
    }

    @Test
    void letsAPluginReachACapabilityItProvidesItself() {
        PluginHost host = host();
        PluginSession calendar = session(host, "calendar", screens(), none());
        List<String> heard = new ArrayList<String>();
        calendar.getCapabilities().watch("screens", recordingInto(heard));

        calendar.provide("screens", "calendar");
        verify(host, calendar);

        assertEquals(Arrays.<Object>asList("calendar"), calendar.getCapabilities().all("screens"));
        assertEquals(Arrays.asList("calendar provide"), heard);
        assertEquals(Collections.emptyList(), capabilityRefusals(host));
    }

    @Test
    void letsAReleasedPluginReadAllButRefusesItsWatchWithoutReportingIt() {
        PluginHost host = host();
        PluginSession calendar = session(host, "calendar", screens(), none());
        calendar.provide("screens", "calendar");
        verify(host, calendar);
        PluginSession agenda = session(host, "agenda", none(), screens());
        host.release("agenda");

        assertEquals(Arrays.<Object>asList("calendar"), agenda.getCapabilities().all("screens"));
        List<String> heard = new ArrayList<String>();
        agenda.getCapabilities().watch("screens", recordingInto(heard));
        PluginSession notes = session(host, "notes", screens(), none());
        notes.provide("screens", "notes");
        verify(host, notes);

        assertEquals(Collections.<String>emptyList(), heard);
        assertEquals(Collections.emptyList(), capabilityRefusals(host));
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("no longer running"), diagnostics.get(0));
    }

    @Test
    void refusesAnUndeclaredCallFromAReleasedPluginAsUndeclared() {
        PluginHost host = host();
        PluginSession others = session(host, "others", none(), none());
        host.release("others");

        others.getCapabilities().watch("screens", recordingInto(new ArrayList<String>()));

        assertEquals(Arrays.asList(Arrays.asList("others", "screens")), capabilityRefusals(host));
        assertNotNull(diagnostics.isEmpty() ? null : diagnostics.get(0));
        assertTrue(diagnostics.get(0).contains("capabilities.consumes"), diagnostics.get(0));
    }
}
