package com.weivel.context;

import static com.weivel.context.ContextFixture.capabilities;
import static com.weivel.context.ContextFixture.value;
import static com.weivel.core.CoreProbes.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.weivel.core.Answer;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreConstants;
import com.weivel.core.FailureKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** A plugin's capabilities: provided, listed in the core's order and watched joining and leaving. */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class CapabilitiesTest {
    private static final String SCREENS = "screens";

    private final ContextFixture fixture = new ContextFixture();
    private final PluginContext consumer = fixture.opened(capabilities("consumer", List.of(), List.of(SCREENS)));

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void theImplementationsListedAreTheProvidersOwnObjectsInTheOrderTheCoreAnswers() {
        PluginContext first = fixture.opened(capabilities("first", List.of(SCREENS), List.of()));
        PluginContext second = fixture.opened(capabilities("second", List.of(SCREENS), List.of()));
        Object firstScreen = new Object();
        Object secondScreen = new Object();
        value(first.provide(SCREENS, firstScreen));
        value(second.provide(SCREENS, secondScreen));
        fixture.verify(second);
        fixture.verify(first);

        List<Object> listed = value(consumer.implementations(SCREENS));

        assertEquals(2, listed.size());
        assertSame(secondScreen, listed.get(0));
        assertSame(firstScreen, listed.get(1));
        assertEquals(List.of(), fixture.failures);
    }

    @Test
    void anImplementationReachesNoConsumerBeforeItsProviderIsVerified() {
        PluginContext provider = fixture.opened(capabilities("provider", List.of(SCREENS), List.of()));
        value(provider.provide(SCREENS, new Object()));

        assertEquals(List.of(), value(consumer.implementations(SCREENS)));
    }

    @Test
    void aListingTheCoreRefusesIsThatRefusalNeverAnEmptyList() {
        Answer.Refused<List<Object>> refused = assertInstanceOf(Answer.Refused.class,
                consumer.implementations("undeclared"));

        assertEquals(CoreConstants.REFUSAL_UNDECLARED, refused.refusal().orElseThrow().code());
    }

    @Test
    void aWatchHearsAnImplementationJoinAndTheVeryImplementationLeave() {
        PluginContext provider = fixture.opened(capabilities("provider", List.of(SCREENS), List.of()));
        List<Object> heard = new ArrayList<>();
        value(consumer.watchCapability(SCREENS, recording(heard)));
        Object screen = new Object();
        value(provider.provide(SCREENS, screen));

        fixture.verify(provider);
        fixture.release(provider);

        assertEquals(List.of("provided", screen, "withdrawn", screen), heard);
        assertSame(screen, heard.get(1));
        assertSame(screen, heard.get(3));
    }

    @Test
    void anImplementationAWatchOrAListingHandedOverIsRevokedWithItsObjectBeforeTheWatchHearsItLeave() {
        PluginContext provider = fixture.opened(capabilities("provider", List.of(SCREENS), List.of()));
        PluginContext lister = fixture.opened(capabilities("lister", List.of(), List.of(SCREENS)));
        List<Object> heard = new ArrayList<>();
        value(consumer.watchCapability(SCREENS, recording(heard)));
        Object screen = new Object();
        value(provider.provide(SCREENS, screen));
        fixture.verify(provider);
        value(lister.implementations(SCREENS));
        consumer.listen(event -> heard.add(event));
        List<PluginEvent> listerHeard = new ArrayList<>();
        lister.listen(listerHeard::add);

        fixture.release(provider);

        assertSame(screen, assertInstanceOf(PluginEvent.Revoked.class, heard.get(2)).object());
        assertEquals(List.of("withdrawn", screen), heard.subList(3, 5));
        assertSame(screen, assertInstanceOf(PluginEvent.Revoked.class, listerHeard.getFirst()).object());
    }

    @Test
    void aCapabilityWatchTheCoreRefusesIsThatRefusal() {
        assertInstanceOf(Answer.Refused.class, consumer.watchCapability("undeclared", recording(new ArrayList<>())));
    }

    @Test
    void anImplementationTheLayerHoldsNoObjectForIsLeftOutAndTheSinkIsTold() {
        long stranger = ok(fixture.core.call(CoreCalls.registerManifest(capabilities("stranger", List.of(SCREENS),
                List.of()))));
        ok(fixture.core.call(CoreCalls.openPlugin(stranger, 0)));
        ok(fixture.core.call(CoreCalls.provideCapability(stranger, SCREENS)));
        ok(fixture.core.call(CoreCalls.verifyActivation(stranger)));

        assertEquals(List.of(), value(consumer.implementations(SCREENS)));
        assertEquals(1, fixture.failuresOf(FailureKind.OBJECT_UNKNOWN).size());
    }

    @Test
    void aListingLongerThanTheRoomFirstTakenIsAnsweredWhole() {
        List<Object> provided = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            PluginContext provider = fixture.opened(capabilities("provider" + index, List.of(SCREENS), List.of()));
            Object screen = new Object();
            value(provider.provide(SCREENS, screen));
            fixture.verify(provider);
            provided.add(screen);
        }

        List<Object> listed = value(consumer.implementations(SCREENS));

        assertEquals(provided.size(), listed.size());
        for (int index = 0; index < provided.size(); index++) {
            assertSame(provided.get(index), listed.get(index));
        }
    }

    static CapabilityWatcher recording(List<Object> heard) {
        return new CapabilityWatcher() {
            @Override
            public void provided(Object implementation) {
                heard.add("provided");
                heard.add(implementation);
            }

            @Override
            public void withdrawn(Object implementation) {
                heard.add("withdrawn");
                heard.add(implementation);
            }
        };
    }
}
