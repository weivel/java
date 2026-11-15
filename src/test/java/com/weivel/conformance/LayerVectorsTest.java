package com.weivel.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.weivel.context.LogRecord;
import com.weivel.core.CoreConstants;
import com.weivel.core.Failure;
import com.weivel.core.FailureKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * One test per layer vector of the pinned kit, each named by the vector's id and witnessing what the kit's
 * {@code LAYER.md} states for it, through the host's public surface on the real core.
 *
 * @implNote Each runs on a thread of its own under a timeout, so a layer that waits where a vector says it must not
 * fails the test rather than hanging the build.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class LayerVectorsTest {
    @Test
    @DisplayName("consume/a-throwing-listener-does-not-silence-the-others")
    void aThrowingListenerDoesNotSilenceTheOthers() {
        LayerVector vector = KitFixture.vector("consume/a-throwing-listener-does-not-silence-the-others");

        LayerWitness witness = KitFixture.witness(vector);

        assertEquals(vector.then(), witness.observed());
        assertEquals(Collections.emptyList(), errorLogs(witness));
        List<String> sunk = new ArrayList<>();
        for (Failure failure : witness.failures()) {
            assertEquals(FailureKind.PLUGIN_CALLBACK, failure.kind());
            sunk.add(failure.cause().get().getMessage());
        }
        assertEquals(Collections.singletonList("the throwing listener of screens was handed calendar provide"), sunk);
    }

    @Test
    @DisplayName("consume/a-refused-verify-quarantines-the-plugin")
    void aRefusedVerifyQuarantinesThePlugin() {
        LayerVector vector = KitFixture.vector("consume/a-refused-verify-quarantines-the-plugin");

        LayerWitness witness = KitFixture.witness(vector);

        assertEquals(vector.then(), witness.observed());
        assertEquals(Collections.<Failure>emptyList(), witness.failures());
    }

    @Test
    @DisplayName("want/a-registration-at-the-deadline-loses")
    void aRegistrationAtTheDeadlineLoses() {
        assertWitnessedQuietly("want/a-registration-at-the-deadline-loses");
    }

    @Test
    @DisplayName("want/a-timed-want-arms-the-timer-at-its-deadline")
    void aTimedWantArmsTheTimerAtItsDeadline() {
        assertWitnessedQuietly("want/a-timed-want-arms-the-timer-at-its-deadline");
    }

    @Test
    @DisplayName("want/a-settled-want-disarms-the-timer")
    void aSettledWantDisarmsTheTimer() {
        assertWitnessedQuietly("want/a-settled-want-disarms-the-timer");
    }

    @Test
    @DisplayName("want/a-registration-past-an-unfired-deadline-loses")
    void aRegistrationPastAnUnfiredDeadlineLoses() {
        assertWitnessedQuietly("want/a-registration-past-an-unfired-deadline-loses");
    }

    @Test
    @DisplayName("lifecycle/an-awaited-want-does-not-hold-back-a-later-plugin")
    void anAwaitedWantDoesNotHoldBackALaterPlugin() {
        assertWitnessedQuietly("lifecycle/an-awaited-want-does-not-hold-back-a-later-plugin");
    }

    @Test
    @DisplayName("lifecycle/events-of-an-activate-wait-for-every-activate-of-the-plan")
    void eventsOfAnActivateWaitForEveryActivateOfThePlan() {
        assertWitnessedQuietly("lifecycle/events-of-an-activate-wait-for-every-activate-of-the-plan");
    }

    @Test
    @DisplayName("lifecycle/an-activate-that-settles-at-once-is-verified-before-the-next-starts")
    void anActivateThatSettlesAtOnceIsVerifiedBeforeTheNextStarts() {
        assertWitnessedQuietly("lifecycle/an-activate-that-settles-at-once-is-verified-before-the-next-starts");
    }

    @Test
    @DisplayName("lifecycle/an-activate-refused-at-once-is-quarantined-before-the-next-starts")
    void anActivateRefusedAtOnceIsQuarantinedBeforeTheNextStarts() {
        assertWitnessedQuietly("lifecycle/an-activate-refused-at-once-is-quarantined-before-the-next-starts");
    }

    @Test
    @DisplayName("lifecycle/a-release-deactivates-then-releases-then-forgets")
    void aReleaseDeactivatesThenReleasesThenForgets() {
        assertWitnessedQuietly("lifecycle/a-release-deactivates-then-releases-then-forgets");
    }

    @Test
    @DisplayName("lifecycle/destroy-releases-a-plugin-whose-activate-is-in-flight")
    void destroyReleasesAPluginWhoseActivateIsInFlight() {
        assertWitnessedQuietly("lifecycle/destroy-releases-a-plugin-whose-activate-is-in-flight");
    }

    @Test
    @DisplayName("library/a-definition-is-the-providers-own-pointer")
    void aDefinitionIsTheProvidersOwnPointer() {
        assertWitnessedQuietly("library/a-definition-is-the-providers-own-pointer");
    }

    @Test
    @DisplayName("library/a-null-definition-fails-the-load")
    void aNullDefinitionFailsTheLoad() {
        assertWitnessedQuietly("library/a-null-definition-fails-the-load");
    }

    @Test
    @DisplayName("library/a-definition-below-its-size-fails-the-load")
    void aDefinitionBelowItsSizeFailsTheLoad() {
        assertWitnessedQuietly("library/a-definition-below-its-size-fails-the-load");
    }

    @Test
    @DisplayName("library/a-definition-at-its-size-loads")
    void aDefinitionAtItsSizeLoads() {
        assertWitnessedQuietly("library/a-definition-at-its-size-loads");
    }

    @Test
    @DisplayName("library/an-unload-revokes-every-holding-of-a-definition")
    void anUnloadRevokesEveryHoldingOfADefinition() {
        assertWitnessedQuietly("library/an-unload-revokes-every-holding-of-a-definition");
    }

    @Test
    @DisplayName("library/a-binary-stays-open-while-a-holding-lives")
    void aBinaryStaysOpenWhileAHoldingLives() {
        assertWitnessedQuietly("library/a-binary-stays-open-while-a-holding-lives");
    }

    @Test
    @DisplayName("library/a-binary-closes-once-every-definition-service-is-released")
    void aBinaryClosesOnceEveryDefinitionServiceIsReleased() {
        assertWitnessedQuietly("library/a-binary-closes-once-every-definition-service-is-released");
    }

    @Test
    @DisplayName("library/a-binary-with-no-holding-closes-at-the-unload")
    void aBinaryWithNoHoldingClosesAtTheUnload() {
        assertWitnessedQuietly("library/a-binary-with-no-holding-closes-at-the-unload");
    }

    @Test
    @DisplayName("library/a-holding-ended-with-its-holder-closes-the-binary")
    void aHoldingEndedWithItsHolderClosesTheBinary() {
        assertWitnessedQuietly("library/a-holding-ended-with-its-holder-closes-the-binary");
    }

    @Test
    @DisplayName("library/an-owner-hears-the-release-its-own-release-queued")
    void anOwnerHearsTheReleaseItsOwnReleaseQueued() {
        assertWitnessedQuietly("library/an-owner-hears-the-release-its-own-release-queued");
    }

    @Test
    @DisplayName("library/a-release-event-after-its-owner-was-quarantined-reaches-no-one")
    void aReleaseEventAfterItsOwnerWasQuarantinedReachesNoOne() {
        assertWitnessedQuietly("library/a-release-event-after-its-owner-was-quarantined-reaches-no-one");
    }

    @Test
    @DisplayName("library/a-forgotten-holder-ends-its-late-holding")
    void aForgottenHolderEndsItsLateHolding() {
        assertWitnessedQuietly("library/a-forgotten-holder-ends-its-late-holding");
    }

    /**
     * The held-bytes vector as this layer witnesses it: every bytes answer is copied once into a Java array and released
     * before the call answers, as a collected language's must be, so no bytes are held after the call, and the binary
     * closes at the last definition release instead of at the drop, which lets go of nothing. Holding the binary open
     * while a bytes call still runs in it is {@code LibrariesTest}'s to show.
     */
    @Test
    @DisplayName("library/a-held-bytes-answer-keeps-the-binary-open-past-the-last-holding")
    void aHeldBytesAnswerKeepsTheBinaryOpenPastTheLastHolding() {
        String id = "library/a-held-bytes-answer-keeps-the-binary-open-past-the-last-holding";
        LayerVector vector = KitFixture.vector(id);
        Map<String, Object> copyingThen = new LinkedHashMap<>(vector.then());
        copyingThen.put("unloads", Collections.singletonMap("equals", Arrays.asList(0L, 1L, 1L)));

        LayerWitness witness = KitFixture.witness(vector);

        assertEquals(Collections.singletonMap("equals", Arrays.asList(0L, 0L, 1L)), vector.then().get("unloads"));
        assertEquals(copyingThen, witness.observed());
        assertEquals(Collections.<Failure>emptyList(), witness.failures());
        assertEquals(Collections.emptyList(), errorLogs(witness));
    }

    private static void assertWitnessedQuietly(String id) {
        LayerVector vector = KitFixture.vector(id);

        LayerWitness witness = KitFixture.witness(vector);

        assertEquals(vector.then(), witness.observed());
        assertEquals(Collections.<Failure>emptyList(), witness.failures());
        assertEquals(Collections.emptyList(), errorLogs(witness));
        assertEquals(id, witness.id());
    }

    private static List<String> errorLogs(LayerWitness witness) {
        List<String> errors = new ArrayList<>();
        for (LogRecord record : witness.logs()) {
            if (record.level() == CoreConstants.LOG_ERROR) {
                errors.add(record.text());
            }
        }
        return errors;
    }
}
