package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The wrapper's creation, drain and answers, one test per sentence of the contract's {@code core} section. */
class WrapperCallTest {
    private final TestCores.RecordingSink sink = new TestCores.RecordingSink();
    private final TestCores.RecordingListener listener = new TestCores.RecordingListener();

    @Test
    void theCreationIsDrainedAndHeardByTheListenerGivenWithIt() {
        CoreOptions debug = new CoreOptions(0, CoreConstants.LOG_DEBUG, 0, List.of(), List.of(), List.of());

        Core core = TestCores.created(TestCores.functions(), EventCopy.INSTANCE, debug, listener, sink);

        CoreEvent.LogEvent created = assertInstanceOf(CoreEvent.LogEvent.class, listener.events().getFirst());
        assertEquals("core created", created.text());
        assertEquals(1, created.sequence());
        core.destroy();
    }

    @Test
    void aCreationThatThrowsAfterTheCoreAnsweredDestroysThatCoreAndClosesItsArena() {
        CoreOptions debug = new CoreOptions(0, CoreConstants.LOG_DEBUG, 0, List.of(), List.of(), List.of());
        TestCores.CallLog log = new TestCores.CallLog();
        MemorySegment[] seen = new MemorySegment[1];
        EventCopying overflowing = new EventCopying() {
            @Override
            public CoreEvent whole(MemorySegment event, long sequence) {
                seen[0] = event;
                throw new StackOverflowError("a copy overflowing on purpose");
            }

            @Override
            public CoreEvent fixedPart(MemorySegment event, long sequence) {
                return EventCopy.INSTANCE.fixedPart(event, sequence);
            }
        };

        assertThrows(StackOverflowError.class, () -> Wrapper.start(log.around(TestCores.functions()), overflowing,
                debug, Optional.of(listener), sink));

        assertEquals(1, log.count(CoreFunction.CORE_CREATE));
        assertEquals(1, log.count(CoreFunction.CORE_DESTROY));
        assertFalse(seen[0].scope().isAlive());
    }

    @Test
    void optionsTheCoreCallsMalformedAreTheCallersMistakeAndNoSinkHearsOfThem() {
        CoreOptions tooLarge = new CoreOptions(70000, 0, 0, List.of(), List.of(), List.of());

        Answer<Core> created = Core.wrapped(Wrapper.start(TestCores.functions(), EventCopy.INSTANCE, tooLarge,
                Optional.of(listener), sink));

        Answer.Refused<Core> refused = assertInstanceOf(Answer.Refused.class, created);
        assertEquals(RefusalStatus.MALFORMED, refused.status());
        assertEquals(Optional.empty(), refused.refusal());
        assertEquals(List.of(), sink.failures());
    }

    @Test
    void aCallReportingNoEventWaitingMakesNoFurtherCrossing() {
        TestCores.CallLog log = new TestCores.CallLog();
        Core core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        log.clear();

        ok(core.call(CoreCalls.registerService(HOST, "quiet")));

        assertEquals(List.of(CoreFunction.REGISTER_SERVICE), log.calls());
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

        assertEquals(List.of(CoreFunction.REGISTER_SERVICE, CoreFunction.NEXT_EVENT, CoreFunction.EVENT_RELEASE,
                CoreFunction.NEXT_EVENT, CoreFunction.EVENT_RELEASE), log.calls());
        assertEquals(2, listener.of(CoreEvent.WatchEvent.class).size());
        core.destroy();
    }

    @Test
    void eachEventIsCopiedIntoJavaValuesExactly() {
        Core core = TestCores.core(listener, sink);
        long watch = ok(core.call(CoreCalls.watchService(HOST, "copied")));

        long registration = ok(core.call(CoreCalls.registerService(HOST, "copied")));

        CoreEvent.WatchEvent heard = listener.of(CoreEvent.WatchEvent.class).getFirst();
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
        Refusal refusal = refused.refusal().orElseThrow();
        assertEquals(CoreConstants.REFUSAL_STALE_HANDLE, refusal.code());
        assertFalse(refusal.detail().isEmpty());
        assertEquals(refusal, listener.of(CoreEvent.RefusalEvent.class).getFirst().refusal());
        assertEquals(List.of(), sink.failures());
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
        Manifest providing = new Manifest("late", 0, Optional.empty(), List.of(), List.of(), List.of(),
                List.of(new Manifest.ServiceOffer("late:service", List.of())), List.of(), List.of(), Optional.empty());
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
        assertEquals(List.of(), listener.events());
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
        assertEquals(CoreConstants.REFUSAL_MALFORMED_CALL, refused.refusal().orElseThrow().code());
        assertEquals(List.of(), sink.failures());
        core.destroy();
    }

    @Test
    void aMalformedCallTheLayerBuiltWholeIsADefectToldToTheSinkOnce() {
        CoreFunctions withoutCondition = TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.HANDLE_STATE ? withNullArgument(handle, 2) : handle);
        Core core = TestCores.core(withoutCondition, EventCopy.INSTANCE, listener, sink);

        Answer<Integer> answer = core.call(CoreCalls.handleState(1L));

        Answer.Defect<Integer> defect = assertInstanceOf(Answer.Defect.class, answer);
        Refusal queued = listener.of(CoreEvent.RefusalEvent.class).getFirst().refusal();
        assertTrue(defect.detail().contains(queued.detail()), defect.detail());
        assertEquals(1, sink.failures().size());
        assertEquals(FailureKind.LAYER_DEFECT, sink.failures().getFirst().kind());
        core.destroy();
    }

    @Test
    void aGetOrWatchOfAMalformedIdIsTheCallersMistakeAndNoSinkHearsOfIt() {
        Core core = TestCores.core(listener, sink);

        Answer<Long> got = core.call(CoreCalls.getService(HOST, ""));
        Answer<Long> watched = core.call(CoreCalls.watchService(HOST, ""));

        assertEquals(RefusalStatus.MALFORMED, assertInstanceOf(Answer.Refused.class, got).status());
        assertEquals(RefusalStatus.MALFORMED, assertInstanceOf(Answer.Refused.class, watched).status());
        assertEquals(List.of(), sink.failures());
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
        assertEquals(List.of(), sink.failures());
        core.destroy();
    }

    @Test
    void anOutOfMemoryAnswerIsTheCoresRefusalAndNoDefect() {
        CoreFunctions starved = TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.REGISTER_SERVICE ? withStatus(handle, 4) : handle);
        Core core = TestCores.core(starved, EventCopy.INSTANCE, listener, sink);

        Answer<Long> answer = core.call(CoreCalls.registerService(HOST, "starved"));

        assertEquals(new Answer.Refused<Long>(RefusalStatus.OUT_OF_MEMORY, Optional.empty()), answer);
        assertEquals(List.of(), sink.failures());
        core.destroy();
    }

    @Test
    void aGetFindingNothingIsAnAbsenceNotARefusal() {
        Core core = TestCores.core(listener, sink);

        assertEquals(new Answer.Absent<Long>(), core.call(CoreCalls.getService(HOST, "nothing")));
        core.destroy();
    }

    static long openedPlugin(Core core, String importName) {
        return openedPlugin(core, Manifest.named(importName));
    }

    static long openedPlugin(Core core, Manifest manifest) {
        long plugin = ok(core.call(CoreCalls.registerManifest(manifest)));
        ok(core.call(CoreCalls.openPlugin(plugin, 0)));
        return plugin;
    }

    /** The handle made as it is, its status replaced by {@code status}. */
    static MethodHandle withStatus(MethodHandle handle, int status) {
        MethodHandle replaced = MethodHandles.dropArguments(MethodHandles.constant(int.class, status), 0, int.class);
        return MethodHandles.filterReturnValue(handle, replaced);
    }

    static MethodHandle withNullArgument(MethodHandle handle, int position) {
        MethodHandle nothing = MethodHandles.dropArguments(MethodHandles.constant(MemorySegment.class,
                MemorySegment.NULL), 0, MemorySegment.class);
        return MethodHandles.filterArguments(handle, position, nothing);
    }

    /** A {@code weivel_get_service} that first asks for {@link #FIRST_ID}, so one call drains two refusals of reach. */
    static final class GetTwice {
        static final String FIRST_ID = "asked-first";
        private final MethodHandle original;

        private GetTwice(MethodHandle original) {
            this.original = original;
        }

        static MethodHandle around(MethodHandle original) {
            try {
                return MethodHandles.lookup().findVirtual(GetTwice.class, "call", MethodType.methodType(int.class,
                                MemorySegment.class, long.class, MemorySegment.class, MemorySegment.class,
                                MemorySegment.class))
                        .bindTo(new GetTwice(original));
            } catch (ReflectiveOperationException unreachable) {
                throw new AssertionError(unreachable);
            }
        }

        int call(MemorySegment core, long plugin, MemorySegment id, MemorySegment implementation,
                 MemorySegment pending) throws Throwable {
            try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
                int ignored = (int) original.invokeExact(core, plugin, arena.allocateFrom(FIRST_ID), implementation,
                        pending);
            }
            return (int) original.invokeExact(core, plugin, id, implementation, pending);
        }
    }
}
