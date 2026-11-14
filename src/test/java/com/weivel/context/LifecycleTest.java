package com.weivel.context;

import static com.weivel.context.ContextFixture.consuming;
import static com.weivel.context.ContextFixture.providing;
import static com.weivel.context.ContextFixture.value;
import static com.weivel.core.CoreProbes.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreConstants;
import com.weivel.core.Manifest;
import com.weivel.core.WantOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.weivel.core.CoreEvent;
import com.weivel.core.CoreOptions;
import com.weivel.timer.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** A context's life: made no later than the first open, ending its entries, kept across a quarantine, detached. */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class LifecycleTest {
    private static final String GREETER = "provider:greeter";
    private static final long GATE_SECONDS = 20;

    @Test
    void afterAReleaseItsListenersHearWhatTheReleaseQueuedAndNothingQueuedAfter() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            Object greeter = new Object();
            ServiceRegistration registration = value(provider.register(GREETER, greeter));
            value(consumer.get(GREETER));
            List<PluginEvent> heard = new ArrayList<>();
            consumer.listen(heard::add);

            fixture.release(consumer);
            value(consumer.get(GREETER));
            value(registration.unregister());

            assertEquals(1, heard.size());
            assertSame(greeter, assertInstanceOf(PluginEvent.Revoked.class, heard.get(0)).object());
        }
    }

    @Test
    void aReleaseMadeInsideADispatchStillDeliversItsOwnEventsBeforeTheEntriesEnd() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            value(provider.register(GREETER, new Object()));
            value(consumer.get(GREETER));
            List<PluginEvent> heard = new ArrayList<>();
            consumer.listen(heard::add);
            AtomicBoolean first = new AtomicBoolean(true);
            fixture.core.addListener(event -> {
                if (first.getAndSet(false)) {
                    fixture.release(consumer);
                }
            });

            fixture.core.call(CoreCalls.unregister(424242L));

            assertEquals(1, heard.size());
            assertInstanceOf(PluginEvent.Revoked.class, heard.get(0));
        }
    }

    @Test
    void anEventDrainedBeforeAContextWasMadeNeverReachesIt() {
        try (ContextFixture fixture = new ContextFixture()) {
            Manifest manifest = consuming("late", GREETER);
            long plugin = ok(fixture.core.call(CoreCalls.registerManifest(manifest)));
            ok(fixture.core.call(CoreCalls.openPlugin(plugin, 0)));
            List<PluginEvent> heard = new ArrayList<>();
            AtomicBoolean first = new AtomicBoolean(true);
            fixture.core.addListener(event -> {
                if (first.getAndSet(false)) {
                    fixture.core.call(CoreCalls.getService(plugin, "undeclared"));
                    fixture.contexts.make(plugin, manifest).listen(heard::add);
                }
            });

            fixture.core.call(CoreCalls.unregister(424242L));

            assertEquals(Collections.emptyList(), heard);
        }
    }

    @Test
    void endingAPluginsEntriesCallsNothingInTheCore() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            value(provider.register(GREETER, new Object()));
            value(provider.watch(GREETER, ServicesTest.recording(new ArrayList<>())));
            ok(fixture.core.call(CoreCalls.releasePlugin(fixture.plugin(provider))));
            long before = fixture.crossings.total();
            int entriesBefore = fixture.handles.entryCount();

            fixture.contexts.endEntries(provider);

            assertEquals(before, fixture.crossings.total());
            assertEquals(entriesBefore - 2, fixture.handles.entryCount());
        }
    }

    @Test
    void theContextIsKeptAcrossAQuarantineAndASecondOpen() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            value(provider.register(GREETER, new Object()));
            long payload = fixture.contexts.payloadOf(provider);

            fixture.quarantine(provider);
            assertEquals(new Answer.Absent<Object>(), consumer.get(GREETER));
            fixture.open(provider);
            Object reopened = new Object();
            value(provider.register(GREETER, reopened));

            assertSame(provider, fixture.contexts.byPayload(payload).get());
            assertEquals(payload, fixture.contexts.payloadOf(provider));
            assertSame(reopened, value(consumer.get(GREETER)));
        }
    }

    @Test
    void aContextIsMadeOncePerPlugin() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext once = fixture.registered(Manifest.named("once"));

            assertThrows(IllegalStateException.class, () -> fixture.contexts.make(fixture.plugin(once),
                    Manifest.named("once")));
        }
    }

    @Test
    void onceForgottenEveryCallThatWouldReachTheCoreIsDetachedAndReachesNothing() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            fixture.release(consumer);
            assertInstanceOf(Answer.Ok.class, fixture.contexts.forget(consumer));
            long before = fixture.crossings.total();

            List<Answer<?>> answers = Arrays.asList(consumer.get(GREETER), consumer.ids(),
                    consumer.watch(GREETER, ServicesTest.recording(new ArrayList<>())),
                    consumer.register("consumer:own", new Object()), consumer.provide("screens", new Object()),
                    consumer.implementations("screens"),
                    consumer.watchCapability("screens", CapabilitiesTest.recording(new ArrayList<>())),
                    consumer.want(GREETER, WantLimits.unbounded()).settlement().join(),
                    fixture.contexts.forget(consumer));

            answers.forEach(answer -> assertInstanceOf(Answer.Detached.class, answer, answer.toString()));
            assertEquals(before, fixture.crossings.total());
            assertFalse(fixture.contexts.byPayload(fixture.contexts.payloadOf(consumer)).isPresent());
        }
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void whetherACallIsDetachedIsDecidedUnderTheLayerLockAsTheCallIsMade() throws Exception {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            ok(fixture.core.call(CoreCalls.wantService(CoreConstants.HOST, "armed", WantOptions.timed(0, 1000))));
            fixture.release(consumer);
            CountDownLatch reading = new CountDownLatch(1);
            CountDownLatch forgotten = new CountDownLatch(1);
            ExecutorService threads = Executors.newSingleThreadExecutor();
            try {
                Thread[] getter = new Thread[1];
                fixture.scheduler.whenRead(() -> {
                    if (Thread.currentThread() == getter[0] && reading.getCount() > 0) {
                        reading.countDown();
                        await(forgotten);
                    }
                });
                Future<Answer<Object>> got = threads.submit(() -> {
                    getter[0] = Thread.currentThread();
                    return consumer.get(GREETER);
                });
                assertTrue(reading.await(GATE_SECONDS, TimeUnit.SECONDS));
                assertInstanceOf(Answer.Ok.class, fixture.contexts.forget(consumer));
                forgotten.countDown();

                assertInstanceOf(Answer.Detached.class, got.get(GATE_SECONDS, TimeUnit.SECONDS));
                List<String> symbols = fixture.crossings.symbols();
                assertTrue(symbols.lastIndexOf("weivel_get_service") < symbols.indexOf("weivel_forget_plugin"),
                        symbols.toString());
            } finally {
                forgotten.countDown();
                threads.shutdownNow();
            }
        }
    }

    @Test
    void aWantMadeBeforeTheForgetIsStillSettledToIt() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            fixture.release(consumer);
            CompletableFuture<Answer<Object>> late = consumer.want(GREETER, WantLimits.unbounded()).settlement();

            fixture.contexts.forget(consumer);

            assertEquals(CoreConstants.REFUSAL_STOPPED, WantTest.refusalOf(late).code());
            assertEquals(Optional.empty(), fixture.contexts.byPayload(fixture.contexts.payloadOf(consumer)));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(GATE_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("a gate was never opened");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @Test
    void aListenerAddedAfterAReleaseHearsNothingQueuedAfterIt() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            fixture.release(consumer);
            List<PluginEvent> heard = new ArrayList<>();
            consumer.listen(heard::add);

            consumer.get("undeclared");

            assertEquals(Collections.emptyList(), heard);
        }
    }

    @Test
    void aQuarantinedPluginOpenedAgainHearsWhatIsQueuedForItFromThatOpenOn() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            fixture.quarantine(consumer);
            List<PluginEvent> heard = new ArrayList<>();
            consumer.listen(heard::add);
            consumer.get("undeclared-while-quarantined");

            fixture.open(consumer);
            consumer.get("undeclared-once-open");

            assertEquals(1, heard.size());
            assertEquals("undeclared-once-open",
                    assertInstanceOf(PluginEvent.ReachRefused.class, heard.get(0)).id());
        }
    }

    @Test
    void aDetachedContextAnswersDetachedBeforeItJudgesTheCallersMistake() {
        try (ContextFixture fixture = new ContextFixture(CoreOptions.defaults(), false, Scheduler.none())) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            PendingWant mistaken = consumer.want(GREETER, WantLimits.timeout(100));
            fixture.release(consumer);
            value(fixture.contexts.forget(consumer));

            assertInstanceOf(Answer.Detached.class, consumer.get("a\u0000b"));
            assertInstanceOf(Answer.Detached.class, consumer.register("a\u0000b", new Object()));
            assertInstanceOf(Answer.Detached.class,
                    consumer.want(GREETER, WantLimits.timeout(100)).settlement().join());
            assertInstanceOf(Answer.Detached.class, consumer.want(GREETER, WantLimits.timeout(100)).withdraw());
            assertInstanceOf(Answer.Detached.class, mistaken.withdraw());
        }
    }

    @Test
    void aListenerHearingTheForgetsOwnEventsFindsTheContextDetached() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            value(provider.register(GREETER, new Object()));
            fixture.release(consumer);
            value(consumer.get(GREETER));
            List<Answer<?>> answeredDuringTheForget = new ArrayList<>();
            fixture.core.addListener(event -> {
                if (event instanceof CoreEvent.RevokedEvent) {
                    answeredDuringTheForget.add(consumer.ids());
                }
            });

            value(fixture.contexts.forget(consumer));

            assertEquals(1, answeredDuringTheForget.size());
            assertInstanceOf(Answer.Detached.class, answeredDuringTheForget.get(0));
        }
    }

    @Test
    void closingTheContextsSettlesEveryWantLeftWaitingAsDestroyed() {
        ContextFixture fixture = new ContextFixture();
        PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
        CompletableFuture<Answer<Object>> settlement = consumer.want(GREETER, WantLimits.unbounded()).settlement();

        fixture.close();

        assertInstanceOf(Answer.Destroyed.class, settlement.getNow(null));
    }

    @Test
    void anOpenFromAnotherThreadLandingBetweenAQuarantineAndItsDrainLeavesThePluginHearing() throws Exception {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            ok(fixture.core.call(CoreCalls.wantService(CoreConstants.HOST, "later", WantOptions.timed(0, 200))));
            consumer.want(GREETER, WantLimits.timeout(100));
            ExecutorService threads = Executors.newSingleThreadExecutor();
            try {
                AtomicBoolean once = new AtomicBoolean(true);
                List<Answer<Void>> opened = new ArrayList<>();
                fixture.scheduler.whenScheduled(at -> {
                    if (at == 200 && once.getAndSet(false)) {
                        opened.add(getWithin(threads.submit(() -> fixture.contexts.open(consumer))));
                    }
                });

                fixture.quarantine(consumer);
                List<PluginEvent> heard = new ArrayList<>();
                consumer.listen(heard::add);
                consumer.get("undeclared-once-open");

                assertInstanceOf(Answer.Ok.class, opened.get(0));
                assertEquals(1, heard.size());
                assertEquals("undeclared-once-open",
                        assertInstanceOf(PluginEvent.ReachRefused.class, heard.get(0)).id());
            } finally {
                threads.shutdownNow();
            }
        }
    }

    private static <T> T getWithin(Future<T> future) {
        try {
            return future.get(GATE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failed) {
            throw new IllegalStateException(failed);
        }
    }
}
