package com.weivel.host;

import static com.weivel.host.HostFixture.done;
import static com.weivel.host.HostFixture.named;
import static com.weivel.host.HostFixture.services;
import static com.weivel.host.HostFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.CoreConstants;
import com.weivel.core.Refusal;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Calls landing inside one of the host's steps on another thread, each placed there by a gate. */
class HostRacesTest {
    @Test
    @Timeout(30)
    void aReleaseBeforeTheActivateIsClaimedCallsNeitherTheDeactivateNorTheActivate() throws Exception {
        Gates.Gate gate = new Gates.Gate();
        try (HostFixture fixture = new HostFixture(new Pauses() {
            @Override
            public void beforeTheActivateIsClaimed(long plugin) {
                gate.hold();
            }
        })) {
            long plugin = fixture.register(named("raced"), fixture.entry("raced"));
            AtomicReference<Activation> activation = new AtomicReference<>();
            Thread activating = new Thread(() -> activation.set(fixture.activate(plugin)));
            activating.start();
            gate.awaitReached();

            CompletionStage<Answer<Boolean>> release = fixture.host.release(plugin);
            gate.open();
            activating.join();

            assertTrue(value(release.toCompletableFuture().get(Gates.WAIT_SECONDS, TimeUnit.SECONDS)));
            assertEquals(ActivationState.RELEASED, done(activation.get().outcomes().get(0)).state());
            assertTrue(fixture.trace().isEmpty(), fixture.trace().toString());
        }
    }

    @Test
    @Timeout(30)
    void aReleaseAfterTheActivateIsClaimedDeactivatesOnlyOnceTheActivateWasCalled() throws Exception {
        Gates.Gate gate = new Gates.Gate();
        try (HostFixture fixture = new HostFixture(new Pauses() {
            @Override
            public void afterTheActivateIsClaimed(long plugin) {
                gate.hold();
            }
        })) {
            long plugin = fixture.register(named("raced"), fixture.entry("raced"));
            Thread activating = new Thread(() -> fixture.activate(plugin));
            activating.start();
            gate.awaitReached();

            CompletionStage<Answer<Boolean>> release = fixture.host.release(plugin);
            assertTrue(fixture.trace().isEmpty(), fixture.trace().toString());
            gate.open();
            activating.join();

            assertTrue(value(release.toCompletableFuture().get(Gates.WAIT_SECONDS, TimeUnit.SECONDS)));
            assertEquals(Arrays.asList("activate raced", "deactivate raced"), fixture.trace());
        }
    }

    @Test
    @Timeout(30)
    void anActivateSettlingWhileTheDestroyWaitsBehindAnotherThreadsDispatchIsQuietAndReleased() throws Exception {
        HostFixture fixture = new HostFixture();
        TestEntry pending = fixture.entry("pending");
        CompletableFuture<Void> activated = new CompletableFuture<>();
        pending.onActivate = context -> activated;
        Activation activation = fixture.activate(fixture.register(named("pending"), pending));
        Gates.BlockedDispatcher dispatcher = new Gates.BlockedDispatcher(fixture);
        CompletionStage<DestroyResult> destroyed = fixture.host.destroy();

        activated.complete(null);

        assertFalse(activation.outcomes().get(0).toCompletableFuture().isDone());
        dispatcher.release();
        destroyed.toCompletableFuture().get(Gates.WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(ActivationState.RELEASED, done(activation.outcomes().get(0)).state());
        assertTrue(fixture.failures.isEmpty());
        fixture.host.close();
    }

    @Test
    @Timeout(30)
    void aVerifyMadeWhileTheDestroyWaitsIsAnsweredDestroyedQuietly() throws Exception {
        HostFixture fixture = new HostFixture();
        TestEntry slow = fixture.entry("slow");
        CompletableFuture<Void> deactivated = new CompletableFuture<>();
        slow.onDeactivate = () -> deactivated;
        long plugin = fixture.register(named("slow"), slow);
        fixture.activate(plugin);
        CompletionStage<DestroyResult> destroyed = fixture.host.destroy();
        int logged = fixture.logs.size();

        Answer<Outcome> verified = fixture.host.verify(plugin);

        assertInstanceOf(Answer.Destroyed.class, verified);
        assertEquals(logged, fixture.logs.size());
        deactivated.complete(null);
        destroyed.toCompletableFuture().get(Gates.WAIT_SECONDS, TimeUnit.SECONDS);
        assertTrue(fixture.failures.isEmpty());
        fixture.host.close();
    }

    @Test
    @Timeout(30)
    void aVerifyRacingTheCoreReleaseOfItsPluginIsAnsweredTheCoresRefusalNotQuarantined() throws Exception {
        Gates.Gate verifying = new Gates.Gate();
        try (HostFixture fixture = new HostFixture(new Pauses() {
            @Override
            public void beforeTheVerification(long plugin) {
                verifying.hold();
            }
        })) {
            long plugin = fixture.register(named("released"), fixture.entry("released"));
            fixture.activate(plugin);
            AtomicReference<Answer<Outcome>> verified = new AtomicReference<>();
            Thread verifier = new Thread(() -> verified.set(fixture.host.verify(plugin)));
            verifier.start();
            verifying.awaitReached();

            Gates.BlockedDispatcher dispatcher = new Gates.BlockedDispatcher(fixture, () -> {
                fixture.host.release(plugin);
                verifying.open();
                try {
                    verifier.join();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            dispatcher.release();

            Refusal refusal = (Refusal) assertInstanceOf(Answer.Refused.class, verified.get()).refusal().get();
            assertEquals(CoreConstants.REFUSAL_PLUGIN_STATE, refusal.code());
            assertTrue(fixture.failures.isEmpty());
        }
    }

    @Test
    @Timeout(30)
    void aPlanMadeOnceTheCoreRegisteredAHostServiceCountsItAsProvidedByTheHost() throws Exception {
        Gates.Gate registering = new Gates.Gate();
        try (HostFixture fixture = new HostFixture(new Pauses() {
            @Override
            public void afterAHostServiceIsRegistered(String id) {
                registering.hold();
            }
        })) {
            Thread registrar = new Thread(() -> fixture.host.registerHostService("clock", new Object()));
            registrar.start();
            registering.awaitReached();
            long needy = fixture.register(services("needy", Collections.<String>emptyList(),
                    Collections.singletonList("clock")), fixture.entry("needy"));

            Activation activation = fixture.activate(needy);

            registering.open();
            registrar.join();
            assertEquals(Collections.singletonList(needy), activation.plan().order());
        }
    }

    @Test
    @Timeout(30)
    void aReleaseWhileThePluginsOpenIsUnderWayReleasesItOnceOpenedWithoutItsActivate() throws Exception {
        Gates.Gate opening = new Gates.Gate();
        try (HostFixture fixture = new HostFixture((contexts, context, opened) -> {
            Answer<Void> answer = contexts.open(context, opened);
            opening.hold();
            return answer;
        })) {
            long plugin = fixture.register(named("opening"), fixture.entry("opening"));
            AtomicReference<Activation> activation = new AtomicReference<>();
            Thread activating = new Thread(() -> activation.set(fixture.activate(plugin)));
            activating.start();
            opening.awaitReached();

            CompletableFuture<Answer<Boolean>> release = fixture.host.release(plugin).toCompletableFuture();

            assertFalse(release.isDone());
            opening.open();
            activating.join();
            assertTrue(value(release.get(Gates.WAIT_SECONDS, TimeUnit.SECONDS)));
            assertEquals(ActivationState.RELEASED, done(activation.get().outcomes().get(0)).state());
            assertTrue(fixture.trace().isEmpty(), fixture.trace().toString());
            assertFalse(fixture.host.manifest(plugin).isPresent());
        }
    }
}
