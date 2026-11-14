package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
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
        Manifest manifest = new Manifest("lister", 0, Optional.empty(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(new Manifest.ServiceOffer("lister:own", Collections.emptyList())),
                Arrays.asList(consumed("second"), consumed("first"), consumed("absent")), Collections.emptyList(),
                        Optional.empty());
        long plugin = WrapperCallTest.openedPlugin(core, manifest);
        ok(core.call(CoreCalls.registerService(HOST, "first")));
        ok(core.call(CoreCalls.registerService(HOST, "second")));
        ok(core.call(CoreCalls.registerService(plugin, "lister:own")));

        assertEquals(Arrays.asList("second", "first", "lister:own"), ok(core.call(CoreCalls.serviceIds(plugin))));
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

        assertEquals(Arrays.asList(new CapabilityImplementation(secondImplementation, second),
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

        assertEquals(Arrays.asList(new CapabilityImplementation(firstImplementation, first),
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
        assertEquals(Collections.emptyList(), listener.of(CoreEvent.CapabilityEvent.class));

        ok(core.call(CoreCalls.verifyActivation(provider)));

        CoreEvent.CapabilityEvent joined = listener.of(CoreEvent.CapabilityEvent.class).get(0);
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
        Manifest providing = new Manifest("fenced", 0, Optional.empty(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(new Manifest.ServiceOffer("fenced:service", Collections.emptyList())),
                        Collections.emptyList(), Collections.emptyList(),
                Optional.empty());
        long plugin = WrapperCallTest.openedPlugin(core, providing);

        ok(core.call(CoreCalls.quarantinePlugin(plugin,
                Optional.of(new QuarantineReason(CoreConstants.REFUSAL_NONE, "it failed", "fix it")))));

        assertEquals(new Answer.Refused<Long>(RefusalStatus.REFUSED, Optional.empty()),
                core.call(CoreCalls.registerService(plugin, "fenced:service")));
    }

    @Test
    void aCapacityAnswerIsHandedToTheCoreWhichSettlesTheWantOnAYes() {
        core = TestCores.created(TestCores.functions(), EventCopy.INSTANCE, new CoreOptions(0, 0, 0,
                Collections.emptyList(),
                Collections.emptyList(), Collections.singletonList(new CoreOptions.ConstraintEntry("latency",
                        "latency", 2))), listener, sink);
        long consumer = WrapperCallTest.openedPlugin(core, new Manifest("consumer", 0, Optional.empty(),
                Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                        Collections.singletonList(new Manifest.ServiceWant("measured", Optional.empty(),
                        Choice.YES, Choice.NO, Collections.singletonList(new NamedNumber("latency", 10.0)))),
                                Collections.emptyList(),
                Optional.empty()));
        long registration = ok(core.call(CoreCalls.registerService(HOST, "measured")));
        long want = ok(core.call(CoreCalls.wantService(consumer, "measured",
                new WantOptions(Choice.DEFAULT, 0, 0, Choice.DEFAULT, 0, 100))));
        CoreEvent.CapacityWantedEvent attributes = listener.of(CoreEvent.CapacityWantedEvent.class).get(0);

        ok(core.call(CoreCalls.reportCapacity(want, registration, new CapacityAnswer(attributes.request(),
                Collections.singletonList(new NamedNumber("latency", 5.0)), Choice.DEFAULT), 0)));
        CoreEvent.CapacityWantedEvent serve = TestCores.last(listener.of(CoreEvent.CapacityWantedEvent.class));
        ok(core.call(CoreCalls.reportCapacity(want, registration, new CapacityAnswer(serve.request(),
                Collections.emptyList(),
                Choice.YES), 0)));

        CoreEvent.WantSettledEvent settled = listener.of(CoreEvent.WantSettledEvent.class).get(0);
        assertEquals(registration, settled.implementation());
        assertEquals(Optional.empty(), settled.refusal());
    }

    @Test
    void anAnswerToNoOpenQuestionIsAnAbsence() {
        core = TestCores.core(listener, sink);

        assertEquals(new Answer.Absent<Void>(), core.call(CoreCalls.reportCapacity(1L, 2L,
                new CapacityAnswer(3L, Collections.emptyList(), Choice.YES), 0)));
    }

    @Test
    void textsHoldingANulInTheNewCallsAreTheCallersMistake() {
        core = TestCores.core(listener, sink);
        String cut = "short\u0000tail";

        assertInstanceOf(Answer.Mistake.class, core.call(CoreCalls.allCapabilities(HOST, cut)));
        assertInstanceOf(Answer.Mistake.class, core.call(CoreCalls.watchCapability(HOST, cut)));
        assertInstanceOf(Answer.Mistake.class, core.call(CoreCalls.reportCapacity(1L, 2L,
                new CapacityAnswer(3L, Collections.singletonList(new NamedNumber(cut, 1.0)), Choice.YES), 0)));
        assertInstanceOf(Answer.Mistake.class, core.call(CoreCalls.quarantinePlugin(1L,
                Optional.of(new QuarantineReason(0, cut, "fix")))));
        assertEquals(Collections.emptyList(), sink.failures());
    }

    private long provider(String importName) {
        Manifest manifest = new Manifest(importName, 0, Optional.empty(), Collections.emptyList(),
                Collections.singletonList("screens"), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Optional.empty());
        return WrapperCallTest.openedPlugin(core, manifest);
    }

    private static Manifest.ServiceWant consumed(String id) {
        return new Manifest.ServiceWant(id, Optional.empty(), Choice.YES, Choice.NO, Collections.emptyList());
    }

    /**
     * A {@code weivel_all_capabilities} that hands its elements over laid out at another stride, each element's
     * {@code size} saying so, as a newer core would lay out wider ones and no core lays out narrower ones.
     */
    static final class Restrided {
        private Restrided() {
        }

        static NativeFunction around(NativeFunction original, long stride) {
            return arguments -> {
                long status = original.call(arguments);
                restride(arguments[3], stride, (int) status);
                return status;
            };
        }

        private static void restride(long listAddress, long stride, int status) {
            NativeAccess access = NativeBackends.current();
            NativeRegion list = new NativeRegion(access, listAddress, CoreLayouts.CAPABILITY_LIST.byteSize(), null);
            int count = list.getInt(16);
            if (status == 0 && count > 0) {
                NativeRegion items = list.at(list.getAddress(8), 24L * count);
                long restridedSize = Math.max(stride, 24) * count;
                NativeRegion restrided = list.at(access.allocate(restridedSize), restridedSize);
                for (int index = 0; index < count; index++) {
                    long base = index * stride;
                    restrided.putInt(base, (int) stride);
                    if (stride >= 24) {
                        restrided.putLong(base + 8, items.getLong(index * 24L + 8));
                        restrided.putLong(base + 16, items.getLong(index * 24L + 16));
                    }
                }
                list.putAddress(8, restrided.address());
            }
        }
    }
}
