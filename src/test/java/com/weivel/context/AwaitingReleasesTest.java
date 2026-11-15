package com.weivel.context;

import static com.weivel.context.ContextFixture.consuming;
import static com.weivel.context.ContextFixture.providing;
import static com.weivel.context.ContextFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Registrations awaiting releases: which holding a revocation lets go of, and what a forget drops. */
class AwaitingReleasesTest {
    private static final String ONE = "provider:one";
    private static final String TWO = "provider:two";
    private static final String FEED = "provider:feed";

    @Test
    void anObjectUnderTwoRegistrationsIsLetGoOfUnderTheOneItsRevocationNamesTheSecondFirst() {
        assertLetGoOfInTurn(1, 0);
    }

    @Test
    void anObjectUnderTwoRegistrationsIsLetGoOfUnderTheOneItsRevocationNamesTheFirstFirst() {
        assertLetGoOfInTurn(0, 1);
    }

    @Test
    void aForgottenPluginsRevokedHoldingIsDroppedWithIt() {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", FEED));
            PluginContext consumer = fixture.opened(consuming("consumer", FEED));
            List<PluginEvent> heard = heardBy(provider);
            fixture.release(consumer);
            Object feed = new Object();
            ServiceRegistration registration = value(provider.registerAwaitingReleases(FEED, feed));
            assertSame(feed, value(consumer.get(FEED)));
            value(registration.unregister());
            int keptBeforeTheForget = fixture.contexts.revokedHoldingsKept();

            value(fixture.contexts.forget(consumer));

            assertEquals(1, keptBeforeTheForget);
            assertEquals(0, fixture.contexts.revokedHoldingsKept());
            assertEquals(Collections.<PluginEvent>singletonList(new PluginEvent.HoldingsReleased(FEED, feed)), heard);
        }
    }

    /** One object registered under two ids awaiting releases, each revocation let go of in the order given. */
    private static void assertLetGoOfInTurn(int first, int second) {
        try (ContextFixture fixture = new ContextFixture()) {
            PluginContext provider = fixture.opened(providing("provider", ONE, TWO));
            PluginContext consumer = fixture.opened(consuming("consumer", ONE, TWO));
            List<PluginEvent> heard = heardBy(provider);
            List<PluginEvent.Revoked> revoked = revocationsHeardBy(consumer);
            Object shared = new Object();
            List<ServiceRegistration> registrations = Arrays.asList(
                    value(provider.registerAwaitingReleases(ONE, shared)),
                    value(provider.registerAwaitingReleases(TWO, shared)));
            value(consumer.get(ONE));
            value(consumer.get(TWO));
            value(registrations.get(0).unregister());
            value(registrations.get(1).unregister());
            List<String> ids = Arrays.asList(ONE, TWO);

            assertTrue(value(consumer.releaseHolding(revoked.get(first))));
            List<PluginEvent> heardAfterTheFirst = new ArrayList<>(heard);
            assertTrue(value(consumer.releaseHolding(revoked.get(second))));

            assertEquals(2, revoked.size());
            assertEquals(Collections.<PluginEvent>singletonList(new PluginEvent.HoldingsReleased(ids.get(first),
                    shared)), heardAfterTheFirst);
            assertEquals(Arrays.<PluginEvent>asList(new PluginEvent.HoldingsReleased(ids.get(first), shared),
                    new PluginEvent.HoldingsReleased(ids.get(second), shared)), heard);
        }
    }

    private static List<PluginEvent> heardBy(PluginContext plugin) {
        List<PluginEvent> heard = Collections.synchronizedList(new ArrayList<>());
        plugin.listen(event -> {
            if (event instanceof PluginEvent.HoldingsReleased) {
                heard.add(event);
            }
        });
        return heard;
    }

    private static List<PluginEvent.Revoked> revocationsHeardBy(PluginContext plugin) {
        List<PluginEvent.Revoked> revoked = Collections.synchronizedList(new ArrayList<>());
        plugin.listen(event -> {
            if (event instanceof PluginEvent.Revoked) {
                revoked.add((PluginEvent.Revoked) event);
            }
        });
        return revoked;
    }
}
