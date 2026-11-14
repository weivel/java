package com.weivel.core;

import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The ledger read, as the conformance driver reads which plugins are quarantined and why. */
class LedgerCallsTest {
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
    void theLedgerShowsAPluginQuarantinedForARefusedVerificationWithTheCoresRefusal() {
        TestCores.CallLog log = new TestCores.CallLog();
        core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        long whole = WrapperCallTest.openedPlugin(core, Manifest.named("whole"));
        long flaky = WrapperCallTest.openedPlugin(core, new Manifest("flaky", 0, Optional.empty(),
                Collections.<String>emptyList(), Collections.singletonList("screens"),
                Collections.<String>emptyList(), Collections.<Manifest.ServiceOffer>emptyList(),
                Collections.<Manifest.ServiceWant>emptyList(), Collections.<String>emptyList(), Optional.empty()));
        ok(core.call(CoreCalls.verifyOrQuarantine(flaky)));

        List<LedgerRow> rows = ok(core.call(CoreCalls.ledger()));

        assertEquals(2, rows.size());
        LedgerRow first = rows.get(0);
        assertEquals(whole, first.plugin());
        assertEquals("whole", first.importName());
        assertEquals(LedgerRow.Status.ACTIVE, first.status());
        assertFalse(first.reason().isPresent());
        List<LedgerRow> quarantined = rows.stream().filter(row -> row.status() == LedgerRow.Status.QUARANTINED)
                .collect(Collectors.toList());
        assertEquals(1, quarantined.size());
        Refusal reason = quarantined.get(0).reason().get();
        assertEquals(CoreConstants.REFUSAL_ACTIVATION_MISMATCH, reason.code());
        assertEquals(Optional.of("flaky"), reason.subject());
        assertTrue(reason.hasFix());
        assertFalse(reason.fix().isEmpty());
        assertEquals(1, log.count(CoreFunction.LEDGER_SNAPSHOT_RELEASE));
    }

    @Test
    void theLedgerShowsTheReasonAQuarantineWasGivenWithNoSubject() {
        core = TestCores.core(listener, sink);
        long plugin = WrapperCallTest.openedPlugin(core, Manifest.named("told"));
        ok(core.call(CoreCalls.quarantinePlugin(plugin,
                Optional.of(new QuarantineReason(CoreConstants.REFUSAL_NONE, "its activate failed", "fix it")))));

        LedgerRow row = ok(core.call(CoreCalls.ledger())).get(0);

        assertEquals(LedgerRow.Status.QUARANTINED, row.status());
        assertEquals("its activate failed", row.reason().get().detail());
        assertEquals("fix it", row.reason().get().fix());
        assertEquals(Optional.<String>empty(), row.reason().get().subject());
    }
}
