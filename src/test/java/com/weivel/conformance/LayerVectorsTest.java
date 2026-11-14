package com.weivel.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.weivel.context.LogRecord;
import com.weivel.core.CoreConstants;
import com.weivel.core.Failure;
import com.weivel.core.FailureKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    @DisplayName("lifecycle/a-release-deactivates-then-releases-then-forgets")
    void aReleaseDeactivatesThenReleasesThenForgets() {
        assertWitnessedQuietly("lifecycle/a-release-deactivates-then-releases-then-forgets");
    }

    @Test
    @DisplayName("lifecycle/destroy-releases-a-plugin-whose-activate-is-in-flight")
    void destroyReleasesAPluginWhoseActivateIsInFlight() {
        assertWitnessedQuietly("lifecycle/destroy-releases-a-plugin-whose-activate-is-in-flight");
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
