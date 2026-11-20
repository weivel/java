package com.weivel.benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.weivel.core.CoreBinary;
import com.weivel.core.CoreLoad;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * The described call's paths run once each outside the timed gate, so the suite on each backend proves both answer
 * the sample they were handed and the layout declared by hand matches the size the library compiled.
 */
class DescribedCallBenchmarkTest {
    @Test
    void bothPathsAnswerTheSampleTheyWereHandedOnThisBackend() {
        CoreBinary binary = assertInstanceOf(CoreLoad.Loaded.class, CoreBinary.load(Paths.get(
                System.getProperty("weivel.test.coreBinary")))).binary();
        try {
            DescribedCallMeasurement measured = DescribedCallBenchmark.measure(binary,
                    Paths.get(System.getProperty("weivel.test.standardBinary")),
                    Paths.get(System.getProperty("weivel.test.describedBinary")),
                    Paths.get(System.getProperty("weivel.test.echoBinary")), new Sampling(0, 1, 2));

            assertEquals(2, measured.pair().roundRatios().length);
        } finally {
            binary.close();
        }
    }
}
