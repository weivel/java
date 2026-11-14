package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;
import org.junit.jupiter.api.Test;

/** Each drained event copied into Java values and released before the next, whatever the copy does. */
class EventCopyTest {
    private final TestCores.RecordingSink sink = new TestCores.RecordingSink();
    private final TestCores.RecordingListener listener = new TestCores.RecordingListener();

    @Test
    void aCapacityQuestionsConstraintsAreCopiedSteppedAtTheCoresStride() {
        Core core = constrainedCore(EventCopy.INSTANCE);
        long consumer = constrainedConsumer(core);
        ok(core.call(CoreCalls.registerService(HOST, "measured")));

        long want = ok(core.call(CoreCalls.wantService(consumer, "measured", polled())));

        CoreEvent.CapacityWantedEvent question = listener.of(CoreEvent.CapacityWantedEvent.class).getFirst();
        assertEquals(want, question.want());
        assertEquals(consumer, question.plugin());
        assertEquals(List.of(new NamedNumber("latency", 10.0)), question.constraints());
        assertFalse(question.incomplete());
        core.destroy();
    }

    @Test
    void anEventWhoseTextsCannotBeCopiedIsDeliveredIncompleteWithEveryNumberExact() {
        Core core = TestCores.core(TestCores.functions(), new FailingCopy(sequence -> true, sequence -> false),
                listener, sink);
        long want = ok(core.call(CoreCalls.wantService(HOST, "awaited", WantOptions.timed(0, 10))));
        sink.failures().forEach(failure -> assertEquals(FailureKind.EVENT_INCOMPLETE, failure.kind()));
        int toldBefore = sink.failures().size();

        ok(core.call(CoreCalls.advance(10)));

        CoreEvent.WantSettledEvent settled = listener.of(CoreEvent.WantSettledEvent.class).getFirst();
        assertTrue(settled.incomplete());
        assertEquals(want, settled.want());
        assertEquals(HOST, settled.plugin());
        assertEquals(0, settled.implementation());
        Refusal refusal = settled.refusal().orElseThrow();
        assertEquals(CoreConstants.REFUSAL_TIMED_OUT, refusal.code());
        assertEquals(CoreEvent.TEXT_NOT_COPIED, refusal.detail());
        assertEquals(CoreEvent.TEXT_NOT_COPIED, refusal.fix());
        assertEquals(Optional.of(CoreEvent.TEXT_NOT_COPIED), refusal.subject());
        assertTrue(refusal.hasFix());
        List<Failure> told = sink.failures().subList(toldBefore, sink.failures().size());
        assertEquals(1, told.stream().filter(failure -> failure.sequence() == settled.sequence()).count());
        assertEquals(FailureKind.EVENT_INCOMPLETE, told.getFirst().kind());
        core.destroy();
    }

    @Test
    void anIncompleteRefusalIsStillTheAnswerOfTheCallItRefused() {
        Core core = TestCores.core(TestCores.functions(), new FailingCopy(sequence -> true, sequence -> false),
                listener, sink);

        Refusal refusal = TestCores.refusalOf(core.call(CoreCalls.unregister(424242L)));

        assertEquals(CoreConstants.REFUSAL_STALE_HANDLE, refusal.code());
        assertEquals(CoreEvent.TEXT_NOT_COPIED, refusal.detail());
        assertEquals(Optional.empty(), refusal.subject());
        assertEquals(1, sink.of(FailureKind.EVENT_INCOMPLETE).size());
        core.destroy();
    }

    @Test
    void anIncompleteCapacityQuestionCarriesNoConstraints() {
        Core core = constrainedCore(new FailingCopy(sequence -> true, sequence -> false));
        long consumer = constrainedConsumer(core);
        ok(core.call(CoreCalls.registerService(HOST, "measured")));

        ok(core.call(CoreCalls.wantService(consumer, "measured", polled())));

        CoreEvent.CapacityWantedEvent question = listener.of(CoreEvent.CapacityWantedEvent.class).getFirst();
        assertTrue(question.incomplete());
        assertEquals(List.of(), question.constraints());
        core.destroy();
    }

    @Test
    void anEventNotEvenHeldInPartIsDroppedReportedAndTheRestAreStillDrainedAndReleased() {
        TestCores.CallLog log = new TestCores.CallLog();
        FailingCopy failing = new FailingCopy(sequence -> false, sequence -> false);
        Core core = TestCores.core(log.around(TestCores.functions()), failing, listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "dropped")));
        ok(core.call(CoreCalls.watchService(HOST, "dropped")));
        failing.failFirstOfNextDrain();
        log.clear();

        ok(core.call(CoreCalls.registerService(HOST, "dropped")));

        assertEquals(1, listener.of(CoreEvent.WatchEvent.class).size());
        assertEquals(2, log.count(CoreFunction.EVENT_RELEASE));
        List<Failure> dropped = sink.of(FailureKind.EVENT_DROPPED);
        assertEquals(1, dropped.size());
        assertEquals(CoreConstants.EVENT_WATCH, dropped.getFirst().eventKind());
        assertEquals(1, sink.failures().size());
        core.destroy();
    }

    @Test
    void anEventOfAKindTheLayerDoesNotKnowIsReleasedAndIgnoredWithoutAReport() {
        TestCores.CallLog log = new TestCores.CallLog();
        CoreFunctions renamed = log.around(TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.NEXT_EVENT ? UnknownWatches.around(handle) : handle));
        Core core = TestCores.core(renamed, EventCopy.INSTANCE, listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "unknown")));
        log.clear();

        ok(core.call(CoreCalls.registerService(HOST, "unknown")));

        assertEquals(List.of(), listener.events());
        assertEquals(1, log.count(CoreFunction.EVENT_RELEASE));
        assertEquals(List.of(), sink.failures());
        core.destroy();
    }

    private Core constrainedCore(EventCopying copying) {
        CoreOptions vocabulary = new CoreOptions(0, 0, 0, List.of(), List.of(),
                List.of(new CoreOptions.ConstraintEntry("latency", "latency", 2)));
        return TestCores.created(TestCores.functions(), copying, vocabulary, listener, sink);
    }

    private static long constrainedConsumer(Core core) {
        Manifest.ServiceWant measured = new Manifest.ServiceWant("measured", Optional.empty(), Choice.YES, Choice.NO,
                List.of(new NamedNumber("latency", 10.0)));
        Manifest manifest = new Manifest("consumer", 0, Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                List.of(measured), List.of(), Optional.empty());
        return WrapperCallTest.openedPlugin(core, manifest);
    }

    private static WantOptions polled() {
        return new WantOptions(Choice.DEFAULT, 0, 0, Choice.DEFAULT, 0, 100);
    }

    /** A copy failing whole, or in its fixed part too, for the sequence numbers it is told. */
    static final class FailingCopy implements EventCopying {
        private final LongPredicate failsWhole;
        private final LongPredicate failsFixedPart;
        private long failingSequence = -1;

        FailingCopy(LongPredicate failsWhole, LongPredicate failsFixedPart) {
            this.failsWhole = failsWhole;
            this.failsFixedPart = failsFixedPart;
        }

        void failFirstOfNextDrain() {
            failingSequence = -2;
        }

        @Override
        public CoreEvent whole(MemorySegment event, long sequence) {
            if (failingSequence == -2) {
                failingSequence = sequence;
            }
            if (failsWhole.test(sequence) || sequence == failingSequence) {
                throw new IllegalStateException("a copy failed on purpose");
            }
            return EventCopy.INSTANCE.whole(event, sequence);
        }

        @Override
        public CoreEvent fixedPart(MemorySegment event, long sequence) {
            if (failsFixedPart.test(sequence) || sequence == failingSequence) {
                throw new OutOfMemoryError("a copy failed on purpose");
            }
            return EventCopy.INSTANCE.fixedPart(event, sequence);
        }
    }

    /** A {@code weivel_next_event} that hands every watch event over as a kind the layer does not know. */
    static final class UnknownWatches {
        private static final int UNKNOWN_KIND = 99;
        private final MethodHandle original;

        private UnknownWatches(MethodHandle original) {
            this.original = original;
        }

        static MethodHandle around(MethodHandle original) {
            try {
                return MethodHandles.lookup().findVirtual(UnknownWatches.class, "call", MethodType.methodType(
                                int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class))
                        .bindTo(new UnknownWatches(original));
            } catch (ReflectiveOperationException unreachable) {
                throw new AssertionError(unreachable);
            }
        }

        int call(MemorySegment core, MemorySegment event, MemorySegment pending) throws Throwable {
            int status = (int) original.invokeExact(core, event, pending);
            if (status == 0 && event.get(JAVA_INT, 4) == CoreConstants.EVENT_WATCH) {
                event.set(JAVA_INT, 4, UNKNOWN_KIND);
            }
            return status;
        }
    }
}
