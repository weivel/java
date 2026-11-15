package com.weivel.host;

import static com.weivel.host.HostFixture.done;
import static com.weivel.host.HostFixture.consuming;
import static com.weivel.host.HostFixture.named;
import static com.weivel.host.HostFixture.optionally;
import static com.weivel.host.HostFixture.providingCapability;
import static com.weivel.host.HostFixture.services;
import static com.weivel.host.HostFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.context.PluginContext;
import com.weivel.context.ServiceWatcher;
import com.weivel.context.WantLimits;
import com.weivel.core.ActivationPlan;
import com.weivel.core.Answer;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreProbes;
import com.weivel.core.EdgeSource;
import com.weivel.core.FailureKind;
import com.weivel.core.LedgerRow;
import com.weivel.core.Manifest;
import com.weivel.core.Refusal;
import com.weivel.core.RefusalStatus;
import com.weivel.timer.TestScheduler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The host section of the core's contract, step by step, against the real core. */
class HostTest {
    private final HostFixture fixture = new HostFixture();
    private final Host host = fixture.host;

    @AfterEach
    void destroy() throws Exception {
        fixture.close();
    }

    @Test
    void aManifestIsRegisteredAsACopyTheCallerCannotChange() {
        List<String> targets = new ArrayList<>(Collections.singletonList("first"));
        Manifest given = new Manifest("copied", 0, Optional.empty(), targets, Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<Manifest.ServiceOffer>emptyList(),
                Collections.<Manifest.ServiceWant>emptyList(), Collections.<String>emptyList(), Optional.empty());

        long plugin = fixture.register(given, fixture.entry("copied"));
        targets.add("second");

        assertEquals(Collections.singletonList("first"), host.manifest(plugin).get().targets());
        Answer<Long> again = host.register(named("copied"), fixture.entry("again"));
        assertEquals(RefusalStatus.REFUSED, assertInstanceOf(Answer.Refused.class, again).status());
        assertTrue(fixture.failures.isEmpty());
    }

    @Test
    void aHostServiceMeetsAPlannedNeedAndIsAnsweredAsTheHostsOwnObject() {
        Object clock = new Object();
        HostService registered = value(host.registerHostService("clock", clock));
        TestEntry needy = fixture.entry("needy");
        AtomicReference<Object> got = new AtomicReference<>();
        needy.onActivate = context -> {
            got.set(value(context.get("clock")));
            return CompletableFuture.completedFuture(null);
        };
        long plugin = fixture.register(services("needy", Collections.<String>emptyList(),
                Collections.singletonList("clock")), needy);

        Activation activation = fixture.activate(plugin);

        assertEquals(Collections.singletonList(plugin), activation.plan().order());
        assertSame(clock, got.get());
        value(registered.unregister());
        long later = fixture.register(services("later", Collections.<String>emptyList(),
                Collections.singletonList("clock")), fixture.entry("later"));
        ActivationPlan unmet = fixture.activate(later).plan();
        assertEquals(CoreConstants.REFUSAL_UNMET_NEED, unmet.refusals().get(0).refusal().code());
    }

    @Test
    void activationStartsEachPluginInPlanOrderOnTheActivatingThreadWithoutWaitingForOne() {
        TestEntry consumer = fixture.entry("consumer");
        TestEntry provider = fixture.entry("provider");
        CompletableFuture<Void> providerDone = new CompletableFuture<>();
        provider.onActivate = context -> providerDone;
        long consumerPlugin = fixture.register(services("consumer", Collections.<String>emptyList(),
                Collections.singletonList("provider:events")), consumer);
        long providerPlugin = fixture.register(services("provider", Collections.singletonList("provider:events"),
                Collections.<String>emptyList()), provider);

        Activation activation = fixture.activate(consumerPlugin, providerPlugin);

        assertEquals(Arrays.asList(providerPlugin, consumerPlugin), activation.plan().order());
        assertEquals(Arrays.asList("activate provider", "activate consumer"), fixture.trace());
        assertSame(Thread.currentThread(), provider.activatedOn);
        assertSame(Thread.currentThread(), consumer.activatedOn);
        assertFalse(activation.outcomes().get(0).toCompletableFuture().isDone());
        assertEquals(ActivationState.ACTIVE, done(activation.outcomes().get(1)).state());
        providerDone.complete(null);
        assertEquals(ActivationState.ACTIVE, done(activation.outcomes().get(0)).state());
    }

    @Test
    void whatAnActivatesOwnCallsQueueIsHeardOnceThatActivateHasReturned() {
        TestEntry waiting = fixture.entry("waiting");
        waiting.onActivate = context -> context.want("serving:events", WantLimits.unbounded()).settlement()
                .thenRun(() -> fixture.trace.add("settled waiting"));
        TestEntry serving = fixture.entry("serving");
        serving.onActivate = context -> {
            value(context.register("serving:events", new Object()));
            fixture.trace.add("registered serving");
            return CompletableFuture.completedFuture(null);
        };
        long waitingPlugin = fixture.register(consuming("waiting", "serving:events"), waiting);
        long servingPlugin = fixture.register(services("serving", Collections.singletonList("serving:events"),
                Collections.<String>emptyList()), serving);

        host.activate(Arrays.asList(waitingPlugin, servingPlugin), EdgeSource.CAPABILITIES);

        assertEquals(Arrays.asList("activate waiting", "activate serving", "registered serving", "settled waiting"),
                fixture.trace());
    }

    @Test
    void anActivateThatThrowsAnswersNoStageOrFailsIsQuarantinedWithAReasonSayingSo() {
        TestEntry throwing = fixture.entry("throwing");
        throwing.onActivate = context -> {
            throw new IllegalStateException("broken");
        };
        TestEntry stageless = fixture.entry("stageless");
        stageless.onActivate = context -> null;
        TestEntry failing = fixture.entry("failing");
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("failed later"));
        failing.onActivate = context -> failed;
        List<Long> plugins = Arrays.asList(fixture.register(named("throwing"), throwing),
                fixture.register(named("stageless"), stageless), fixture.register(named("failing"), failing));

        Activation activation = value(host.activate(plugins, EdgeSource.SERVICES));

        for (int index = 0; index < plugins.size(); index++) {
            assertEquals(ActivationState.QUARANTINED, done(activation.outcomes().get(index)).state());
        }
        assertEquals(RefusalStatus.REFUSED, assertInstanceOf(Answer.Refused.class,
                throwing.context.register("throwing:late", new Object())).status());
        assertTrue(fixture.logs.stream().anyMatch(record -> record.text().contains("broken")));
        assertTrue(fixture.failures.isEmpty());
    }

    @Test
    void aSecondOpenOfAPluginHeldOpenIsRefusedByTheCoreWithItsContextUnchanged() {
        TestEntry once = fixture.entry("once");
        long plugin = fixture.register(named("once"), once);
        fixture.activate(plugin);
        PluginContext context = host.context(plugin).get();

        Outcome again = done(fixture.activate(plugin).outcomes().get(0));

        assertEquals(ActivationState.REFUSED, again.state());
        assertEquals(CoreConstants.REFUSAL_PLUGIN_STATE, again.refusal().get().code());
        assertSame(context, host.context(plugin).get());
        assertEquals(Collections.singletonList("activate once"), fixture.trace());
    }

    @Test
    void aQuarantinedPluginOpenedAgainKeepsItsContext() {
        TestEntry flaky = fixture.entry("flaky");
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("first try"));
        flaky.onActivate = context -> failed;
        long plugin = fixture.register(named("flaky"), flaky);
        assertEquals(ActivationState.QUARANTINED, done(fixture.activate(plugin).outcomes().get(0)).state());
        PluginContext first = flaky.context;
        flaky.onActivate = context -> CompletableFuture.completedFuture(null);

        Outcome second = done(fixture.activate(plugin).outcomes().get(0));

        assertEquals(ActivationState.ACTIVE, second.state());
        assertSame(first, flaky.context);
    }

    @Test
    void aRefusedVerificationQuarantinesThePluginSoItsWatchStopsThere() {
        List<String> heard = Collections.synchronizedList(new ArrayList<>());
        TestEntry lacking = fixture.entry("lacking");
        lacking.onActivate = context -> {
            value(context.watch("clock", recording(heard)));
            return CompletableFuture.completedFuture(null);
        };
        long plugin = fixture.register(providingCapability("lacking", "screens", "clock"), lacking);

        Outcome outcome = done(fixture.activate(plugin).outcomes().get(0));
        value(host.registerHostService("clock", new Object()));

        assertEquals(ActivationState.QUARANTINED, outcome.state());
        assertTrue(heard.isEmpty());
        assertTrue(fixture.failures.isEmpty());
    }

    @Test
    void anOutcomeCarriesTheRefusalThatDecidedItAndAQuarantineForAHostReasonCarriesNone() {
        TestEntry failing = fixture.entry("failing");
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("failed on purpose"));
        failing.onActivate = context -> failed;
        long lacking = fixture.register(providingCapability("lacking", "screens"), fixture.entry("lacking"));
        long failingPlugin = fixture.register(named("failing"), failing);
        long whole = fixture.register(named("whole"), fixture.entry("whole"));

        Activation activation = value(host.activate(Arrays.asList(lacking, failingPlugin, whole),
                EdgeSource.SERVICES));

        Outcome refusedVerification = outcomeOf(activation, lacking);
        assertEquals(ActivationState.QUARANTINED, refusedVerification.state());
        assertEquals(CoreConstants.REFUSAL_ACTIVATION_MISMATCH, refusedVerification.refusal().get().code());
        assertEquals(Optional.of("lacking"), refusedVerification.refusal().get().subject());
        Outcome hostReason = outcomeOf(activation, failingPlugin);
        assertEquals(ActivationState.QUARANTINED, hostReason.state());
        assertEquals(Optional.<Refusal>empty(), hostReason.refusal());
        assertEquals(Optional.<Refusal>empty(), outcomeOf(activation, whole).refusal());
        Outcome refusedOpen = done(fixture.activate(whole).outcomes().get(0));
        assertEquals(ActivationState.REFUSED, refusedOpen.state());
        assertEquals(CoreConstants.REFUSAL_PLUGIN_STATE, refusedOpen.refusal().get().code());
    }

    @Test
    void aVirtualMachineErrorFromAnActivatePropagatesOnceTheHostCanStillReleaseThePlugin() throws Exception {
        TestEntry overflowing = fixture.entry("overflowing");
        overflowing.onActivate = context -> {
            throw new StackOverflowError("an overflow on purpose");
        };
        long plugin = fixture.register(named("overflowing"), overflowing);

        assertThrows(StackOverflowError.class, () -> fixture.activate(plugin));
        Answer<Boolean> released = host.release(plugin).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals(new Answer.Ok<>(true), released);
        assertEquals(Arrays.asList("activate overflowing", "deactivate overflowing"), fixture.trace());
        assertTrue(fixture.failures.isEmpty());
    }

    @Test
    void aSettledActivateIsVerifiedAndARefusedOneQuarantinedBeforeTheNextPluginIsStarted() {
        AtomicReference<List<LedgerRow>> seenBySecond = new AtomicReference<>();
        long lacking = fixture.register(providingCapability("lacking", "screens"), fixture.entry("lacking"));
        long whole = fixture.register(named("whole"), fixture.entry("whole"));
        TestEntry second = fixture.entry("second");
        second.onActivate = context -> {
            seenBySecond.set(value(host.ledger()));
            return CompletableFuture.completedFuture(null);
        };
        long secondPlugin = fixture.register(named("second"), second);

        Activation activation = value(host.activate(Arrays.asList(lacking, whole, secondPlugin),
                EdgeSource.SERVICES));

        assertEquals(Arrays.asList(lacking, whole, secondPlugin), activation.plan().order());
        assertEquals(LedgerRow.Status.QUARANTINED, statusOf(seenBySecond.get(), lacking));
        assertEquals(LedgerRow.Status.ACTIVE, statusOf(seenBySecond.get(), whole));
    }

    private static LedgerRow.Status statusOf(List<LedgerRow> rows, long plugin) {
        return rows.stream().filter(row -> row.plugin() == plugin).findFirst().get().status();
    }

    private static Outcome outcomeOf(Activation activation, long plugin) {
        return done(activation.outcomes().get(activation.plan().order().indexOf(plugin)));
    }

    @Test
    void anActivateThrowingWithANulInItsMessageIsQuarantinedWithTheNulEscapedInItsReason() {
        TestEntry nul = fixture.entry("nul");
        nul.onActivate = context -> {
            value(context.register("nul:feed", new Object()));
            throw new RuntimeException("bad\u0000reason");
        };
        long plugin = fixture.register(services("nul", Collections.singletonList("nul:feed"),
                Collections.<String>emptyList()), nul);

        Outcome outcome = done(fixture.activate(plugin).outcomes().get(0));

        assertEquals(ActivationState.QUARANTINED, outcome.state());
        LedgerRow row = value(host.ledger()).get(0);
        assertEquals(LedgerRow.Status.QUARANTINED, row.status());
        assertTrue(row.reason().get().detail().contains("bad\\0reason"), row.reason().toString());
        assertTrue(fixture.failures.isEmpty(), fixture.failures.toString());
    }

    @Test
    void aVerifyTheCallerAsksForIsCarriedOutAsASettledActivatesIs() {
        TestEntry growing = fixture.entry("growing");
        long plugin = fixture.register(named("growing"), growing);
        fixture.activate(plugin);
        assertEquals(ActivationState.ACTIVE, value(host.verify(plugin)));
        value(growing.context.provide("screens", new Object()));

        assertEquals(ActivationState.QUARANTINED, value(host.verify(plugin)));
        assertEquals(RefusalStatus.REFUSED, assertInstanceOf(Answer.Refused.class,
                growing.context.register("growing:late", new Object())).status());
    }

    @Test
    void aVerifyOfAPluginNeverOpenedIsAnsweredAsTheCoreAnswersItAndQuarantinesNothing() {
        TestEntry unopened = fixture.entry("unopened");
        long plugin = fixture.register(named("unopened"), unopened);

        Answer<ActivationState> verified = host.verify(plugin);

        Refusal refusal = (Refusal) assertInstanceOf(Answer.Refused.class, verified).refusal().get();
        assertEquals(CoreConstants.REFUSAL_PLUGIN_STATE, refusal.code());
        assertEquals(ActivationState.ACTIVE, done(fixture.activate(plugin).outcomes().get(0)).state());
    }

    @Test
    void theLedgerShowsAPluginQuarantinedForARefusedVerificationWithTheCoresRefusal() {
        TestEntry growing = fixture.entry("growing");
        long plugin = fixture.register(named("growing"), growing);
        long bystander = fixture.register(named("bystander"), fixture.entry("bystander"));
        fixture.activate(plugin);
        value(growing.context.provide("screens", new Object()));
        value(host.verify(plugin));

        List<LedgerRow> rows = value(host.ledger());

        assertEquals(Arrays.asList(plugin, bystander), Arrays.asList(rows.get(0).plugin(), rows.get(1).plugin()));
        assertEquals(LedgerRow.Status.QUARANTINED, rows.get(0).status());
        assertEquals(LedgerRow.Status.REGISTERED, rows.get(1).status());
        Refusal reason = rows.get(0).reason().get();
        assertEquals(CoreConstants.REFUSAL_ACTIVATION_MISMATCH, reason.code());
        assertEquals(Optional.of("growing"), reason.subject());
        assertTrue(reason.hasFix());
    }

    @Test
    void theTimerObserverHearsEachMoveTheTimerMakesAsTheCoreAnnouncesIt() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        List<OptionalLong> moves = Collections.synchronizedList(new ArrayList<OptionalLong>());
        Host observed = value(Host.create(CoreProbes.binary(), HostOptions.defaults().withScheduler(scheduler)
                .withTimerObserver(moves::add).withErrorSink(fixture.failures::add)));
        TestEntry waiting = new TestEntry("waiting", new ArrayList<String>());
        long plugin = value(observed.register(consuming("waiting", "absent:service"), waiting));
        observed.activate(Collections.singletonList(plugin), EdgeSource.SERVICES);

        waiting.context.want("absent:service", WantLimits.timeout(1000));
        scheduler.moveTo(1000);

        assertEquals(Arrays.asList(OptionalLong.of(1000), OptionalLong.empty()), moves);
        observed.destroy().toCompletableFuture().get(10, TimeUnit.SECONDS);
        observed.close();
        assertTrue(fixture.failures.isEmpty());
    }

    @Test
    void aReleaseDeactivatesWhileWholeThenReleasesThenLetsItsEventsReachThePluginThenForgets() {
        TestEntry calendar = fixture.entry("calendar");
        Object events = new Object();
        AtomicReference<CompletableFuture<Answer<Object>>> pending = new AtomicReference<>();
        calendar.onActivate = context -> {
            value(context.register("calendar:events", events));
            pending.set(context.want("absent", WantLimits.unbounded()).settlement());
            return CompletableFuture.completedFuture(null);
        };
        calendar.onDeactivate = () -> {
            fixture.trace.add("deactivate got " + (value(calendar.context.get("calendar:events")) == events));
            fixture.trace.add("deactivate sees the want waiting " + !pending.get().isDone());
            return CompletableFuture.completedFuture(null);
        };
        TestEntry agenda = fixture.entry("agenda");
        long calendarPlugin = fixture.register(optionally("calendar", Collections.singletonList("calendar:events"),
                "absent"), calendar);
        long agendaPlugin = fixture.register(consuming("agenda", "calendar:events"), agenda);
        fixture.activate(calendarPlugin, agendaPlugin);
        pending.get().thenRun(() -> fixture.trace.add("want rejected, calendar still known "
                + host.manifest(calendarPlugin).isPresent()));

        assertTrue(value(done(host.release(calendarPlugin))));

        assertEquals(Arrays.asList("deactivate calendar", "deactivate got true", "deactivate sees the want waiting true",
                "want rejected, calendar still known true"), fixture.trace().subList(2, 6));
        Answer<Object> rejected = done(pending.get());
        assertEquals(CoreConstants.REFUSAL_STOPPED,
                ((Refusal) assertInstanceOf(Answer.Refused.class, rejected).refusal().get()).code());
        assertInstanceOf(Answer.Absent.class, agenda.context.get("calendar:events"));
        assertFalse(host.manifest(calendarPlugin).isPresent());
        assertInstanceOf(Answer.Detached.class, calendar.context.get("calendar:events"));
    }

    @Test
    void aReleaseInsideADispatchLeavesItsLastStepsBehindItsOwnEvents() {
        TestEntry watcher = fixture.entry("watcher");
        TestEntry released = fixture.entry("released");
        AtomicReference<CompletableFuture<Answer<Object>>> pending = new AtomicReference<>();
        released.onActivate = context -> {
            pending.set(context.want("absent", WantLimits.unbounded()).settlement());
            return CompletableFuture.completedFuture(null);
        };
        long releasedPlugin = fixture.register(consuming("released", "absent"), released);
        long watching = fixture.register(consuming("watcher", "signal"), watcher);
        fixture.activate(releasedPlugin, watching);
        AtomicReference<CompletableFuture<Answer<Boolean>>> release = new AtomicReference<>();
        value(watcher.context.watch("signal", new ServiceWatcher() {
            @Override
            public void registered(Object service) {
                release.set(host.release(releasedPlugin).toCompletableFuture());
                fixture.trace.add("inside the dispatch, done " + release.get().isDone()
                        + ", known " + host.manifest(releasedPlugin).isPresent()
                        + ", want settled " + pending.get().isDone());
            }

            @Override
            public void unregistered() {
            }
        }));
        pending.get().thenRun(() -> fixture.trace.add("want rejected, known "
                + host.manifest(releasedPlugin).isPresent()));

        value(host.registerHostService("signal", new Object()));

        assertEquals(Arrays.asList("deactivate released",
                "inside the dispatch, done false, known true, want settled false",
                "want rejected, known true"), fixture.trace().subList(2, 5));
        assertTrue(value(done(release.get())));
        assertFalse(host.manifest(releasedPlugin).isPresent());
    }

    @Test
    void aDeactivateFinishingLaterMovesTheCoreReleaseToWhenItFinishes() {
        TestEntry slow = fixture.entry("slow");
        CompletableFuture<Void> deactivated = new CompletableFuture<>();
        slow.onActivate = context -> {
            value(context.register("slow:service", new Object()));
            return CompletableFuture.completedFuture(null);
        };
        slow.onDeactivate = () -> deactivated;
        TestEntry other = fixture.entry("other");
        long plugin = fixture.register(named("slow"), slow);
        fixture.activate(plugin, fixture.register(consuming("other", "slow:service"), other));

        CompletableFuture<Answer<Boolean>> release = host.release(plugin).toCompletableFuture();

        assertFalse(release.isDone());
        assertInstanceOf(Answer.Ok.class, other.context.get("slow:service"));
        deactivated.complete(null);
        assertTrue(value(done(release)));
        assertInstanceOf(Answer.Absent.class, other.context.get("slow:service"));
    }

    @Test
    void aPluginReleasedWhileItsActivateIsInFlightIsAnsweredReleasedAndItsSettlingReachesNothing() {
        TestEntry hanging = fixture.entry("hanging");
        CompletableFuture<Void> activated = new CompletableFuture<>();
        hanging.onActivate = context -> activated;
        long plugin = fixture.register(named("hanging"), hanging);
        Activation activation = fixture.activate(plugin);

        CompletableFuture<Answer<Boolean>> release = host.release(plugin).toCompletableFuture();

        assertEquals(ActivationState.RELEASED, done(activation.outcomes().get(0)).state());
        assertTrue(value(done(release)));
        activated.complete(null);
        assertEquals(ActivationState.RELEASED, done(activation.outcomes().get(0)).state());
        assertEquals(Arrays.asList("activate hanging", "deactivate hanging"), fixture.trace());
        assertTrue(fixture.failures.isEmpty());
    }

    @Test
    void aReleaseOfAPluginNeverOpenedOrAlreadyForgottenAnswersThereWasNothingToRelease() {
        long registeredOnly = fixture.register(named("registered"), fixture.entry("registered"));
        long released = fixture.register(named("released"), fixture.entry("released"));
        fixture.activate(released);
        done(host.release(released));

        assertFalse(value(done(host.release(registeredOnly))));
        assertFalse(value(done(host.release(released))));
        assertFalse(value(done(host.release(12345L))));
        assertTrue(host.manifest(registeredOnly).isPresent());
        assertEquals(ActivationState.ACTIVE, done(fixture.activate(registeredOnly).outcomes().get(0)).state());
    }

    @Test
    void aDeactivateThatFailsIsReportedAndTheReleaseGoesOn() {
        TestEntry throwing = fixture.entry("throwing");
        throwing.onDeactivate = () -> {
            throw new IllegalStateException("broken");
        };
        TestEntry stageless = fixture.entry("stageless");
        stageless.onDeactivate = () -> null;
        TestEntry failing = fixture.entry("failing");
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("failed later"));
        failing.onDeactivate = () -> failed;
        List<Long> plugins = Arrays.asList(fixture.register(named("throwing"), throwing),
                fixture.register(named("stageless"), stageless), fixture.register(named("failing"), failing));
        value(host.activate(plugins, EdgeSource.SERVICES));

        for (long plugin : plugins) {
            assertTrue(value(done(host.release(plugin))));
            assertFalse(host.manifest(plugin).isPresent());
        }

        assertEquals(3, fixture.failuresOf(FailureKind.PLUGIN_DEACTIVATE).size());
    }

    @Test
    void anEntryWithoutADeactivateIsReleasedWithoutOne() {
        long plugin = fixture.register(named("plain"), TestEntry.activateOnly("plain", fixture.trace));
        fixture.activate(plugin);

        assertTrue(value(done(host.release(plugin))));

        assertEquals(Collections.singletonList("activate plain"), fixture.trace());
        assertTrue(fixture.failures.isEmpty());
    }

    private static ServiceWatcher recording(List<String> heard) {
        return new ServiceWatcher() {
            @Override
            public void registered(Object service) {
                heard.add("registered");
            }

            @Override
            public void unregistered() {
                heard.add("unregistered");
            }
        };
    }
}
