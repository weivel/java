package com.weivel.benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.CoreBinary;
import com.weivel.core.CoreLoad;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The benchmark gates on the real core and the core's echo library, run by the {@code benchmark} task alone. In the
 * measuring mode each repetition prints one {@code weivel-benchmark} line per gate and no timing is judged; in the
 * gating mode one repetition is judged against {@code benchmarks/java.txt} of the pinned kit. The identity gate holds
 * in both.
 */
@Tag("benchmark")
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class BenchmarksTest {
    private static final String MEASURE = "measure";
    private static final String GATE = "gate";

    private static CoreBinary binary;

    @BeforeAll
    static void loadTheCore() {
        binary = assertInstanceOf(CoreLoad.Loaded.class, CoreBinary.load(existing("weivel.test.coreBinary")))
                .binary();
    }

    @AfterAll
    static void closeTheCore() {
        binary.close();
    }

    @Test
    void runsOnTheBackendTheBuildChose() {
        assertEquals(System.getProperty("weivel.test.backend"), binary.backend());
    }

    @Test
    void answersAConsumerTheProvidersOwnObject() {
        SameLanguageBenchmark.Resolution resolution = SameLanguageBenchmark.resolve(binary);

        assertSame(resolution.provided(), resolution.resolved());
    }

    @Test
    void timesTheSameLanguageCallWithinItsNoise() {
        for (int repetition = 1; repetition <= repetitions(); repetition++) {
            SameLanguageMeasurement measured = SameLanguageBenchmark.measure(binary,
                    SameLanguageBenchmark.DEFAULT_SAMPLING);
            double noise = Gates.deviation(Summary.of(measured.noiseRatios()).median());
            double deviation = Gates.deviation(Summary.of(measured.sameLanguageRatios()).median());
            report("same-language", repetition, String.format(Locale.ROOT,
                    "noise=%.6f deviation=%.6f direct_ns=%.4f direct_again_ns=%.4f through_weivel_ns=%.4f "
                            + "noise_rounds_over_5pct=%d", noise, deviation, measured.direct().median(),
                    measured.directAgain().median(), measured.throughWeivel().median(),
                    roundsOver(measured.noiseRatios(), 0.05)));
            if (isGating()) {
                assertPassed(Gates.sameLanguage(budget(), KitPlatform.current(), measured.noiseRatios(),
                        measured.sameLanguageRatios()));
            }
        }
    }

    @Test
    void timesTheControlCallWithinItsBound() {
        for (int repetition = 1; repetition <= repetitions(); repetition++) {
            PairMeasurement measured = ControlCallBenchmark.measure(binary, ControlCallBenchmark.DEFAULT_SAMPLING);
            judged(RatioGate.CONTROL_CALL, repetition, measured);
        }
    }

    @Test
    void timesTheLibraryCallWithinItsBound() {
        Path echo = existing("weivel.test.echoBinary");
        for (int repetition = 1; repetition <= repetitions(); repetition++) {
            PairMeasurement measured = LibraryCallBenchmark.measure(binary, echo,
                    LibraryCallBenchmark.DEFAULT_SAMPLING);
            judged(RatioGate.LIBRARY_CALL, repetition, measured);
        }
    }

    @Test
    void timesTheDefinitionCallWithinItsNoise() {
        Path echo = existing("weivel.test.echoBinary");
        for (int repetition = 1; repetition <= repetitions(); repetition++) {
            SameLanguageMeasurement measured = DefinitionCallBenchmark.measure(binary, echo,
                    DefinitionCallBenchmark.DEFAULT_SAMPLING);
            double noise = Gates.deviation(Summary.of(measured.noiseRatios()).median());
            double deviation = Gates.deviation(Summary.of(measured.sameLanguageRatios()).median());
            report(RatioGate.LIBRARY_CALL.kitNameFor(binary.backend()) + "-definition", repetition,
                    String.format(Locale.ROOT, "noise=%.6f deviation=%.6f direct_ns=%.4f direct_again_ns=%.4f "
                            + "through_weivel_ns=%.4f", noise, deviation, measured.direct().median(),
                            measured.directAgain().median(), measured.throughWeivel().median()));
            if (isGating()) {
                assertPassed(Gates.sameLanguage(budget(), KitPlatform.current(), measured.noiseRatios(),
                        measured.sameLanguageRatios()));
            }
        }
    }

    private static long roundsOver(double[] ratios, double deviation) {
        long over = 0;
        for (double ratio : ratios) {
            if (Gates.deviation(ratio) > deviation) {
                over++;
            }
        }
        return over;
    }

    private static void judged(RatioGate gate, int repetition, PairMeasurement measured) {
        double ratio = Summary.of(measured.roundRatios()).median();
        report(gate.kitNameFor(binary.backend()), repetition, String.format(Locale.ROOT,
                "ratio=%.4f bare_median_ns=%.2f weivel_median_ns=%.2f", ratio, measured.bare().median(),
                measured.weivel().median()));
        if (isGating()) {
            assertPassed(Gates.ratio(budget(), gate, binary.backend(), KitPlatform.current(),
                    measured.roundRatios()));
        }
    }

    private static void report(String gate, int repetition, String measured) {
        System.out.println("weivel-benchmark gate=" + gate + " backend=" + binary.backend() + " platform="
                + KitPlatform.current().orElse("none") + " java=" + System.getProperty("java.version")
                + " repetition=" + repetition + " " + measured);
    }

    private static void assertPassed(Verdict verdict) {
        assertTrue(verdict.passed(), verdict.reason());
    }

    private static boolean isGating() {
        String mode = System.getProperty("weivel.benchmark.mode", GATE);
        if (!mode.equals(MEASURE) && !mode.equals(GATE)) {
            throw new IllegalStateException("weivel.benchmark.mode is " + mode + ", neither " + MEASURE + " nor "
                    + GATE);
        }
        return mode.equals(GATE);
    }

    private static int repetitions() {
        return isGating() ? 1 : Integer.parseInt(System.getProperty("weivel.benchmark.repetitions", "1"));
    }

    private static LanguageBudget budget() {
        return LanguageBudget.read(existing("weivel.benchmark.budget"));
    }

    private static Path existing(String property) {
        String location = Optional.ofNullable(System.getProperty(property)).orElseThrow(() ->
                new IllegalStateException(property + " is not set. Run the benchmarks through Gradle, which sets it."));
        Path path = Paths.get(location);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(property + " names " + path + ", which does not exist, so no gate can "
                    + "be judged and every one fails.");
        }
        return path;
    }
}
