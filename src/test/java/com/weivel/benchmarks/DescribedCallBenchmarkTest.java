package com.weivel.benchmarks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.weivel.core.CoreBinary;
import com.weivel.core.CoreLoad;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The described call's paths run outside the timed gate, so the suite on each backend proves both answer the sample
 * they were handed, the layout declared by hand matches the size the library compiled, and both make the library
 * release the same things once per call, as the library counts its own releases.
 */
class DescribedCallBenchmarkTest {
    private static final int CALLS = 25;

    private CoreBinary binary;

    @BeforeEach
    void loadTheCore() {
        binary = assertInstanceOf(CoreLoad.Loaded.class, CoreBinary.load(file("weivel.test.coreBinary"))).binary();
    }

    @AfterEach
    void closeTheCore() {
        binary.close();
    }

    @Test
    void bothPathsAnswerTheSampleTheyWereHandedOnThisBackend() {
        DescribedCallMeasurement measured = DescribedCallBenchmark.measure(binary, file("weivel.test.standardBinary"),
                file("weivel.test.describedBinary"), file("weivel.test.echoBinary"), new Sampling(0, 1, 2));

        assertEquals(2, measured.pair().roundRatios().length);
    }

    @Test
    void bothPathsReleaseOneReflectionACallAndNoEmptyRefusal() {
        Map<String, long[]> released = DescribedCallBenchmark.releasesOver(binary, file("weivel.test.standardBinary"),
                file("weivel.test.describedBinary"), file("weivel.test.echoBinary"), CALLS);

        assertEquals(Arrays.asList(PairMeasurement.BARE, PairMeasurement.WEIVEL, DescribedCallMeasurement.ECHO),
                Arrays.asList(released.keySet().toArray()));
        assertArrayEquals(new long[] {CALLS, 0}, released.get(PairMeasurement.BARE), "the hand path");
        assertArrayEquals(new long[] {CALLS, 0}, released.get(PairMeasurement.WEIVEL), "the bound object");
        assertArrayEquals(new long[] {0, 0}, released.get(DescribedCallMeasurement.ECHO), "the echo call");
    }

    private static Path file(String property) {
        return Paths.get(System.getProperty(property));
    }
}
