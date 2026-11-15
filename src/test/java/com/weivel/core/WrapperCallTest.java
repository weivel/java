package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The wrapper's creation, drain and answers, one test per sentence of the contract's {@code core} section. */
class WrapperCallTest {
    private final TestCores.RecordingSink sink = new TestCores.RecordingSink();
    private final TestCores.RecordingListener listener = new TestCores.RecordingListener();

    @Test
    void theCreationIsDrainedAndHeardByTheListenerGivenWithIt() {
        CoreOptions debug = new CoreOptions(0, CoreConstants.LOG_DEBUG, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        Core core = TestCores.created(TestCores.functions(), EventCopy.INSTANCE, debug, listener, sink);

        CoreEvent.LogEvent created = assertInstanceOf(CoreEvent.LogEvent.class, listener.events().get(0));
        assertEquals("core created", created.text());
        assertEquals(1, created.sequence());
        core.destroy();
    }

    @Test
    void aCreationThatThrowsAfterTheCoreAnsweredDestroysThatCoreAndClosesItsArena() {
        CoreOptions debug = new CoreOptions(0, CoreConstants.LOG_DEBUG, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        TestCores.CallLog log = new TestCores.CallLog();
        NativeRegion[] seen = new NativeRegion[1];
        EventCopying overflowing = new EventCopying() {
            @Override
            public CoreEvent whole(NativeRegion event, long sequence) {
                seen[0] = event;
                throw new StackOverflowError("a copy overflowing on purpose");
            }

            @Override
            public CoreEvent fixedPart(NativeRegion event, long sequence) {
                return EventCopy.INSTANCE.fixedPart(event, sequence);
            }
        };

        assertThrows(StackOverflowError.class, () -> Wrapper.start(log.around(TestCores.functions()), overflowing,
                debug, Optional.of(listener), sink));

        assertEquals(1, log.count(CoreFunction.CORE_CREATE));
        assertEquals(1, log.count(CoreFunction.CORE_DESTROY));
        assertFalse(seen[0].isAlive());
    }

    @Test
    void optionsTheCoreCallsMalformedAreTheCallersMistakeAndNoSinkHearsOfThem() {
        CoreOptions tooLarge = new CoreOptions(70000, 0, 0, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());

        Answer<Core> created = Core.wrapped(Wrapper.start(TestCores.functions(), EventCopy.INSTANCE, tooLarge,
                Optional.of(listener), sink));

        Answer.Refused<Core> refused = assertInstanceOf(Answer.Refused.class, created);
        assertEquals(RefusalStatus.MALFORMED, refused.status());
        assertEquals(Optional.empty(), refused.refusal());
        assertEquals(Collections.emptyList(), sink.failures());
    }

    @Test
    void aCallReportingNoEventWaitingMakesNoFurtherCrossing() {
        TestCores.CallLog log = new TestCores.CallLog();
        Core core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        log.clear();

        ok(core.call(CoreCalls.registerService(HOST, "quiet")));

        assertEquals(Collections.singletonList(CoreFunction.REGISTER_SERVICE), log.calls());
        core.destroy();
    }

    @Test
    void eachEventIsReleasedBeforeTheNextIsDrainedAndTheDrainStopsAtTheCountTheCoreReports() {
        TestCores.CallLog log = new TestCores.CallLog();
        Core core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "watched")));
        ok(core.call(CoreCalls.watchService(HOST, "watched")));
        log.clear();

        ok(core.call(CoreCalls.registerService(HOST, "watched")));

        assertEquals(Arrays.asList(CoreFunction.REGISTER_SERVICE, CoreFunction.NEXT_EVENT, CoreFunction.EVENT_RELEASE,
                CoreFunction.NEXT_EVENT, CoreFunction.EVENT_RELEASE), log.calls());
        assertEquals(2, listener.of(CoreEvent.WatchEvent.class).size());
        core.destroy();
    }

    @Test
    void eachEventIsCopiedIntoJavaValuesExactly() {
        Core core = TestCores.core(listener, sink);
        long watch = ok(core.call(CoreCalls.watchService(HOST, "copied")));

        long registration = ok(core.call(CoreCalls.registerService(HOST, "copied")));

        CoreEvent.WatchEvent heard = listener.of(CoreEvent.WatchEvent.class).get(0);
        assertEquals(watch, heard.watch());
        assertEquals(HOST, heard.plugin());
        assertEquals(registration, heard.implementation());
        assertEquals(CoreConstants.CHANGE_REGISTERED, heard.change());
        assertFalse(heard.incomplete());
        core.destroy();
    }

    @Test
    void aRefusedCallIsAnsweredWithTheRefusalTheCoreQueuedForIt() {
        Core core = TestCores.core(listener, sink);

        Answer<Void> answer = core.call(CoreCalls.unregister(424242L));

        Answer.Refused<Void> refused = assertInstanceOf(Answer.Refused.class, answer);
        assertEquals(RefusalStatus.REFUSED, refused.status());
        Refusal refusal = refused.refusal().get();
        assertEquals(CoreConstants.REFUSAL_STALE_HANDLE, refusal.code());
        assertFalse(refusal.detail().isEmpty());
        assertEquals(refusal, listener.of(CoreEvent.RefusalEvent.class).get(0).refusal());
        assertEquals(Collections.emptyList(), sink.failures());
        core.destroy();
    }

    @Test
    void aRepeatedRefusalOfReachIsAnsweredWithTheStatusAlone() {
        Core core = TestCores.core(listener, sink);
        long plugin = openedPlugin(core, "reacher");

        Answer<Long> first = core.call(CoreCalls.getService(plugin, "undeclared"));
        Answer<Long> repeated = core.call(CoreCalls.getService(plugin, "undeclared"));

        Refusal reach = TestCores.refusalOf(first);
        assertEquals(CoreConstants.REFUSAL_UNDECLARED, reach.code());
        assertEquals(Optional.of("undeclared"), reach.subject());
        Answer.Refused<Long> again = assertInstanceOf(Answer.Refused.class, repeated);
        assertEquals(RefusalStatus.REFUSED, again.status());
        assertEquals(Optional.empty(), again.refusal());
        core.destroy();
    }

    @Test
    void aLateRegistrationIsAnsweredWithTheStatusAlone() {
        Core core = TestCores.core(listener, sink);
        Manifest providing = new Manifest("late", 0, Optional.empty(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(new Manifest.ServiceOffer("late:service", Collections.emptyList())),
                        Collections.emptyList(), Collections.emptyList(), Optional.empty());
        long plugin = openedPlugin(core, providing);
        ok(core.call(CoreCalls.releasePlugin(plugin)));

        Answer<Long> registered = core.call(CoreCalls.registerService(plugin, "late:service"));
        Answer<Long> watched = core.call(CoreCalls.watchService(plugin, "late:service"));

        assertEquals(new Answer.Refused<Long>(RefusalStatus.REFUSED, Optional.empty()), registered);
        assertEquals(new Answer.Refused<Long>(RefusalStatus.REFUSED, Optional.empty()), watched);
        core.destroy();
    }

    @Test
    void theStateOfAHandleNamingNothingIsAnsweredWithTheStatusAlone() {
        Core core = TestCores.core(listener, sink);

        Answer<Integer> state = core.call(CoreCalls.handleState(424242L));

        assertEquals(new Answer.Refused<Integer>(RefusalStatus.REFUSED, Optional.empty()), state);
        assertEquals(Collections.emptyList(), listener.events());
        core.destroy();
    }

    @Test
    void aCallDrainingTwoRefusalsIsAnsweredWithTheFirst() {
        CoreFunctions twice = TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.GET_SERVICE ? GetTwice.around(handle) : handle);
        Core core = TestCores.core(twice, EventCopy.INSTANCE, listener, sink);
        long plugin = openedPlugin(core, "twice");

        Answer<Long> answer = core.call(CoreCalls.getService(plugin, "asked"));

        assertEquals(2, listener.of(CoreEvent.ReachRefusedEvent.class).size());
        Refusal refusal = TestCores.refusalOf(answer);
        assertEquals(Optional.of(GetTwice.FIRST_ID), refusal.subject());
        core.destroy();
    }

    @Test
    void aMalformedCallCarryingTheCallersPartIsAnsweredTheRefusalAndNoSinkHearsOfIt() {
        Core core = TestCores.core(listener, sink);

        Answer<Long> answer = core.call(CoreCalls.registerService(HOST, ""));

        Answer.Refused<Long> refused = assertInstanceOf(Answer.Refused.class, answer);
        assertEquals(RefusalStatus.MALFORMED, refused.status());
        assertEquals(CoreConstants.REFUSAL_MALFORMED_CALL, refused.refusal().get().code());
        assertEquals(Collections.emptyList(), sink.failures());
        core.destroy();
    }

    @Test
    void aMalformedCallTheLayerBuiltWholeIsADefectToldToTheSinkOnce() {
        CoreFunctions withoutCondition = TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.HANDLE_STATE ? withNullArgument(handle, 2) : handle);
        Core core = TestCores.core(withoutCondition, EventCopy.INSTANCE, listener, sink);

        Answer<Integer> answer = core.call(CoreCalls.handleState(1L));

        Answer.Defect<Integer> defect = assertInstanceOf(Answer.Defect.class, answer);
        Refusal queued = listener.of(CoreEvent.RefusalEvent.class).get(0).refusal();
        assertTrue(defect.detail().contains(queued.detail()), defect.detail());
        assertEquals(1, sink.failures().size());
        assertEquals(FailureKind.LAYER_DEFECT, sink.failures().get(0).kind());
        core.destroy();
    }

    @Test
    void aGetOrWatchOfAMalformedIdIsTheCallersMistakeAndNoSinkHearsOfIt() {
        Core core = TestCores.core(listener, sink);

        Answer<Long> got = core.call(CoreCalls.getService(HOST, ""));
        Answer<Long> watched = core.call(CoreCalls.watchService(HOST, ""));

        assertEquals(RefusalStatus.MALFORMED, assertInstanceOf(Answer.Refused.class, got).status());
        assertEquals(RefusalStatus.MALFORMED, assertInstanceOf(Answer.Refused.class, watched).status());
        assertEquals(Collections.emptyList(), sink.failures());
        core.destroy();
    }

    @Test
    void aMalformedAdvanceCanOnlyBeTheLayersAndIsADefect() {
        CoreFunctions withoutCore = TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.ADVANCE ? withNullArgument(handle, 0) : handle);
        Core core = TestCores.core(withoutCore, EventCopy.INSTANCE, listener, sink);

        Answer<Void> answer = core.call(CoreCalls.advance(10));

        assertInstanceOf(Answer.Defect.class, answer);
        assertEquals(1, sink.of(FailureKind.LAYER_DEFECT).size());
        core.destroy();
    }

    @Test
    void aMalformedOpenCarriesTheCallersPayloadAndIsTheCallersMistake() {
        CoreFunctions withoutCore = TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.OPEN_PLUGIN ? withNullArgument(handle, 0) : handle);
        Core core = TestCores.core(withoutCore, EventCopy.INSTANCE, listener, sink);
        long plugin = ok(core.call(CoreCalls.registerManifest(Manifest.named("opened"))));

        Answer<Void> answer = core.call(CoreCalls.openPlugin(plugin, 7));

        assertEquals(RefusalStatus.MALFORMED, assertInstanceOf(Answer.Refused.class, answer).status());
        assertEquals(Collections.emptyList(), sink.failures());
        core.destroy();
    }

    @Test
    void anOutOfMemoryAnswerIsTheCoresRefusalAndNoDefect() {
        CoreFunctions starved = TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.REGISTER_SERVICE ? withStatus(handle, 4) : handle);
        Core core = TestCores.core(starved, EventCopy.INSTANCE, listener, sink);

        Answer<Long> answer = core.call(CoreCalls.registerService(HOST, "starved"));

        assertEquals(new Answer.Refused<Long>(RefusalStatus.OUT_OF_MEMORY, Optional.empty()), answer);
        assertEquals(Collections.emptyList(), sink.failures());
        core.destroy();
    }

    @Test
    void aGetFindingNothingIsAnAbsenceNotARefusal() {
        Core core = TestCores.core(listener, sink);

        assertEquals(new Answer.Absent<Long>(), core.call(CoreCalls.getService(HOST, "nothing")));
        core.destroy();
    }

    @Test
    void whatACallThatThrewNotedIsDroppedAndNotToldWithTheNextCall() {
        Core core = TestCores.core(TestCores.functions(), new EventCopyTest.FailingCopy(sequence -> true,
                sequence -> false), listener, sink);
        CallSteps<Long> throwing = new CallSteps<>(() -> Optional.empty(), value -> {
            throw new InternalError("an answered step failing on purpose");
        });

        assertThrows(InternalError.class, () -> CoreInternals.call(core,
                CoreCalls.wantService(HOST, "awaited", WantOptions.timed(0, 10)), throwing));
        ok(core.call(CoreCalls.ledger()));

        assertEquals(Collections.emptyList(), sink.failures());
        core.destroy();
    }

    @Test
    void noAnswerStaysReachableFromTheWrapperOnceItsCallReturns() throws InterruptedException {
        Core core = TestCores.core(listener, sink);
        openedPlugin(core, "remembered");

        WeakReference<List<LedgerRow>> ledger = ledgerAnswered(core);

        assertTrue(TestCores.collected(ledger), "the ledger a call answered stayed reachable after it returned");
        core.destroy();
    }

    @Test
    void aClosedCoreHoldsNoRefusalItsLastCallMet() throws InterruptedException {
        Core core = TestCores.core(event -> { }, sink);

        WeakReference<Refusal> refusal = refusalMet(core);
        core.destroy();
        core.close();

        assertTrue(TestCores.collected(refusal), "the closed core still held the refusal its last call met");
    }

    private static WeakReference<List<LedgerRow>> ledgerAnswered(Core core) {
        return new WeakReference<>(ok(core.call(CoreCalls.ledger())));
    }

    private static WeakReference<Refusal> refusalMet(Core core) {
        return new WeakReference<>(TestCores.refusalOf(core.call(CoreCalls.unregister(424242L))));
    }

    static long openedPlugin(Core core, String importName) {
        return openedPlugin(core, Manifest.named(importName));
    }

    static long openedPlugin(Core core, Manifest manifest) {
        long plugin = ok(core.call(CoreCalls.registerManifest(manifest)));
        ok(core.call(CoreCalls.openPlugin(plugin, 0)));
        return plugin;
    }

    /** The function called as it is, its status replaced by {@code status}. */
    static NativeFunction withStatus(NativeFunction original, int status) {
        return arguments -> {
            original.call(arguments);
            return status;
        };
    }

    static NativeFunction withNullArgument(NativeFunction original, int position) {
        return arguments -> {
            long[] replaced = arguments.clone();
            replaced[position] = 0;
            return original.call(replaced);
        };
    }

    /** A {@code weivel_get_service} that first asks for {@link #FIRST_ID}, so one call drains two refusals of reach. */
    static final class GetTwice {
        static final String FIRST_ID = "asked-first";

        private GetTwice() {
        }

        static NativeFunction around(NativeFunction original) {
            return arguments -> {
                try (NativeArena arena = new NativeArena(NativeBackends.current())) {
                    long[] first = arguments.clone();
                    first[2] = arena.text(FIRST_ID).address();
                    original.call(first);
                }
                return original.call(arguments);
            };
        }
    }
}
