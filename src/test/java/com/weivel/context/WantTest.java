package com.weivel.context;

import static com.weivel.context.ContextFixture.consuming;
import static com.weivel.context.ContextFixture.providing;
import static com.weivel.context.ContextFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.Choice;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreOptions;
import com.weivel.core.CoreProbes;
import com.weivel.core.Refusal;
import com.weivel.core.WantOptions;
import com.weivel.timer.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * A plugin's wants: each settles once, from a dispatch step, as the core settles it, its limits handed over
 * unchanged.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class WantTest {
    private static final String GREETER = "provider:greeter";

    @Test
    void aWantSettledAtOnceAnswersTheProvidersOwnObject() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            Object greeter = new Object();
            value(provider.register(GREETER, greeter));

            CompletableFuture<Answer<Object>> settlement = consumer.want(GREETER, WantLimits.unbounded()).settlement();

            assertSame(greeter, value(settlement.join()));
        }
    }

    @Test
    void aWantSettlesWhenTheServiceArrivesWithTheProvidersOwnObject() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            CompletableFuture<Answer<Object>> settlement = consumer.want(GREETER, WantLimits.unbounded()).settlement();
            assertFalse(settlement.isDone());
            Object greeter = new Object();

            value(provider.register(GREETER, greeter));

            assertSame(greeter, value(settlement.join()));
        }
    }

    @Test
    void aWantIsCompletedByTheDispatchNeverInsideTheCallThatMadeIt() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            value(provider.register(GREETER, new Object()));
            List<Boolean> doneWhenMade = new ArrayList<>();
            List<CompletableFuture<Answer<Object>>> made = new ArrayList<>();
            AtomicBoolean first = new AtomicBoolean(true);
            fixture.core.addListener(event -> {
                if (first.getAndSet(false)) {
                    CompletableFuture<Answer<Object>> settlement = consumer.want(GREETER, WantLimits.unbounded())
                            .settlement();
                    doneWhenMade.add(settlement.isDone());
                    made.add(settlement);
                }
            });

            fixture.core.call(CoreCalls.unregister(424242L));

            assertEquals(List.of(false), doneWhenMade);
            assertInstanceOf(Answer.Ok.class, made.getFirst().join());
        }
    }

    @Test
    void aWantTheCoreNeverMadeIsStillSettledByTheDispatchNotInsideTheCall() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext unopened = fixture.registered(consuming("unopened", GREETER));
            List<Boolean> doneWhenMade = new ArrayList<>();
            List<CompletableFuture<Answer<Object>>> made = new ArrayList<>();
            AtomicBoolean first = new AtomicBoolean(true);
            fixture.core.addListener(event -> {
                if (first.getAndSet(false)) {
                    CompletableFuture<Answer<Object>> settlement = unopened.want(GREETER, WantLimits.unbounded())
                            .settlement();
                    doneWhenMade.add(settlement.isDone());
                    made.add(settlement);
                }
            });

            fixture.core.call(CoreCalls.unregister(424242L));

            assertEquals(List.of(false), doneWhenMade);
            assertInstanceOf(Answer.Refused.class, made.getFirst().join());
        }
    }

    @Test
    void aWantRejectedAtOnceCarriesTheCoresRefusalIntact() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));

            Refusal refusal = refusalOf(consumer.want("undeclared", WantLimits.unbounded()).settlement());

            assertEquals(CoreConstants.REFUSAL_UNDECLARED, refusal.code());
            assertEquals(Optional.of("undeclared"), refusal.subject());
            assertTrue(refusal.hasFix());
            assertFalse(refusal.fix().isEmpty());
        }
    }

    @Test
    void aTimedWantHandsTheCoreItsTimeoutWithTheTimersTimeAndTimesOut() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            fixture.scheduler.setClock(1000);

            CompletableFuture<Answer<Object>> settlement = consumer.want(GREETER, WantLimits.timeout(100))
                    .settlement();
            fixture.scheduler.moveTo(1100);

            assertEquals(List.of(new WantOptions(Choice.YES, 1000, 100, Choice.DEFAULT, 0, 0)),
                    fixture.crossings.wantOptions());
            assertEquals(CoreConstants.REFUSAL_TIMED_OUT, refusalOf(settlement).code());
        }
    }

    @Test
    void aConstrainedWantHandsTheCoreItsBudgetAndPollUnchanged() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY)) {
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", GREETER, 10, Choice.NO));
            fixture.scheduler.setClock(40);

            consumer.want(GREETER, WantLimits.budgeted(500, 100));

            assertEquals(List.of(new WantOptions(Choice.DEFAULT, 40, 0, Choice.YES, 500, 100)),
                    fixture.crossings.wantOptions());
        }
    }

    @Test
    void anUnboundedWantArmsNoDeadline() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));

            consumer.want(GREETER, WantLimits.unbounded());

            assertEquals(List.of(new WantOptions(Choice.DEFAULT, 0, 0, Choice.DEFAULT, 0, 0)),
                    fixture.crossings.wantOptions());
            assertEquals(new Answer.Absent<Long>(), fixture.core.call(CoreCalls.nextDeadline()));
        }
    }

    @Test
    void aTimedOrConstrainedWantWithNoClockIsTheCallersMistakeAndReachesNoCore() {
        try (ContextFixture fixture = new ContextFixture(CoreOptions.defaults(), false, Scheduler.none())) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));

            Answer<Object> timed = consumer.want(GREETER, WantLimits.timeout(100)).settlement().join();
            Answer<Object> polled = consumer.want(GREETER, WantLimits.polled(100)).settlement().join();

            assertInstanceOf(Answer.Mistake.class, timed);
            assertInstanceOf(Answer.Mistake.class, polled);
            assertEquals(0, fixture.crossings.count("weivel_want_service"));
            assertEquals(List.of(), fixture.failures);
        }
    }

    @Test
    void aWantThePluginStillWaitsOnWhenReleasedIsRejectedStoppedToIt() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            CompletableFuture<Answer<Object>> settlement = consumer.want(GREETER, WantLimits.unbounded()).settlement();

            fixture.release(consumer);

            assertEquals(CoreConstants.REFUSAL_STOPPED, refusalOf(settlement).code());
        }
    }

    @Test
    void aWantMadeAfterThePluginsEntriesEndedStillSettlesStopped() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            fixture.release(consumer);

            CompletableFuture<Answer<Object>> late = consumer.want(GREETER, WantLimits.unbounded()).settlement();

            assertEquals(CoreConstants.REFUSAL_STOPPED, refusalOf(late).code());
        }
    }

    @Test
    void aWithdrawnWantSettlesOnceAsAnAbsenceAndNeverAgain() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            PendingWant want = consumer.want(GREETER, WantLimits.unbounded());
            AtomicInteger completions = new AtomicInteger();
            want.settlement().thenRun(completions::incrementAndGet);

            assertInstanceOf(Answer.Ok.class, want.withdraw());
            value(provider.register(GREETER, new Object()));

            assertEquals(new Answer.Absent<Object>(), want.settlement().join());
            assertEquals(1, completions.get());
            assertInstanceOf(Answer.Refused.class, want.withdraw());
        }
    }

    @Test
    void aSettlementIsCompletedOutsideEveryLock() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", GREETER));
            PluginContext consumer = fixture.opened(consuming("consumer", GREETER));
            List<Boolean> locked = new ArrayList<>();
            consumer.want(GREETER, WantLimits.unbounded()).settlement().thenRun(() -> locked.add(
                    CoreProbes.isLockHeldByCurrentThread(fixture.core)
                            || com.weivel.handles.HandlesProbes.isLockHeldByCurrentThread(fixture.handles)));

            value(provider.register(GREETER, new Object()));

            assertEquals(List.of(false), locked);
        }
    }

    static Refusal refusalOf(CompletableFuture<Answer<Object>> settlement) {
        Answer.Refused<?> refused = assertInstanceOf(Answer.Refused.class, settlement.join());
        return refused.refusal().orElseThrow();
    }
}
