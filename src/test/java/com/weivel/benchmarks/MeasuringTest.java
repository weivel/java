package com.weivel.benchmarks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** How paths are sampled side by side and summarised. */
class MeasuringTest {
    @Test
    void startsEachRoundOnePathLaterSoNoPathAlwaysFollowsTheSameOne() {
        List<String> names = Arrays.asList("a", "b", "c");

        assertEquals(Arrays.asList("a", "b", "c"), Interleaved.roundOrder(names, 0));
        assertEquals(Arrays.asList("b", "c", "a"), Interleaved.roundOrder(names, 1));
        assertEquals(Arrays.asList("c", "a", "b"), Interleaved.roundOrder(names, 2));
        assertEquals(Arrays.asList("a", "b", "c"), Interleaved.roundOrder(names, 3));
    }

    @Test
    void callsEveryPathForItsWarmUpAndOneBatchARoundAndKeepsOneSampleARound() {
        Map<String, Integer> calls = new HashMap<>();
        LinkedHashMap<String, MeasuredPath> paths = new LinkedHashMap<>();
        for (String name : new String[] {"first", "second"}) {
            paths.put(name, call -> {
                calls.merge(name, 1, Integer::sum);
                return call;
            });
        }
        Sampling sampling = new Sampling(7, 5, 3);

        Map<String, double[]> samples = Interleaved.sample(paths, sampling);

        assertEquals(22L, sampling.callsPerPath());
        assertEquals(Integer.valueOf(22), calls.get("first"));
        assertEquals(Integer.valueOf(22), calls.get("second"));
        assertEquals(3, samples.get("first").length);
        assertEquals(3, samples.get("second").length);
    }

    @Test
    void timesEachBatchOnAPathItsSupplierMadeForIt() {
        Map<String, Integer> made = new HashMap<>();
        LinkedHashMap<String, Supplier<MeasuredPath>> paths = new LinkedHashMap<>();
        for (String name : new String[] {"first", "second"}) {
            paths.put(name, () -> {
                made.merge(name, 1, Integer::sum);
                return call -> call;
            });
        }

        Interleaved.sampleFresh(paths, new Sampling(7, 5, 3));

        assertEquals(Integer.valueOf(4), made.get("first"));
        assertEquals(Integer.valueOf(4), made.get("second"));
    }

    @Test
    void takesEachRoundsRatioOfItsOwnBatches() {
        assertArrayEquals(new double[] {2.0, 0.5, 3.0},
                Interleaved.roundRatios(new double[] {4.0, 1.0, 9.0}, new double[] {2.0, 2.0, 3.0}), 1e-12);
    }

    @Test
    void summarisesByRankWithTheLowerMiddleSampleAsTheMedian() {
        Summary summary = Summary.of(new double[] {9, 1, 5, 3, 7, 2});

        assertEquals(1.0, summary.minimum());
        assertEquals(3.0, summary.median());
        assertEquals(9.0, summary.maximum());
        assertEquals(2.0, summary.lowerQuartile());
        assertEquals(7.0, summary.upperQuartile());
        assertThrows(IllegalArgumentException.class, () -> Summary.of(new double[0]));
    }

    @Test
    void refusesASamplingThatMeasuresNothing() {
        assertThrows(IllegalArgumentException.class, () -> new Sampling(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Sampling(0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Sampling(-1, 1, 1));
    }

    @Test
    void namesTheSystemAsTheKitDoes() {
        assertEquals(Optional.of("linux"), KitPlatform.of("Linux"));
        assertEquals(Optional.of("windows"), KitPlatform.of("Windows Server 2025"));
        assertEquals(Optional.of("macos"), KitPlatform.of("Mac OS X"));
        assertEquals(Optional.<String>empty(), KitPlatform.of("FreeBSD"));
    }
}
