package com.weivel.context;

import static com.weivel.context.ContextFixture.capabilities;
import static com.weivel.context.ContextFixture.consuming;
import static com.weivel.context.ContextFixture.providing;
import static com.weivel.context.ContextFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.Choice;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreEvent;
import com.weivel.core.FailureKind;
import com.weivel.core.Manifest;
import com.weivel.core.NamedNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** What a plugin hears of the core, its log and its manifest, and listeners that never reach a caller. */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class EventsTest {
    private static final String GREETER = "provider:greeter";

    @Test
    void aPluginHearsAServiceItHeldRevokedWithTheObjectItWasHanded() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            Object greeter = new Object();
            ServiceRegistration registration = value(provider.register(GREETER, greeter));
            value(consumer.get(GREETER));
            List<PluginEvent> heard = new ArrayList<>();
            consumer.listen(heard::add);

            value(registration.unregister());

            assertEquals(1, heard.size());
            assertSame(greeter, assertInstanceOf(PluginEvent.Revoked.class, heard.getFirst()).object());
        }
    }

    @Test
    void whatASettledWantOrAWatchHandedOverIsHeldAndRevokedWithItsObject() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext wanting = fixture.opened(consuming("wanting", GREETER));
            PluginContext watching = fixture.opened(consuming("watching", GREETER));
            CompletableFuture<Answer<Object>> settlement = wanting.want(GREETER, WantLimits.unbounded()).settlement();
            value(watching.watch(GREETER, ServicesTest.recording(new ArrayList<>())));
            Object greeter = new Object();
            ServiceRegistration registration = value(provider.register(GREETER, greeter));
            assertSame(greeter, value(settlement.join()));
            List<PluginEvent> wantingHeard = new ArrayList<>();
            List<PluginEvent> watchingHeard = new ArrayList<>();
            wanting.listen(wantingHeard::add);
            watching.listen(watchingHeard::add);

            value(registration.unregister());

            assertSame(greeter, assertInstanceOf(PluginEvent.Revoked.class, wantingHeard.getFirst()).object());
            assertSame(greeter, assertInstanceOf(PluginEvent.Revoked.class, watchingHeard.getFirst()).object());
            assertEquals(0, fixture.handles.holdingCount());
        }
    }

    @Test
    void aPluginHearsARefusalOfReach() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            List<PluginEvent> heard = new ArrayList<>();
            consumer.listen(heard::add);

            consumer.get("undeclared");

            PluginEvent.ReachRefused refused = assertInstanceOf(PluginEvent.ReachRefused.class, heard.getFirst());
            assertEquals(CoreConstants.REACH_SERVICE, refused.kind());
            assertEquals("undeclared", refused.id());
            assertEquals(CoreConstants.REFUSAL_UNDECLARED, refused.refusal().code());
        }
    }

    @Test
    void aPluginHearsAConstrainedWantThatSettledDegraded() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY)) {
            PluginContext provider = fixture.opened(new Manifest("provider", 0, Optional.empty(), List.of(),
                    List.of(), List.of(), List.of(new Manifest.ServiceOffer(GREETER,
                    List.of(new NamedNumber("latency", 50)))), List.of(), List.of(), Optional.empty()));
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", GREETER, 10, Choice.YES));
            Object greeter = new Object();
            value(provider.register(GREETER, greeter));
            List<PluginEvent> heard = new ArrayList<>();
            consumer.listen(heard::add);

            CompletableFuture<Answer<Object>> settlement = consumer.want(GREETER, WantLimits.budgeted(100, 100))
                    .settlement();
            fixture.scheduler.moveTo(1000);

            assertSame(greeter, value(settlement.join()));
            PluginEvent.Degraded degraded = assertInstanceOf(PluginEvent.Degraded.class, heard.getFirst());
            assertEquals(GREETER, degraded.id());
            assertSame(greeter, degraded.service());
            assertEquals(Optional.of("latency"), degraded.unmetConstraint());
        }
    }

    @Test
    void aThrowingListenerDoesNotSilenceTheOthers() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext calendar = fixture.opened(capabilities("calendar", List.of("screens"), List.of()));
            PluginContext bad = fixture.opened(capabilities("bad", List.of(), List.of("screens")));
            PluginContext agenda = fixture.opened(capabilities("agenda", List.of(), List.of("screens")));
            List<Object> badHeard = new ArrayList<>();
            List<Object> agendaHeard = new ArrayList<>();
            value(bad.watchCapability("screens", new CapabilityWatcher() {
                @Override
                public void provided(Object implementation) {
                    badHeard.add(implementation);
                    throw new IllegalStateException("a listener failing on purpose");
                }

                @Override
                public void withdrawn(Object implementation) {
                    badHeard.add(implementation);
                    throw new IllegalStateException("a listener failing on purpose");
                }
            }));
            value(agenda.watchCapability("screens", CapabilitiesTest.recording(agendaHeard)));
            Object screen = new Object();
            value(calendar.provide("screens", screen));

            fixture.verify(calendar);

            assertEquals(List.of("provided", screen), agendaHeard);
            assertEquals(List.of(screen), badHeard);
            assertEquals(List.of(), fixture.heard.stream().filter(CoreEvent.RefusalEvent.class::isInstance).toList());
            assertEquals(1, fixture.failuresOf(FailureKind.PLUGIN_CALLBACK).size());
            assertEquals(1, fixture.failures.size());
        }
    }

    @Test
    void aPluginEventListenerThatThrowsGoesToTheSinkAndTheRestStillHearInOrder() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            List<String> heard = new ArrayList<>();
            consumer.listen(event -> heard.add("first"));
            consumer.listen(event -> {
                throw new IllegalStateException("a listener failing on purpose");
            });
            consumer.listen(event -> heard.add("third"));

            consumer.get("undeclared");

            assertEquals(List.of("first", "third"), heard);
            assertEquals(1, fixture.failuresOf(FailureKind.PLUGIN_CALLBACK).size());
        }
    }

    @Test
    void aListenerThatStoppedHearsNothingMore() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            List<PluginEvent> heard = new ArrayList<>();
            consumer.listen(heard::add).stop();

            consumer.get("undeclared");

            assertEquals(List.of(), heard);
        }
    }

    @Test
    void aPluginsLogGoesToTheHostsLogWithThePluginNamed() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));

            consumer.log().write(CoreConstants.LOG_INFO, "hello");

            assertEquals(List.of(new LogRecord(CoreConstants.LOG_INFO, Optional.of("consumer"), "hello")),
                    fixture.logs);
        }
    }

    @Test
    void theCoresOwnLogGoesToTheHostsLogAtItsLevel() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            fixture.release(provider);

            provider.register(GREETER, new Object());

            CoreEvent.LogEvent logged = (CoreEvent.LogEvent) fixture.heard.stream()
                    .filter(CoreEvent.LogEvent.class::isInstance).reduce((first, second) -> second).orElseThrow();
            LogRecord written = fixture.logs.getLast();
            assertEquals(new LogRecord(logged.level(), Optional.empty(), logged.text()), written);
        }
    }

    @Test
    void aHostLogThatThrowsGoesToTheSink() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContexts throwing = PluginContexts.create(fixture.core, new com.weivel.handles.Handles(),
                    fixture.timer, record -> {
                        throw new IllegalStateException("a log failing on purpose");
                    });
            long plugin = com.weivel.core.CoreProbes.ok(fixture.core.call(
                    com.weivel.core.CoreCalls.registerManifest(Manifest.named("logging"))));
            PluginContext logging = throwing.make(plugin, Manifest.named("logging"));

            logging.log().write(CoreConstants.LOG_WARNING, "unheard");

            assertEquals(1, fixture.failuresOf(FailureKind.LOG).size());
            fixture.core.destroy();
            throwing.close();
        }
    }

    @Test
    void theManifestIsTheValueTheHostRegistered() {
        try (ContextFixture fixture = new ContextFixture()) {
            Manifest manifest = consuming("consumer", GREETER);

            assertSame(manifest, fixture.opened(manifest).manifest());
            assertTrue(fixture.failures.isEmpty());
        }
    }
}
