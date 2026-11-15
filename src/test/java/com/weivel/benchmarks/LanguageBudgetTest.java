package com.weivel.benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@code benchmarks/java.txt} read as the kit's RUNNER.md lays it out, with a gate per backend. */
class LanguageBudgetTest {
    static final String BUDGET = String.join("\n",
            "control-call-ffm linux 2.5",
            "control-call-ffm windows 2.25",
            "control-call-ffm macos 2",
            "control-call-jna linux 9",
            "control-call-jna windows 8",
            "control-call-jna macos 7",
            "library-call-ffm linux 1.5",
            "library-call-ffm windows 1.25",
            "library-call-ffm macos 1.75",
            "library-call-jna linux 4",
            "library-call-jna windows 3",
            "library-call-jna macos 5",
            "ratio-margin 2",
            "quiet-noise linux 0.01",
            "quiet-noise windows 0.02",
            "quiet-noise macos 0.03",
            "worst-noise linux 0.1",
            "worst-noise windows 0.2",
            "worst-noise macos 0.3",
            "noise-margin linux 4",
            "noise-margin windows 5",
            "noise-margin macos 6",
            "");

    @Test
    void boundsEachGateOfEachBackendAtItsHighestRatioTimesTheMargin() {
        LanguageBudget budget = LanguageBudget.parse(BUDGET);

        assertEquals(5.0, budget.ratioBound(RatioGate.CONTROL_CALL, "ffm", "linux").getAsDouble());
        assertEquals(16.0, budget.ratioBound(RatioGate.CONTROL_CALL, "jna", "windows").getAsDouble());
        assertEquals(3.5, budget.ratioBound(RatioGate.LIBRARY_CALL, "ffm", "macos").getAsDouble());
        assertEquals(10.0, budget.ratioBound(RatioGate.LIBRARY_CALL, "jna", "macos").getAsDouble());
        assertEquals(2.0, budget.ratioMargin());
    }

    @Test
    void givesNoBoundForABackendOrPlatformTheBudgetHasNoLineFor() {
        LanguageBudget budget = LanguageBudget.parse(BUDGET.replace("control-call-jna macos 7\n", ""));

        assertFalse(budget.ratioBound(RatioGate.CONTROL_CALL, "jna", "macos").isPresent());
        assertTrue(budget.ratioBound(RatioGate.CONTROL_CALL, "ffm", "macos").isPresent());
        assertFalse(budget.ratioBound(RatioGate.CONTROL_CALL, "graal", "linux").isPresent());
        assertFalse(budget.ratioBound(RatioGate.CONTROL_CALL, "ffm", "freebsd").isPresent());
    }

    @Test
    void readsEachPlatformsNoiseAndNoiseMargin() {
        LanguageBudget budget = LanguageBudget.parse(BUDGET);

        assertEquals(Optional.of(new NoiseBounds(0.02, 0.2, 5.0)), budget.noiseBounds("windows"));
        assertEquals(Optional.of(new NoiseBounds(0.03, 0.3, 6.0)), budget.noiseBounds("macos"));
        assertFalse(LanguageBudget.parse(BUDGET.replace("worst-noise macos 0.3\n", "")).noiseBounds("macos")
                .isPresent());
        assertFalse(LanguageBudget.parse(BUDGET.replace("noise-margin macos 6\n", "")).noiseBounds("macos")
                .isPresent());
        assertFalse(budget.noiseBounds("freebsd").isPresent());
    }

    @Test
    void refusesARatioLineNamingNoBackend() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> LanguageBudget.parse(BUDGET + "control-call linux 1.2\n"));

        assertTrue(refused.getMessage().contains("neither a gate of a backend"), refused.getMessage());
    }

    @Test
    void refusesALineTheFormatDoesNotDefine() {
        assertThrows(IllegalArgumentException.class, () -> LanguageBudget.parse(BUDGET + "dispatch-ffm linux 3\n"));
        assertThrows(IllegalArgumentException.class, () -> LanguageBudget.parse(BUDGET + "control-call-ffm bsd 3\n"));
        assertThrows(IllegalArgumentException.class, () -> LanguageBudget.parse(BUDGET + "noise-margin bsd 4\n"));
    }

    @Test
    void refusesANoiseMarginGivenForEveryPlatformAtOnce() {
        String scalar = BUDGET.replace("noise-margin linux 4\nnoise-margin windows 5\nnoise-margin macos 6\n",
                "noise-margin 4\n");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> LanguageBudget.parse(scalar));

        assertTrue(refused.getMessage().contains("\"noise-margin 4\""), refused.getMessage());
    }

    @Test
    void refusesANumberThatIsNotPositive() {
        for (String number : new String[] {"0", "-1", "many", "Infinity", "NaN"}) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> LanguageBudget.parse(BUDGET.replace("ratio-margin 2", "ratio-margin " + number)));
            assertTrue(refused.getMessage().contains("not a positive number"), refused.getMessage());
        }
    }

    @Test
    void refusesALineGivenTwice() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> LanguageBudget.parse(BUDGET + "library-call-jna linux 4\n"));

        assertTrue(refused.getMessage().contains("twice"), refused.getMessage());
    }

    @Test
    void refusesABudgetMissingAMargin() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> LanguageBudget.parse(BUDGET.replace("ratio-margin 2\n", "")));

        assertTrue(refused.getMessage().contains("names no ratio-margin"), refused.getMessage());
    }
}
