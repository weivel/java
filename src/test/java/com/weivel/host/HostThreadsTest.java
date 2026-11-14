package com.weivel.host;

import static com.weivel.host.HostFixture.consuming;
import static com.weivel.host.HostFixture.done;
import static com.weivel.host.HostFixture.named;
import static com.weivel.host.HostFixture.providingCapability;
import static com.weivel.host.HostFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.context.CapabilityWatcher;
import com.weivel.context.ServiceWatcher;
import com.weivel.core.Answer;
import com.weivel.core.RefusalStatus;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The host's steps that another thread can interleave with, each interleaving forced by gates. */
class HostThreadsTest {
    private static final long WAIT_SECONDS = 10;

    @Test
    @Timeout(30)
    void activationStartsEveryActivateOnTheActivatingThreadAndReturnsWhileAnotherThreadDispatches()
            throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            Thread dispatcher = blockedDispatcher(fixture);
            TestEntry first = fixture.entry("first");
            TestEntry second = fixture.entry("second");
            long firstPlugin = fixture.register(named("first"), first);
            long secondPlugin = fixture.register(named("second"), second);

            Activation activation = fixture.activate(firstPlugin, secondPlugin);

            assertEquals(2, activation.plan().order().size());
            assertSame(Thread.currentThread(), first.activatedOn);
            assertSame(Thread.currentThread(), second.activatedOn);
            assertTrue(dispatcher.isAlive());
            assertEquals(ActivationState.ACTIVE, done(activation.outcomes().get(0)).state());
            release(dispatcher);
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
            Thread dispatcher = blockedDispatcher(fixture);

            activated.complete(null);

            assertEquals(ActivationState.QUARANTINED, done(activation.outcomes().get(0)).state());
            assertTrue(dispatcher.isAlive());
            Answer<?> late = flaky.context.register("flaky:late", new Object());
            assertEquals(RefusalStatus.REFUSED, assertInstanceOf(Answer.Refused.class, late).status());
            release(dispatcher);
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
            await(proceed);
            return answer;
        });
        long plugin = fixture.register(named("slow"), fixture.entry("slow"));
        AtomicReference<Activation> activation = new AtomicReference<>();
        Thread activating = new Thread(() -> activation.set(fixture.activate(plugin)));
        activating.start();
        await(opening);

        CompletionStage<DestroyResult> destroyed = fixture.host.destroy();

        assertFalse(destroyed.toCompletableFuture().isDone());
        proceed.countDown();
        activating.join();
        assertEquals(ActivationState.RELEASED, done(activation.get().outcomes().get(0)).state());
        assertTrue(fixture.trace().isEmpty());
        destroyed.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
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
                    await(proceed);
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
        await(watching);

        CompletionStage<DestroyResult> destroyed = fixture.host.destroy();

        assertFalse(destroyed.toCompletableFuture().isDone());
        proceed.countDown();
        settling.join();
        destroyed.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
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

    /**
     * A thread dispatching the host's core, held inside a plugin's watcher until {@link #release} lets it go: a
     * plugin watching {@code signal} hears the host register it on that thread.
     */
    private static Thread blockedDispatcher(HostFixture fixture) throws InterruptedException {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        TestEntry watcher = fixture.entry("blocking");
        long plugin = fixture.register(consuming("blocking", "signal"), watcher);
        fixture.activate(plugin);
        value(watcher.context.watch("signal", new ServiceWatcher() {
            @Override
            public void registered(Object service) {
                inside.countDown();
                await(proceed);
            }

            @Override
            public void unregistered() {
            }
        }));
        Thread dispatcher = new Thread(() -> fixture.host.registerHostService("signal", new Object()));
        GATES.put(dispatcher, proceed);
        dispatcher.start();
        await(inside);
        return dispatcher;
    }

    private static final java.util.Map<Thread, CountDownLatch> GATES =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    private static void release(Thread dispatcher) throws InterruptedException {
        GATES.remove(dispatcher).countDown();
        dispatcher.join();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("a gate was never opened");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
