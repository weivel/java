package com.weivel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The failures one call met under the layer lock, told to the error sink once the lock is let go. */
final class CallNotes {
    private final List<Failure> failures = new ArrayList<>();

    void note(FailureKind kind, String text, int eventKind, long sequence, Optional<Throwable> cause) {
        failures.add(new Failure(kind, text, eventKind, sequence, cause));
    }

    void noteDefect(String text, Optional<Throwable> cause) {
        note(FailureKind.LAYER_DEFECT, text, 0, 0, cause);
    }

    List<Failure> failures() {
        return failures;
    }
}
