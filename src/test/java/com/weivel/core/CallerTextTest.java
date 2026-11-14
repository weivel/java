package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A text a caller hands the layer is the caller's value, and C reads a text only up to its first NUL character, so a
 * text holding one would reach the core cut short: every such text is the caller's mistake, refused before anything
 * crosses into the core and reported to no sink.
 */
class CallerTextTest {
    private static final String CUT = "short\u0000tail";

    private final TestCores.RecordingSink sink = new TestCores.RecordingSink();
    private final TestCores.RecordingListener listener = new TestCores.RecordingListener();
    private final TestCores.CallLog log = new TestCores.CallLog();
    private Core core;

    @AfterEach
    void destroy() {
        if (core != null) {
            core.destroy();
        }
    }

    @Test
    void aServiceIdHoldingANulIsNeverRegisteredCutShort() {
        Answer<Long> registered = coreUnderLog().call(CoreCalls.registerService(HOST, CUT));

        assertMistake(registered);
        assertEquals(new Answer.Absent<Long>(), core.call(CoreCalls.getService(HOST, "short")));
    }

    @Test
    void aGetOfAnIdHoldingANulReachesNothing() {
        assertMistake(coreUnderLog().call(CoreCalls.getService(HOST, CUT)));
    }

    @Test
    void aWantOfAnIdHoldingANulReachesNothing() {
        assertMistake(coreUnderLog().call(CoreCalls.wantService(HOST, CUT, WantOptions.unbounded())));
    }

    @Test
    void aWatchOfAnIdHoldingANulReachesNothing() {
        assertMistake(coreUnderLog().call(CoreCalls.watchService(HOST, CUT)));
    }

    @Test
    void aCapabilityIdHoldingANulIsNeverProvided() {
        long plugin = WrapperCallTest.openedPlugin(coreUnderLog(), "provider");
        log.clear();

        assertMistake(core.call(CoreCalls.provideCapability(plugin, CUT)));
    }

    @Test
    void anImportNameHoldingANulIsNeverRegistered() {
        assertMistake(coreUnderLog().call(CoreCalls.registerManifest(Manifest.named(CUT))));
    }

    @Test
    void aTextDeepInAManifestHoldingANulIsFound() {
        Manifest.ServiceWant want = new Manifest.ServiceWant("wanted", Optional.empty(), Choice.YES, Choice.NO,
                List.of(new NamedNumber(CUT, 1.0)));
        Manifest manifest = new Manifest("deep", 0, Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                List.of(want), List.of(), Optional.empty());

        assertMistake(coreUnderLog().call(CoreCalls.registerManifest(manifest)));
    }

    @Test
    void anIdentityHoldingANulIsFound() {
        Manifest manifest = new Manifest("identified", 0, Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), Optional.of(CUT));

        assertMistake(coreUnderLog().call(CoreCalls.registerManifest(manifest)));
    }

    @Test
    void coreOptionsHoldingANulCreateNoCore() {
        CoreOptions options = new CoreOptions(0, 0, 0, List.of(), List.of(CUT), List.of());

        Answer<Core> created = Core.wrapped(Wrapper.start(log.around(TestCores.functions()), EventCopy.INSTANCE,
                options, Optional.of(listener), sink));

        assertInstanceOf(Answer.Mistake.class, created);
        assertEquals(List.of(), log.calls());
        assertEquals(List.of(), sink.failures());
    }

    private Core coreUnderLog() {
        core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        log.clear();
        return core;
    }

    private void assertMistake(Answer<?> answer) {
        Answer.Mistake<?> mistake = assertInstanceOf(Answer.Mistake.class, answer);
        assertTrue(mistake.detail().contains("NUL"), mistake.detail());
        assertEquals(List.of(), log.calls());
        assertEquals(List.of(), sink.failures());
    }
}
