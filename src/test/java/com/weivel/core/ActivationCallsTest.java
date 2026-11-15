package com.weivel.core;

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

/** The calls a host makes to plan an activation and to verify one, each read as the core's header says it answers. */
class ActivationCallsTest {
    private static final long STATUS_MALFORMED = 2;

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
    void aPlanOrdersAProviderBeforeItsConsumerAndIsReleasedOnceCopied() {
        TestCores.CallLog log = new TestCores.CallLog();
        core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        long consumer = registered(services("consumer", Collections.<String>emptyList(),
                Collections.singletonList("provider:events")));
        long provider = registered(services("provider", Collections.singletonList("provider:events"),
                Collections.<String>emptyList()));

        ActivationPlan plan = ok(core.call(CoreCalls.planActivation(Arrays.asList(consumer, provider),
                EdgeSource.SERVICES, Collections.<String>emptyList())));

        assertEquals(Arrays.asList(provider, consumer), plan.order());
        assertEquals(Collections.singletonList(new ActivationPlan.Edge(provider, consumer)), plan.edges());
        assertTrue(plan.cycles().isEmpty());
        assertTrue(plan.refusals().isEmpty());
        assertEquals(1, log.count(CoreFunction.ACTIVATION_PLAN_RELEASE));
    }

    @Test
    void aPlanRefusesAnUnmetNeedUnlessTheHostProvidesIt() {
        core = TestCores.core(listener, sink);
        long needy = registered(services("needy", Collections.<String>emptyList(),
                Collections.singletonList("clock")));

        ActivationPlan unmet = ok(core.call(CoreCalls.planActivation(Collections.singletonList(needy),
                EdgeSource.SERVICES, Collections.<String>emptyList())));
        ActivationPlan met = ok(core.call(CoreCalls.planActivation(Collections.singletonList(needy),
                EdgeSource.SERVICES, Collections.singletonList("clock"))));

        assertTrue(unmet.order().isEmpty());
        assertEquals(1, unmet.refusals().size());
        assertEquals(needy, unmet.refusals().get(0).plugin());
        assertEquals(CoreConstants.REFUSAL_UNMET_NEED, unmet.refusals().get(0).refusal().code());
        assertEquals(Collections.singletonList(needy), met.order());
    }

    @Test
    void aPlanCarriesItsCyclesMembers() {
        core = TestCores.core(listener, sink);
        long first = registered(new Manifest("first", 0, Optional.empty(), Collections.<String>emptyList(),
                Collections.singletonList("left"), Collections.singletonList("right"),
                Collections.<Manifest.ServiceOffer>emptyList(), Collections.<Manifest.ServiceWant>emptyList(),
                Collections.<String>emptyList(), Optional.empty()));
        long second = registered(new Manifest("second", 0, Optional.empty(), Collections.<String>emptyList(),
                Collections.singletonList("right"), Collections.singletonList("left"),
                Collections.<Manifest.ServiceOffer>emptyList(), Collections.<Manifest.ServiceWant>emptyList(),
                Collections.<String>emptyList(), Optional.empty()));

        ActivationPlan plan = ok(core.call(CoreCalls.planActivation(Arrays.asList(first, second),
                EdgeSource.CAPABILITIES, Collections.<String>emptyList())));

        assertEquals(1, plan.cycles().size());
        assertEquals(Arrays.asList(first, second), plan.cycles().get(0));
    }

    @Test
    void aHostProvidedIdHoldingANulIsTheCallersMistake() {
        core = TestCores.core(listener, sink);

        Answer<ActivationPlan> plan = core.call(CoreCalls.planActivation(Collections.<Long>emptyList(),
                EdgeSource.SERVICES, Collections.singletonList("cl\u0000ock")));

        assertInstanceOf(Answer.Mistake.class, plan);
    }

    @Test
    void aVerificationThatPassesQuarantinesNothing() {
        core = TestCores.core(listener, sink);
        long plugin = WrapperCallTest.openedPlugin(core, Manifest.named("whole"));

        Verification verified = ok(core.call(CoreCalls.verifyOrQuarantine(plugin)));

        assertTrue(verified.verified());
        assertTrue(listener.of(CoreEvent.LogEvent.class).isEmpty());
    }

    @Test
    void aRefusedVerificationIsQuarantinedInTheSameCrossingWithNoReasonOfTheLayers() {
        TestCores.CallLog log = new TestCores.CallLog();
        core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        long plugin = WrapperCallTest.openedPlugin(core, new Manifest("lacking", 0, Optional.empty(),
                Collections.<String>emptyList(), Collections.singletonList("screens"),
                Collections.<String>emptyList(), Collections.<Manifest.ServiceOffer>emptyList(),
                Collections.<Manifest.ServiceWant>emptyList(), Collections.<String>emptyList(), Optional.empty()));
        log.clear();
        listener.clear();

        Verification verification = ok(core.call(CoreCalls.verifyOrQuarantine(plugin)));

        assertTrue(!verification.verified());
        assertEquals(Arrays.asList(CoreFunction.VERIFY_ACTIVATION, CoreFunction.QUARANTINE_PLUGIN),
                log.calls().subList(0, 2));
        List<CoreEvent> heard = listener.events();
        assertInstanceOf(CoreEvent.RefusalEvent.class, heard.get(0));
        assertEquals(CoreConstants.REFUSAL_ACTIVATION_MISMATCH,
                ((CoreEvent.RefusalEvent) heard.get(0)).refusal().code());
        assertEquals(RefusalStatus.REFUSED, assertInstanceOf(Answer.Refused.class,
                core.call(CoreCalls.registerService(plugin, "lacking:late"))).status());
    }

    @Test
    void aRefusedVerificationWhoseQuarantineTheCoreRefusesIsAnsweredTheVerificationsRefusal() {
        core = TestCores.core(listener, sink);
        long plugin = WrapperCallTest.openedPlugin(core, Manifest.named("released"));
        ok(core.call(CoreCalls.releasePlugin(plugin)));

        Answer<Verification> verification = core.call(CoreCalls.verifyOrQuarantine(plugin));

        assertEquals(CoreConstants.REFUSAL_PLUGIN_STATE, TestCores.refusalOf(verification).code());
    }

    @Test
    void aVerificationOfAPluginNeverOpenedIsTheCoresRefusalAndQuarantinesNothing() {
        TestCores.CallLog log = new TestCores.CallLog();
        core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        long plugin = ok(core.call(CoreCalls.registerManifest(Manifest.named("unopened"))));

        Answer<Void> verified = core.call(CoreCalls.verifyActivation(plugin));

        assertEquals(RefusalStatus.REFUSED, assertInstanceOf(Answer.Refused.class, verified).status());
        assertEquals(0, log.count(CoreFunction.QUARANTINE_PLUGIN));
    }

    @Test
    void aReleaseOfACopiedAnswerThatTheCoreRefusesIsToldToTheSinkAndTheCopyStillAnswers() {
        List<CoreFunction> releases = Arrays.asList(CoreFunction.ACTIVATION_PLAN_RELEASE,
                CoreFunction.LEDGER_SNAPSHOT_RELEASE, CoreFunction.SERVICE_LIST_RELEASE,
                CoreFunction.CAPABILITY_LIST_RELEASE);
        core = TestCores.core(TestCores.functions().mapped((function, original) -> releases.contains(function)
                ? arguments -> {
                    original.call(arguments);
                    return STATUS_MALFORMED;
                }
                : original), EventCopy.INSTANCE, listener, sink);
        long plugin = registered(services("lonely", Collections.<String>emptyList(),
                Collections.<String>emptyList()));

        ActivationPlan plan = ok(core.call(CoreCalls.planActivation(Collections.singletonList(plugin),
                EdgeSource.SERVICES, Collections.<String>emptyList())));
        List<LedgerRow> ledger = ok(core.call(CoreCalls.ledger()));
        ok(core.call(CoreCalls.openPlugin(plugin, 0)));
        ok(core.call(CoreCalls.serviceIds(plugin)));
        ok(core.call(CoreCalls.allCapabilities(CoreConstants.HOST, "screens")));

        assertEquals(Collections.singletonList(plugin), plan.order());
        assertEquals(1, ledger.size());
        List<Failure> defects = sink.of(FailureKind.LAYER_DEFECT);
        assertEquals(releases.size(), defects.size(), String.valueOf(defects));
        for (int index = 0; index < releases.size(); index++) {
            assertTrue(defects.get(index).text().contains(releases.get(index).symbol()), defects.get(index).text());
        }
    }

    private long registered(Manifest manifest) {
        return ok(core.call(CoreCalls.registerManifest(manifest)));
    }

    private static Manifest services(String importName, List<String> provides, List<String> consumes) {
        return new Manifest(importName, 0, Optional.empty(), Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                provides.stream().map(id -> new Manifest.ServiceOffer(id, Collections.<NamedNumber>emptyList()))
                        .collect(java.util.stream.Collectors.toList()),
                consumes.stream().map(id -> new Manifest.ServiceWant(id, Optional.<String>empty(), Choice.NO,
                        Choice.NO, Collections.<NamedNumber>emptyList()))
                        .collect(java.util.stream.Collectors.toList()),
                Collections.<String>emptyList(), Optional.empty());
    }
}
