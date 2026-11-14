package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The calls a plugin's context makes beyond the wrapper's own, each read as the core's header says it answers. */
class ContextCallsTest {
    private final TestCores.RecordingSink sink = new TestCores.RecordingSink();
    private final TestCores.RecordingListener listener = new TestCores.RecordingListener();
    private Core core;

    @AfterEach
    void destroy() {
        if (core != null) {
            core.destroy();
        }
    }

    @Test
    void theIdsAPluginMayReachAreAnsweredConsumedBeforeProvidedInItsManifestsOrder() {
        TestCores.CallLog log = new TestCores.CallLog();
        core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        Manifest manifest = new Manifest("lister", 0, Optional.empty(), List.of(), List.of(), List.of(),
                List.of(new Manifest.ServiceOffer("lister:own", List.of())),
                List.of(consumed("second"), consumed("first"), consumed("absent")), List.of(), Optional.empty());
        long plugin = WrapperCallTest.openedPlugin(core, manifest);
        ok(core.call(CoreCalls.registerService(HOST, "first")));
        ok(core.call(CoreCalls.registerService(HOST, "second")));
        ok(core.call(CoreCalls.registerService(plugin, "lister:own")));

        assertEquals(List.of("second", "first", "lister:own"), ok(core.call(CoreCalls.serviceIds(plugin))));
        assertEquals(1, log.count(CoreFunction.SERVICE_LIST_RELEASE));
    }

    @Test
    void theIdsOfAPluginNeverOpenedAreTheCoresRefusal() {
        core = TestCores.core(listener, sink);
        long plugin = ok(core.call(CoreCalls.registerManifest(Manifest.named("unopened"))));

        Answer<List<String>> ids = core.call(CoreCalls.serviceIds(plugin));

        assertEquals(RefusalStatus.REFUSED, assertInstanceOf(Answer.Refused.class, ids).status());
    }

    @Test
    void theImplementationsOfACapabilityAreAnsweredInTheOrderTheirActivationsWereVerified() {
        TestCores.CallLog log = new TestCores.CallLog();
        core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        long first = provider("first");
        long second = provider("second");
        long firstImplementation = ok(core.call(CoreCalls.provideCapability(first, "screens")));
        long secondImplementation = ok(core.call(CoreCalls.provideCapability(second, "screens")));
        ok(core.call(CoreCalls.verifyActivation(second)));
        ok(core.call(CoreCalls.verifyActivation(first)));

        List<CapabilityImplementation> listed = ok(core.call(CoreCalls.allCapabilities(HOST, "screens")));

        assertEquals(List.of(new CapabilityImplementation(secondImplementation, second),
                new CapabilityImplementation(firstImplementation, first)), listed);
        assertEquals(1, log.count(CoreFunction.CAPABILITY_LIST_RELEASE));
    }

    @Test
    void capabilityElementsWiderThanTheLayerKnowsAreReadAtTheFirstElementsSize() {
        core = TestCores.core(TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.ALL_CAPABILITIES ? Restrided.around(handle, 48) : handle),
                EventCopy.INSTANCE, listener, sink);
        long first = provider("first");
        long second = provider("second");
        long firstImplementation = ok(core.call(CoreCalls.provideCapability(first, "screens")));
        long secondImplementation = ok(core.call(CoreCalls.provideCapability(second, "screens")));
        ok(core.call(CoreCalls.verifyActivation(first)));
        ok(core.call(CoreCalls.verifyActivation(second)));

        assertEquals(List.of(new CapabilityImplementation(firstImplementation, first),
                new CapabilityImplementation(secondImplementation, second)),
                ok(core.call(CoreCalls.allCapabilities(HOST, "screens"))));
    }

    @Test
    void aCapabilityElementNarrowerThanTheLayerReadsIsADefectAndTheCallsEventsAreStillDrained() {
        core = TestCores.core(TestCores.functions().mapped((function, handle) ->
                function == CoreFunction.ALL_CAPABILITIES ? Restrided.around(handle, 8) : handle),
                EventCopy.INSTANCE, listener, sink);
        long plugin = WrapperCallTest.openedPlugin(core, "reacher");

        Answer<List<CapabilityImplementation>> listed = core.call(CoreCalls.allCapabilities(plugin, "screens"));

        assertInstanceOf(Answer.Refused.class, listed);
        assertEquals(1, listener.of(CoreEvent.ReachRefusedEvent.class).size());
        long provider = provider("narrow");
        ok(core.call(CoreCalls.provideCapability(provider, "screens")));
        ok(core.call(CoreCalls.verifyActivation(provider)));
        int heardBefore = listener.events().size();

        Answer<List<CapabilityImplementation>> narrow = core.call(CoreCalls.allCapabilities(HOST, "screens"));

        assertInstanceOf(Answer.Defect.class, narrow);
        assertEquals(1, sink.of(FailureKind.LAYER_DEFECT).size());
        assertEquals(heardBefore, listener.events().size());
        assertEquals(new Answer.Absent<Long>(), core.call(CoreCalls.getService(HOST, "nothing")));
    }

    @Test
    void aCapabilityWatchHearsAnImplementationJoinOnceItsProviderIsVerified() {
        core = TestCores.core(listener, sink);
        long provider = provider("joining");
        long watch = ok(core.call(CoreCalls.watchCapability(HOST, "screens")));
        long implementation = ok(core.call(CoreCalls.provideCapability(provider, "screens")));
        assertEquals(List.of(), listener.of(CoreEvent.CapabilityEvent.class));

        ok(core.call(CoreCalls.verifyActivation(provider)));

        CoreEvent.CapabilityEvent joined = listener.of(CoreEvent.CapabilityEvent.class).getFirst();
        assertEquals(watch, joined.watch());
        assertEquals(implementation, joined.implementation());
        assertEquals(provider, joined.provider());
    }

    @Test
    void aVerificationOfAPluginThatProvidedLessThanItDeclaresIsTheCoresRefusal() {
        core = TestCores.core(listener, sink);
        long provider = provider("short");

        Refusal refusal = TestCores.refusalOf(core.call(CoreCalls.verifyActivation(provider)));

        assertEquals(CoreConstants.REFUSAL_ACTIVATION_MISMATCH, refusal.code());
    }

    @Test
    void aQuarantinedPluginsCallsAreLate() {
        core = TestCores.core(listener, sink);
        Manifest providing = new Manifest("fenced", 0, Optional.empty(), List.of(), List.of(), List.of(),
                List.of(new Manifest.ServiceOffer("fenced:service", List.of())), List.of(), List.of(),
                Optional.empty());
        long plugin = WrapperCallTest.openedPlugin(core, providing);

        ok(core.call(CoreCalls.quarantinePlugin(plugin,
                Optional.of(new QuarantineReason(CoreConstants.REFUSAL_NONE, "it failed", "fix it")))));

        assertEquals(new Answer.Refused<Long>(RefusalStatus.REFUSED, Optional.empty()),
                core.call(CoreCalls.registerService(plugin, "fenced:service")));
    }

    @Test
    void aCapacityAnswerIsHandedToTheCoreWhichSettlesTheWantOnAYes() {
        core = TestCores.created(TestCores.functions(), EventCopy.INSTANCE, new CoreOptions(0, 0, 0, List.of(),
                List.of(), List.of(new CoreOptions.ConstraintEntry("latency", "latency", 2))), listener, sink);
        long consumer = WrapperCallTest.openedPlugin(core, new Manifest("consumer", 0, Optional.empty(), List.of(),
                List.of(), List.of(), List.of(), List.of(new Manifest.ServiceWant("measured", Optional.empty(),
                        Choice.YES, Choice.NO, List.of(new NamedNumber("latency", 10.0)))), List.of(),
                Optional.empty()));
        long registration = ok(core.call(CoreCalls.registerService(HOST, "measured")));
        long want = ok(core.call(CoreCalls.wantService(consumer, "measured",
                new WantOptions(Choice.DEFAULT, 0, 0, Choice.DEFAULT, 0, 100))));
        CoreEvent.CapacityWantedEvent attributes = listener.of(CoreEvent.CapacityWantedEvent.class).getFirst();

        ok(core.call(CoreCalls.reportCapacity(want, registration, new CapacityAnswer(attributes.request(),
                List.of(new NamedNumber("latency", 5.0)), Choice.DEFAULT), 0)));
        CoreEvent.CapacityWantedEvent serve = listener.of(CoreEvent.CapacityWantedEvent.class).getLast();
        ok(core.call(CoreCalls.reportCapacity(want, registration, new CapacityAnswer(serve.request(), List.of(),
                Choice.YES), 0)));

        CoreEvent.WantSettledEvent settled = listener.of(CoreEvent.WantSettledEvent.class).getFirst();
        assertEquals(registration, settled.implementation());
        assertEquals(Optional.empty(), settled.refusal());
    }

    @Test
    void anAnswerToNoOpenQuestionIsAnAbsence() {
        core = TestCores.core(listener, sink);

        assertEquals(new Answer.Absent<Void>(), core.call(CoreCalls.reportCapacity(1L, 2L,
                new CapacityAnswer(3L, List.of(), Choice.YES), 0)));
    }

    @Test
    void textsHoldingANulInTheNewCallsAreTheCallersMistake() {
        core = TestCores.core(listener, sink);
        String cut = "short\u0000tail";

        assertInstanceOf(Answer.Mistake.class, core.call(CoreCalls.allCapabilities(HOST, cut)));
        assertInstanceOf(Answer.Mistake.class, core.call(CoreCalls.watchCapability(HOST, cut)));
        assertInstanceOf(Answer.Mistake.class, core.call(CoreCalls.reportCapacity(1L, 2L,
                new CapacityAnswer(3L, List.of(new NamedNumber(cut, 1.0)), Choice.YES), 0)));
        assertInstanceOf(Answer.Mistake.class, core.call(CoreCalls.quarantinePlugin(1L,
                Optional.of(new QuarantineReason(0, cut, "fix")))));
        assertEquals(List.of(), sink.failures());
    }

    private long provider(String importName) {
        Manifest manifest = new Manifest(importName, 0, Optional.empty(), List.of(), List.of("screens"), List.of(),
                List.of(), List.of(), List.of(), Optional.empty());
        return WrapperCallTest.openedPlugin(core, manifest);
    }

    private static Manifest.ServiceWant consumed(String id) {
        return new Manifest.ServiceWant(id, Optional.empty(), Choice.YES, Choice.NO, List.of());
    }

    /**
     * A {@code weivel_all_capabilities} that hands its elements over laid out at another stride, each element's
     * {@code size} saying so, as a newer core would lay out wider ones and no core lays out narrower ones.
     */
    static final class Restrided {
        private final MethodHandle original;
        private final long stride;

        private Restrided(MethodHandle original, long stride) {
            this.original = original;
            this.stride = stride;
        }

        static MethodHandle around(MethodHandle original, long stride) {
            try {
                return MethodHandles.lookup().findVirtual(Restrided.class, "call", MethodType.methodType(int.class,
                                MemorySegment.class, long.class, MemorySegment.class, MemorySegment.class,
                                MemorySegment.class))
                        .bindTo(new Restrided(original, stride));
            } catch (ReflectiveOperationException unreachable) {
                throw new AssertionError(unreachable);
            }
        }

        int call(MemorySegment core, long plugin, MemorySegment id, MemorySegment list, MemorySegment pending)
                throws Throwable {
            int status = (int) original.invokeExact(core, plugin, id, list, pending);
            int count = list.get(JAVA_INT, 16);
            if (status == 0 && count > 0) {
                MemorySegment items = list.get(ADDRESS, 8).reinterpret(24L * count);
                MemorySegment restrided = Arena.global().allocate(Math.max(stride, 24) * count, 8);
                for (int index = 0; index < count; index++) {
                    long base = index * stride;
                    restrided.set(JAVA_INT, base, (int) stride);
                    if (stride >= 24) {
                        restrided.set(JAVA_LONG, base + 8, items.get(JAVA_LONG, index * 24L + 8));
                        restrided.set(JAVA_LONG, base + 16, items.get(JAVA_LONG, index * 24L + 16));
                    }
                }
                list.set(ADDRESS, 8, restrided);
            }
            return status;
        }
    }
}
