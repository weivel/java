package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
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

        CoreEvent.CapacityWantedEvent question = listener.of(CoreEvent.CapacityWantedEvent.class).get(0);
        assertEquals(want, question.want());
        assertEquals(consumer, question.plugin());
        assertEquals(Collections.singletonList(new NamedNumber("latency", 10.0)), question.constraints());
        assertFalse(question.incomplete());
        core.destroy();
    }

    @Test
    void constraintsLaidOutWiderThanTheLayerKnowsAreReadAtTheFirstElementsSize() {
        Core core = constrainedCore(EventCopy.INSTANCE, TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.NEXT_EVENT ? WiderNumbers.around(handle) : handle));
        long consumer = constrainedConsumer(core);
        ok(core.call(CoreCalls.registerService(HOST, "measured")));

        ok(core.call(CoreCalls.wantService(consumer, "measured", polled())));

        CoreEvent.CapacityWantedEvent question = listener.of(CoreEvent.CapacityWantedEvent.class).get(0);
        assertEquals(Arrays.asList(new NamedNumber("latency", 10.0), new NamedNumber("latency", 10.0)),
                question.constraints());
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

        CoreEvent.WantSettledEvent settled = listener.of(CoreEvent.WantSettledEvent.class).get(0);
        assertTrue(settled.incomplete());
        assertEquals(want, settled.want());
        assertEquals(HOST, settled.plugin());
        assertEquals(0, settled.implementation());
        Refusal refusal = settled.refusal().get();
        assertEquals(CoreConstants.REFUSAL_TIMED_OUT, refusal.code());
        assertEquals(CoreEvent.TEXT_NOT_COPIED, refusal.detail());
        assertEquals(CoreEvent.TEXT_NOT_COPIED, refusal.fix());
        assertEquals(Optional.of(CoreEvent.TEXT_NOT_COPIED), refusal.subject());
        assertTrue(refusal.hasFix());
        List<Failure> told = sink.failures().subList(toldBefore, sink.failures().size());
        assertEquals(1, told.stream().filter(failure -> failure.sequence() == settled.sequence()).count());
        assertEquals(FailureKind.EVENT_INCOMPLETE, told.get(0).kind());
        core.destroy();
    }

    @Test
    void aRejectedWantKeepsItsSubjectOnItsRefusalAndNamesNoUnmetConstraint() {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.wantService(HOST, "awaited", WantOptions.timed(0, 10))));

        ok(core.call(CoreCalls.advance(10)));

        CoreEvent.WantSettledEvent settled = listener.of(CoreEvent.WantSettledEvent.class).get(0);
        Refusal refusal = settled.refusal().get();
        assertEquals(CoreConstants.REFUSAL_TIMED_OUT, refusal.code());
        assertEquals(Optional.of("awaited"), refusal.subject());
        assertEquals(Optional.empty(), settled.unmetConstraint());
        assertFalse(settled.degraded());
        core.destroy();
    }

    @Test
    void anOutOfMemoryErrorInAWholeCopyFallsBackToAnIncompleteEvent() {
        Core core = TestCores.core(TestCores.functions(), new StarvedCopy(), listener, sink);

        Refusal refusal = TestCores.refusalOf(core.call(CoreCalls.unregister(424242L)));

        assertEquals(CoreConstants.REFUSAL_STALE_HANDLE, refusal.code());
        assertEquals(CoreEvent.TEXT_NOT_COPIED, refusal.detail());
        assertTrue(listener.of(CoreEvent.RefusalEvent.class).get(0).incomplete());
        assertEquals(1, sink.of(FailureKind.EVENT_INCOMPLETE).size());
        assertInstanceOf(OutOfMemoryError.class, sink.of(FailureKind.EVENT_INCOMPLETE).get(0).cause()
                .get());
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

        CoreEvent.CapacityWantedEvent question = listener.of(CoreEvent.CapacityWantedEvent.class).get(0);
        assertTrue(question.incomplete());
        assertEquals(Collections.emptyList(), question.constraints());
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
        assertEquals(CoreConstants.EVENT_WATCH, dropped.get(0).eventKind());
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

        assertEquals(Collections.emptyList(), listener.events());
        assertEquals(1, log.count(CoreFunction.EVENT_RELEASE));
        assertEquals(Collections.emptyList(), sink.failures());
        core.destroy();
    }

    private Core constrainedCore(EventCopying copying) {
        return constrainedCore(copying, TestCores.functions());
    }

    private Core constrainedCore(EventCopying copying, CoreFunctions functions) {
        CoreOptions vocabulary = new CoreOptions(0, 0, 0, Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(new CoreOptions.ConstraintEntry("latency", "latency", 2)));
        return TestCores.created(functions, copying, vocabulary, listener, sink);
    }

    private static long constrainedConsumer(Core core) {
        Manifest.ServiceWant measured = new Manifest.ServiceWant("measured", Optional.empty(), Choice.YES, Choice.NO,
                Collections.singletonList(new NamedNumber("latency", 10.0)));
        Manifest manifest = new Manifest("consumer", 0, Optional.empty(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(measured), Collections.emptyList(), Optional.empty());
        return WrapperCallTest.openedPlugin(core, manifest);
    }

    private static WantOptions polled() {
        return new WantOptions(Choice.DEFAULT, 0, 0, Choice.DEFAULT, 0, 100);
    }

    /** A copy whose every whole copy runs out of memory, as a copy of a large text could. */
    static final class StarvedCopy implements EventCopying {
        @Override
        public CoreEvent whole(NativeRegion event, long sequence) {
            throw new OutOfMemoryError("a copy ran out of memory on purpose");
        }

        @Override
        public CoreEvent fixedPart(NativeRegion event, long sequence) {
            return EventCopy.INSTANCE.fixedPart(event, sequence);
        }
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
        public CoreEvent whole(NativeRegion event, long sequence) {
            if (failingSequence == -2) {
                failingSequence = sequence;
            }
            if (failsWhole.test(sequence) || sequence == failingSequence) {
                throw new IllegalStateException("a copy failed on purpose");
            }
            return EventCopy.INSTANCE.whole(event, sequence);
        }

        @Override
        public CoreEvent fixedPart(NativeRegion event, long sequence) {
            if (failsFixedPart.test(sequence) || sequence == failingSequence) {
                throw new OutOfMemoryError("a copy failed on purpose");
            }
            return EventCopy.INSTANCE.fixedPart(event, sequence);
        }
    }

    /**
     * A {@code weivel_next_event} that hands a capacity question's constraints over as a newer core would: each element
     * twice as wide as the layer's, and the one constraint listed twice. Read at the layer's own stride, the second
     * element would be the first one's tail, which names {@link #MISREAD} at 99.
     */
    static final class WiderNumbers {
        static final String MISREAD = "misread";
        private static final long WIDER = CoreLayouts.NUMBER.byteSize() * 2;
        private static final long CONSTRAINTS = CoreLayouts.offsetOf(CoreLayouts.EVENT, "data")
                + CoreLayouts.offsetOf(CoreLayouts.CAPACITY_EVENT, "constraints");

        private WiderNumbers() {
        }

        static NativeFunction around(NativeFunction original) {
            return arguments -> {
                long status = original.call(arguments);
                NativeRegion event = eventAt(arguments[1]);
                if (status == 0 && event.getInt(4) == CoreConstants.EVENT_CAPACITY_WANTED) {
                    widen(event);
                }
                return status;
            };
        }

        private static void widen(NativeRegion event) {
            NativeAccess access = NativeBackends.current();
            NativeRegion first = event.at(event.getAddress(CONSTRAINTS), CoreLayouts.NUMBER.byteSize());
            NativeRegion wider = event.at(access.allocate(WIDER * 2), WIDER * 2);
            for (int index = 0; index < 2; index++) {
                NativeRegion element = wider.slice(index * WIDER, WIDER);
                element.putInt(0, (int) WIDER);
                element.putAddress(8, first.getAddress(8));
                element.putDouble(16, first.getDouble(16));
                element.putLong(24, -1L);
                element.putAddress(32, globalText(access, MISREAD));
                element.putDouble(40, 99.0);
            }
            event.putAddress(CONSTRAINTS, wider.address());
            event.putInt(CONSTRAINTS + 8, 2);
        }

        private static long globalText(NativeAccess access, String text) {
            byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
            long address = access.allocate(encoded.length + 1L);
            access.writeBytes(address, 0, encoded);
            return address;
        }
    }

    /** A {@code weivel_next_event} that hands every watch event over as a kind the layer does not know. */
    static final class UnknownWatches {
        private static final int UNKNOWN_KIND = 99;

        private UnknownWatches() {
        }

        static NativeFunction around(NativeFunction original) {
            return arguments -> {
                long status = original.call(arguments);
                NativeRegion event = eventAt(arguments[1]);
                if (status == 0 && event.getInt(4) == CoreConstants.EVENT_WATCH) {
                    event.putInt(4, UNKNOWN_KIND);
                }
                return status;
            };
        }
    }

    private static NativeRegion eventAt(long address) {
        return new NativeRegion(NativeBackends.current(), address, CoreLayouts.EVENT.byteSize(), null);
    }
}
