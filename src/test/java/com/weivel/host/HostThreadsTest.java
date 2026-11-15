package com.weivel.host;

import static com.weivel.host.HostFixture.consuming;
import static com.weivel.host.HostFixture.done;
import static com.weivel.host.HostFixture.named;
import static com.weivel.host.HostFixture.providingCapability;
import static com.weivel.host.HostFixture.services;
import static com.weivel.host.HostFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.context.CapabilityWatcher;
import com.weivel.context.ServiceWatcher;
import com.weivel.core.Answer;
import com.weivel.core.LedgerRow;
import com.weivel.core.RefusalStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The host's steps that another thread can interleave with, each interleaving forced by gates. */
class HostThreadsTest {
    @Test
    @Timeout(30)
    void activationStartsEveryActivateOnTheActivatingThreadAndReturnsWhileAnotherThreadDispatches()
            throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            Gates.BlockedDispatcher dispatcher = new Gates.BlockedDispatcher(fixture);
            TestEntry first = fixture.entry("first");
            TestEntry second = fixture.entry("second");
            long firstPlugin = fixture.register(named("first"), first);
            long secondPlugin = fixture.register(named("second"), second);

            Activation activation = fixture.activate(firstPlugin, secondPlugin);

            assertEquals(2, activation.plan().order().size());
            assertSame(Thread.currentThread(), first.activatedOn);
            assertSame(Thread.currentThread(), second.activatedOn);
            assertTrue(dispatcher.isDispatching());
            assertEquals(ActivationState.ACTIVE, done(activation.outcomes().get(0)).state());
            dispatcher.release();
        }
    }

    @Test
    @Timeout(30)
    void whileAnotherThreadDispatchesAnActivatesEventsJoinItsQueueAndASettledActivateIsVerifiedBeforeTheNext()
            throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            List<Thread> heardOn = Collections.synchronizedList(new ArrayList<Thread>());
            TestEntry listening = fixture.entry("listening");
            long listeningPlugin = fixture.register(consuming("listening", "joining:events"), listening);
            fixture.activate(listeningPlugin);
            value(listening.context.watch("joining:events", new ServiceWatcher() {
                @Override
                public void registered(Object service) {
                    heardOn.add(Thread.currentThread());
                }

                @Override
                public void unregistered() {
                }
            }));
            Gates.BlockedDispatcher dispatcher = new Gates.BlockedDispatcher(fixture);
            long lacking = fixture.register(providingCapability("lacking", "screens"), fixture.entry("lacking"));
            TestEntry joining = fixture.entry("joining");
            AtomicReference<List<LedgerRow>> seenByJoining = new AtomicReference<>();
            joining.onActivate = context -> {
                seenByJoining.set(value(fixture.host.ledger()));
                value(context.register("joining:events", new Object()));
                return CompletableFuture.completedFuture(null);
            };
            long joiningPlugin = fixture.register(services("joining", Collections.singletonList("joining:events"),
                    Collections.<String>emptyList()), joining);

            Activation activation = fixture.activate(lacking, joiningPlugin);

            assertEquals(Arrays.asList(lacking, joiningPlugin), activation.plan().order());
            assertEquals(LedgerRow.Status.QUARANTINED, seenByJoining.get().stream()
                    .filter(row -> row.plugin() == lacking).findFirst().get().status());
            assertEquals(ActivationState.QUARANTINED, done(activation.outcomes().get(0)).state());
            assertTrue(heardOn.isEmpty(), heardOn.toString());
            Thread dispatching = dispatcher.thread();
            dispatcher.release();
            assertEquals(Collections.singletonList(dispatching), heardOn);
            assertTrue(fixture.failures.isEmpty());
        }
    }

    @Test
    @Timeout(30)
    void aRefusedVerificationIsQuarantinedInItsOwnHoldWhileAnotherThreadDispatches() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            TestEntry flaky = fixture.entry("flaky");
            CompletableFuture<Void> activated = new CompletableFuture<>();
            flaky.onActivate = context -> activated;
            long plugin = fixture.register(providingCapability("flaky", "screens"), flaky);
            Activation activation = fixture.activate(plugin);
            Gates.BlockedDispatcher dispatcher = new Gates.BlockedDispatcher(fixture);

            activated.complete(null);

            assertEquals(ActivationState.QUARANTINED, done(activation.outcomes().get(0)).state());
            assertTrue(dispatcher.isDispatching());
            Answer<?> late = flaky.context.register("flaky:late", new Object());
            assertEquals(RefusalStatus.REFUSED, assertInstanceOf(Answer.Refused.class, late).status());
            dispatcher.release();
            assertTrue(fixture.failures.isEmpty());
        }
    }

    @Test
    @Timeout(30)
    void destroyWaitsForAnOpenBegunWhileTheHostWasRunning() throws Exception {
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        HostFixture fixture = new HostFixture((contexts, context, opened) -> {
            Answer<Void> answer = contexts.open(context, opened);
            opening.countDown();
            Gates.await(proceed);
            return answer;
        });
        long plugin = fixture.register(named("slow"), fixture.entry("slow"));
        AtomicReference<Activation> activation = new AtomicReference<>();
        Thread activating = new Thread(() -> activation.set(fixture.activate(plugin)));
        activating.start();
        Gates.await(opening);

        CompletionStage<DestroyResult> destroyed = fixture.host.destroy();

        assertFalse(destroyed.toCompletableFuture().isDone());
        proceed.countDown();
        activating.join();
        assertEquals(ActivationState.RELEASED, done(activation.get().outcomes().get(0)).state());
        assertTrue(fixture.trace().isEmpty());
        destroyed.toCompletableFuture().get(Gates.WAIT_SECONDS, TimeUnit.SECONDS);
        assertFalse(fixture.host.manifest(plugin).isPresent());
        fixture.host.close();
    }

    @Test
    @Timeout(30)
    void destroyWaitsForASettlingUnderWayAndLetsItAnswerItsOutcome() throws Exception {
        HostFixture fixture = new HostFixture();
        CountDownLatch watching = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        TestEntry watcher = fixture.entry("watcher");
        watcher.onActivate = context -> {
            value(context.watchCapability("screens", new CapabilityWatcher() {
                @Override
                public void provided(Object implementation) {
                    watching.countDown();
                    Gates.await(proceed);
                }

                @Override
                public void withdrawn(Object implementation) {
                }
            }));
            return CompletableFuture.completedFuture(null);
        };
        TestEntry screen = fixture.entry("screen");
        CompletableFuture<Void> activated = new CompletableFuture<>();
        screen.onActivate = context -> {
            value(context.provide("screens", new Object()));
            return activated;
        };
        long watcherPlugin = fixture.register(capabilityConsumer("watcher"), watcher);
        long screenPlugin = fixture.register(providingCapability("screen", "screens"), screen);
        Activation activation = fixture.activate(watcherPlugin, screenPlugin);
        Thread settling = new Thread(() -> activated.complete(null));
        settling.start();
        Gates.await(watching);

        CompletionStage<DestroyResult> destroyed = fixture.host.destroy();

        assertFalse(destroyed.toCompletableFuture().isDone());
        proceed.countDown();
        settling.join();
        destroyed.toCompletableFuture().get(Gates.WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(ActivationState.ACTIVE, done(activation.outcomes().get(1)).state());
        assertTrue(fixture.trace().contains("deactivate screen"));
        fixture.host.close();
    }

    private static com.weivel.core.Manifest capabilityConsumer(String importName) {
        return new com.weivel.core.Manifest(importName, 0, java.util.Optional.empty(),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonList("screens"), Collections.<com.weivel.core.Manifest.ServiceOffer>emptyList(),
                Collections.<com.weivel.core.Manifest.ServiceWant>emptyList(), Collections.<String>emptyList(),
                java.util.Optional.empty());
    }
}
