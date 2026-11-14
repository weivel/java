package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Dispatch on one thread: first in, first out, never re-entered, with failures going to the sink. */
class DispatchTest {
    private final TestCores.RecordingSink sink = new TestCores.RecordingSink();
    private final TestCores.RecordingListener listener = new TestCores.RecordingListener();

    @Test
    void aCallInsideAListenerDrainsItsOwnEventsWhichAreHeardAfterEveryEventDrainedBefore() {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "first")));
        ok(core.call(CoreCalls.watchService(HOST, "first")));
        ok(core.call(CoreCalls.watchService(HOST, "inner")));
        AtomicInteger depth = new AtomicInteger();
        AtomicInteger deepest = new AtomicInteger();
        AtomicBoolean calledInside = new AtomicBoolean();
        core.addListener(event -> {
            deepest.accumulateAndGet(depth.incrementAndGet(), Math::max);
            if (calledInside.compareAndSet(false, true)) {
                ok(core.call(CoreCalls.registerService(HOST, "inner")));
                assertEquals(1, listener.events().size(), "the inner event was heard inside the listener's call");
            }
            depth.decrementAndGet();
        });

        ok(core.call(CoreCalls.registerService(HOST, "first")));

        List<Long> sequences = listener.events().stream().map(CoreEvent::sequence).toList();
        assertEquals(3, sequences.size());
        assertEquals(sequences.stream().sorted().toList(), sequences);
        assertEquals(1, deepest.get());
        core.destroy();
    }

    @Test
    void aDeferredStepAtTheTopLevelRunsBeforeDeferReturns() {
        Core core = TestCores.core(listener, sink);
        AtomicBoolean ran = new AtomicBoolean();

        DeferredStep.Deferral deferral = CoreInternals.defer(core, () -> {
            ran.set(true);
            return DeferredStep.Status.DONE;
        });

        assertEquals(DeferredStep.Deferral.RAN_HERE, deferral);
        assertTrue(ran.get());
        core.destroy();
    }

    @Test
    void aStepDeferredWithACallJoinsTheQueueRightBehindThatCallsEventsInTheSameHold() {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "between")));
        List<String> order = new ArrayList<>();
        core.addListener(event -> order.add(event.getClass().getSimpleName()));
        AtomicBoolean first = new AtomicBoolean(true);
        CoreInternals.installDeadlineHooks(core, new DeadlineHooks() {
            @Override
            public long now() {
                return 0;
            }

            @Override
            public void follow(long at, boolean set) {
                if (first.getAndSet(false)) {
                    ok(core.call(CoreCalls.registerService(HOST, "between")));
                }
            }
        });

        ok(CoreInternals.callThenDefer(core, CoreCalls.wantService(HOST, "timed", WantOptions.timed(0, 100)),
                CallSteps.none(), () -> step(order, "step")));

        assertEquals(List.of("DeadlineEvent", "step", "WatchEvent"), order);
        core.destroy();
    }

    @Test
    void aStepDeferredWithARefusedCallIsNeverQueued() {
        Core core = TestCores.core(listener, sink);
        AtomicBoolean ran = new AtomicBoolean();

        Answer<Void> answer = CoreInternals.callThenDefer(core, CoreCalls.unregister(424242L), CallSteps.none(),
                () -> {
                    ran.set(true);
                    return DeferredStep.Status.DONE;
                });

        assertInstanceOf(Answer.Refused.class, answer);
        assertFalse(ran.get());
        core.destroy();
    }

    @Test
    void theDrainedCountIsHowManyEventsTheCoreHasHandedTheLayer() {
        Core core = TestCores.core(listener, sink);
        long before = CoreInternals.drainedCount(core);
        ok(core.call(CoreCalls.watchService(HOST, "counted")));
        ok(core.call(CoreCalls.watchService(HOST, "counted")));

        ok(core.call(CoreCalls.registerService(HOST, "counted")));

        assertEquals(before + 2, CoreInternals.drainedCount(core));
        assertEquals(CoreInternals.drainedCount(core), listener.events().getLast().sequence());
        core.destroy();
    }

    @Test
    void stepsDeferredInsideADispatchRunInQueueOrderBehindWhatWasQueuedBeforeThem() {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "outer")));
        ok(core.call(CoreCalls.watchService(HOST, "between")));
        List<String> order = new ArrayList<>();
        List<DeferredStep.Deferral> deferrals = new ArrayList<>();
        AtomicBoolean first = new AtomicBoolean(true);
        core.addListener(event -> {
            order.add("event " + event.sequence());
            if (first.getAndSet(false)) {
                deferrals.add(CoreInternals.defer(core, () -> step(order, "step one")));
                ok(core.call(CoreCalls.registerService(HOST, "between")));
                deferrals.add(CoreInternals.defer(core, () -> step(order, "step two")));
            }
        });

        ok(core.call(CoreCalls.registerService(HOST, "outer")));

        assertEquals(List.of("event 1", "step one", "event 2", "step two"), order);
        assertEquals(List.of(DeferredStep.Deferral.LEFT_TO_THE_DISPATCH, DeferredStep.Deferral.LEFT_TO_THE_DISPATCH),
                deferrals);
        core.destroy();
    }

    @Test
    void aListenerThatThrowsGoesToTheSinkAndEveryLaterListenerAndEventIsStillHeard() {
        Core core = TestCores.core(event -> {
            throw new IllegalStateException("a listener failing on purpose");
        }, sink);
        core.addListener(listener);
        ok(core.call(CoreCalls.watchService(HOST, "failing")));
        ok(core.call(CoreCalls.watchService(HOST, "failing")));

        ok(core.call(CoreCalls.registerService(HOST, "failing")));

        assertEquals(2, listener.events().size());
        List<Failure> told = sink.of(FailureKind.LISTENER);
        assertEquals(2, told.size());
        assertEquals(listener.events().getFirst().sequence(), told.getFirst().sequence());
        assertInstanceOf(IllegalStateException.class, told.getFirst().cause().orElseThrow());
        core.destroy();
    }

    @Test
    void aStepThatFailsGoesToTheSinkAndTheEntriesBehindItStillRun() {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "trigger")));
        ok(core.call(CoreCalls.watchService(HOST, "behind")));
        AtomicBoolean first = new AtomicBoolean(true);
        core.addListener(event -> {
            if (first.getAndSet(false)) {
                CoreInternals.defer(core, () -> DeferredStep.Status.FAILED);
                CoreInternals.defer(core, () -> {
                    throw new IllegalStateException("a step failing on purpose");
                });
                ok(core.call(CoreCalls.registerService(HOST, "behind")));
            }
        });

        ok(core.call(CoreCalls.registerService(HOST, "trigger")));

        assertEquals(2, sink.of(FailureKind.STEP).size());
        assertEquals(2, listener.of(CoreEvent.WatchEvent.class).size());
        core.destroy();
    }

    @Test
    void aSinkThatThrowsIsWrittenToStandardError() {
        Core core = TestCores.core(event -> {
            throw new IllegalStateException("a listener failing on purpose");
        }, failure -> {
            throw new IllegalStateException("a sink failing on purpose");
        });
        ok(core.call(CoreCalls.watchService(HOST, "unheard")));
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        PrintStream standardError = System.err;
        System.setErr(new PrintStream(written, true, StandardCharsets.UTF_8));
        try {
            ok(core.call(CoreCalls.registerService(HOST, "unheard")));
        } finally {
            System.setErr(standardError);
        }

        String text = written.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("a sink failing on purpose"), text);
        assertTrue(text.contains("a listener threw while it heard an event"), text);
        core.destroy();
    }

    @Test
    void aVirtualMachineErrorIsRethrownOnlyOnceTheDispatcherIsFreeAgain() {
        AtomicBoolean overflow = new AtomicBoolean(true);
        Core core = TestCores.core(event -> {
            if (overflow.getAndSet(false)) {
                throw new StackOverflowError("an overflow on purpose");
            }
        }, sink);
        core.addListener(listener);
        ok(core.call(CoreCalls.watchService(HOST, "overflowing")));
        ok(core.call(CoreCalls.watchService(HOST, "overflowing")));

        assertThrows(StackOverflowError.class, () -> core.call(CoreCalls.registerService(HOST, "overflowing")));
        assertEquals(List.of(), listener.events());
        ok(core.call(CoreCalls.getService(HOST, "overflowing")));

        assertEquals(1, listener.events().size());
        assertSame(Thread.currentThread(), listener.heard().getFirst().thread());
        core.destroy();
    }

    @Test
    void theAnsweredStepRunsUnderTheLockBeforeAnyEventOfItsCallIsDispatched() {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "recorded")));
        AtomicLong recorded = new AtomicLong();
        List<Boolean> heardAfterRecording = new ArrayList<>();
        core.addListener(event -> heardAfterRecording.add(
                event instanceof CoreEvent.WatchEvent watch && watch.implementation() == recorded.get()));

        Answer<Long> answer = CoreInternals.call(core, CoreCalls.registerService(HOST, "recorded"),
                new CallSteps<>(Optional::empty, registration -> {
                    assertTrue(core.isLockHeld());
                    recorded.set(registration);
                    return new Answer.Ok<>(registration);
                }));

        assertEquals(recorded.get(), ok(answer));
        assertEquals(List.of(true), heardAfterRecording);
        core.destroy();
    }

    @Test
    void aGuardAnsweringCancelsTheCallBeforeItReachesTheCore() {
        TestCores.CallLog log = new TestCores.CallLog();
        Core core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        log.clear();

        Answer<Long> answer = CoreInternals.call(core, CoreCalls.registerService(HOST, "guarded"),
                new CallSteps<>(() -> {
                    assertTrue(core.isLockHeld());
                    return Optional.of(new Answer.Detached<>());
                }, Answer.Ok::new));

        assertEquals(new Answer.Detached<Long>(), answer);
        assertEquals(List.of(), log.calls());
        core.destroy();
    }

    @Test
    void takingTheLayerLockAgainOnTheThreadHoldingItIsADefectOfTheLayer() {
        Core core = TestCores.core(listener, sink);

        Answer<Long> answer = CoreInternals.call(core, CoreCalls.registerService(HOST, "again"),
                new CallSteps<>(Optional::empty, registration -> core.call(CoreCalls.getService(HOST, "again"))));

        assertInstanceOf(Answer.Defect.class, answer);
        Failure defect = sink.of(FailureKind.LAYER_DEFECT).getFirst();
        assertTrue(defect.cause().orElseThrow().getMessage().contains("taken again"), defect.toString());
        assertFalse(core.isLockHeld());
        core.destroy();
    }

    @Test
    void aReportMadeUnderTheLayerLockIsADefectAndReachesNoSinkThere() {
        List<Boolean> lockSeen = new ArrayList<>();
        Core[] holder = new Core[1];
        Core core = TestCores.core(listener, failure -> {
            lockSeen.add(holder[0].isLockHeld());
            sink.report(failure);
        });
        holder[0] = core;
        Failure underTheLock = new Failure(FailureKind.STEP, "reported under the lock", 0, 0, Optional.empty());

        Answer<Long> answer = CoreInternals.call(core, CoreCalls.registerService(HOST, "reporting"),
                new CallSteps<>(() -> {
                    CoreInternals.report(core, underTheLock);
                    return Optional.empty();
                }, Answer.Ok::new));

        assertInstanceOf(Answer.Defect.class, answer);
        assertEquals(List.of(), sink.of(FailureKind.STEP));
        Failure defect = sink.of(FailureKind.LAYER_DEFECT).getFirst();
        assertTrue(defect.cause().orElseThrow().getMessage().contains("under the layer lock"), defect.toString());
        assertEquals(List.of(false), lockSeen);
        core.destroy();
    }

    @Test
    void aDestroyMadeByAListenerEndsTheRunningDispatchAtOnce() {
        Core[] holder = new Core[1];
        Core core = TestCores.core(event -> holder[0].destroy(), sink);
        holder[0] = core;
        core.addListener(listener);
        ok(core.call(CoreCalls.watchService(HOST, "ended")));

        ok(core.call(CoreCalls.registerService(HOST, "ended")));

        assertTrue(core.isDestroyed());
        assertEquals(List.of(), listener.events());
        core.close();
    }

    @Test
    void aDestroyedCoreAnswersEveryCallDestroyedAndDropsWhatItHadNotDispatched() {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "dropped")));
        ok(core.call(CoreCalls.watchService(HOST, "dropped")));
        AtomicBoolean stepRan = new AtomicBoolean();
        core.addListener(event -> {
            CoreInternals.defer(core, () -> {
                stepRan.set(true);
                return DeferredStep.Status.DONE;
            });
            core.destroy();
        });

        ok(core.call(CoreCalls.registerService(HOST, "dropped")));

        assertEquals(1, listener.events().size());
        assertFalse(stepRan.get());
        assertTrue(core.isDestroyed());
        assertEquals(new Answer.Destroyed<Long>(), core.call(CoreCalls.getService(HOST, "dropped")));
        assertEquals(DeferredStep.Deferral.DESTROYED, CoreInternals.defer(core, () -> DeferredStep.Status.DONE));
        core.close();
    }

    @Test
    void closingRefusesWhileTheCoreIsNotDestroyedOrADispatchStillRuns() {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "closing")));
        List<Throwable> closing = new ArrayList<>();
        core.addListener(event -> {
            core.destroy();
            closing.add(assertThrows(IllegalStateException.class, core::close));
        });
        assertThrows(IllegalStateException.class, core::close);

        ok(core.call(CoreCalls.registerService(HOST, "closing")));

        assertEquals(1, closing.size());
        core.close();
    }

    private static DeferredStep.Status step(List<String> order, String name) {
        order.add(name);
        return DeferredStep.Status.DONE;
    }
}
