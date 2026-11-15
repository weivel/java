package com.weivel.benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The gates judged on given bounds and given samples, so each refusal is seen without a live run. */
class GatesTest {
    private static final LanguageBudget BUDGET = LanguageBudget.parse(LanguageBudgetTest.BUDGET);
    private static final Optional<String> LINUX = Optional.of("linux");

    @Test
    void passesARatioAtOrUnderItsBoundOnItsBackend() {
        assertTrue(Gates.ratio(BUDGET, RatioGate.CONTROL_CALL, "ffm", LINUX, new double[] {4.0, 5.0, 4.5}).passed());
        assertTrue(Gates.ratio(BUDGET, RatioGate.CONTROL_CALL, "jna", LINUX, new double[] {17.0}).passed());
    }

    @Test
    void failsARatioOverItsBoundOnItsBackend() {
        Verdict verdict = Gates.ratio(BUDGET, RatioGate.CONTROL_CALL, "ffm", LINUX, new double[] {5.5, 5.1, 6.0});

        assertFalse(verdict.passed());
        assertTrue(verdict.reason().contains("control-call-ffm on linux ran 5.500000"), verdict.reason());
        assertFalse(Gates.ratio(BUDGET, RatioGate.LIBRARY_CALL, "jna", LINUX, new double[] {8.1}).passed());
    }

    @Test
    void judgesTheMedianOfTheRoundsSoOneSlowRoundDoesNotFail() {
        assertTrue(Gates.ratio(BUDGET, RatioGate.LIBRARY_CALL, "ffm", LINUX, new double[] {2.0, 2.9, 40.0})
                .passed());
        assertFalse(Gates.ratio(BUDGET, RatioGate.LIBRARY_CALL, "ffm", LINUX, new double[] {2.0, 3.1, 40.0})
                .passed());
    }

    @Test
    void failsAPlatformTheBudgetHasNoLineFor() {
        LanguageBudget withoutMacos = LanguageBudget.parse(LanguageBudgetTest.BUDGET
                .replace("library-call-jna macos 5\n", ""));

        Verdict verdict = Gates.ratio(withoutMacos, RatioGate.LIBRARY_CALL, "jna", Optional.of("macos"),
                new double[] {1.0});

        assertFalse(verdict.passed());
        assertTrue(verdict.reason().contains("no CI run has measured library-call-jna on macos"), verdict.reason());
        assertTrue(Gates.ratio(withoutMacos, RatioGate.LIBRARY_CALL, "ffm", Optional.of("macos"),
                new double[] {1.0}).passed());
    }

    @Test
    void failsASystemThatIsNoKitPlatform() {
        assertFalse(Gates.ratio(BUDGET, RatioGate.CONTROL_CALL, "ffm", Optional.<String>empty(), new double[] {1.0})
                .passed());
        assertFalse(Gates.sameLanguage(BUDGET, Optional.<String>empty(), new double[] {1.0}, new double[] {1.0})
                .passed());
    }

    @Test
    void failsARunWithNothingMeasured() {
        Verdict ratio = Gates.ratio(BUDGET, RatioGate.CONTROL_CALL, "ffm", LINUX, new double[0]);
        Verdict noNoise = Gates.sameLanguage(BUDGET, LINUX, new double[0], new double[] {1.0});
        Verdict noPair = Gates.sameLanguage(BUDGET, LINUX, new double[] {1.0}, new double[0]);

        assertFalse(ratio.passed());
        assertTrue(ratio.reason().contains("nothing was measured"), ratio.reason());
        assertFalse(noNoise.passed());
        assertFalse(noPair.passed());
    }

    @Test
    void failsTheSameLanguagePairWhereTheBudgetHasNoNoiseLine() {
        LanguageBudget withoutNoise = LanguageBudget.parse(LanguageBudgetTest.BUDGET
                .replace("quiet-noise linux 0.01\n", ""));

        Verdict verdict = Gates.sameLanguage(withoutNoise, LINUX, new double[] {1.0}, new double[] {1.0});

        assertFalse(verdict.passed());
        assertTrue(verdict.reason().contains("no noise line for linux"), verdict.reason());
    }

    @Test
    void refusesARunNoisierThanThePlatformsWorstNoise() {
        assertTrue(Gates.sameLanguage(BUDGET, LINUX, new double[] {1.099}, new double[] {1.0}).passed());

        Verdict noisy = Gates.sameLanguage(BUDGET, LINUX, new double[] {1.101}, new double[] {1.0});
        Verdict noisyTheOtherWay = Gates.sameLanguage(BUDGET, LINUX, new double[] {1 / 1.101}, new double[] {1.0});

        assertFalse(noisy.passed());
        assertTrue(noisy.reason().contains("too noisy to judge"), noisy.reason());
        assertFalse(noisyTheOtherWay.passed());
    }

    @Test
    void holdsTheSameLanguagePairToTheMarginTimesTheQuietNoiseOnAQuietRun() {
        assertTrue(Gates.sameLanguage(BUDGET, LINUX, new double[] {1.0}, new double[] {1.039}).passed());
        assertFalse(Gates.sameLanguage(BUDGET, LINUX, new double[] {1.0}, new double[] {1.041}).passed());
        assertFalse(Gates.sameLanguage(BUDGET, LINUX, new double[] {1.0}, new double[] {1 / 1.041}).passed());
    }

    @Test
    void widensTheSameLanguageBoundToANoisyRunsOwnNoise() {
        assertTrue(Gates.sameLanguage(BUDGET, LINUX, new double[] {1.05}, new double[] {1.19}).passed());
        assertFalse(Gates.sameLanguage(BUDGET, LINUX, new double[] {1.05}, new double[] {1.21}).passed());
    }

    @Test
    void measuresDeviationTheSameEitherWay() {
        assertEquals(0.25, Gates.deviation(1.25), 1e-12);
        assertEquals(0.25, Gates.deviation(1 / 1.25), 1e-12);
        assertEquals(0.0, Gates.deviation(1.0), 0.0);
    }
}
