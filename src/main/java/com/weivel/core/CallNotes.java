package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The failures the calls of one hold of the layer lock met, taken before the lock is let go and told to the error
 * sink after.
 *
 * @implNote One per wrapper, guarded by the layer lock, and its list is made only once a failure is noted, so a call
 * that meets none allocates nothing here.
 */
final class CallNotes {
    private List<Failure> failures;

    void note(FailureKind kind, String text, int eventKind, long sequence, Optional<Throwable> cause) {
        if (failures == null) {
            failures = new ArrayList<>();
        }
        failures.add(new Failure(kind, text, eventKind, sequence, cause));
    }

    void noteDefect(String text, Optional<Throwable> cause) {
        note(FailureKind.LAYER_DEFECT, text, 0, 0, cause);
    }

    /** The failures noted since the last time they were taken, which are noted no more. */
    List<Failure> taken() {
        if (failures == null) {
            return Collections.emptyList();
        }
        List<Failure> noted = failures;
        failures = null;
        return noted;
    }
}
