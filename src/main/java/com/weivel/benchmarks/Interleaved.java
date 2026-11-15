package com.weivel.benchmarks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Samples paths side by side: every path warmed up first, then one batch of each per round, so a change in the
 * machine's speed during the run falls on every path alike, the path that goes first moving one on each round.
 */
final class Interleaved {
    private static volatile long kept;

    private Interleaved() {
    }

    /** Each path's nanoseconds per call, one batch a round, in round order, keyed as {@code paths} is. */
    static Map<String, double[]> sample(LinkedHashMap<String, MeasuredPath> paths, Sampling sampling) {
        LinkedHashMap<String, Supplier<MeasuredPath>> fixed = new LinkedHashMap<>();
        for (Map.Entry<String, MeasuredPath> path : paths.entrySet()) {
            MeasuredPath measured = path.getValue();
            fixed.put(path.getKey(), () -> measured);
        }
        return sampleFresh(fixed, sampling);
    }

    /**
     * As {@link #sample}, each batch timing a path its supplier made for that batch.
     *
     * @implNote A same-language path is one call on one small object, and on CI runners a path ran a constant third
     *     of a nanosecond slower than the other two for every round of one run, the same object all run long; an
     *     object made each batch moves whatever its placement costs onto a different path each round, where the
     *     median of the rounds leaves it out.
     */
    static Map<String, double[]> sampleFresh(LinkedHashMap<String, Supplier<MeasuredPath>> paths,
                                             Sampling sampling) {
        List<String> names = new ArrayList<>(paths.keySet());
        for (String name : names) {
            timeBatch(paths.get(name).get(), sampling.warmupCalls());
        }
        Map<String, double[]> samples = new LinkedHashMap<>();
        for (String name : names) {
            samples.put(name, new double[sampling.rounds()]);
        }
        for (int round = 0; round < sampling.rounds(); round++) {
            for (String name : roundOrder(names, round)) {
                samples.get(name)[round] = timeBatch(paths.get(name).get(), sampling.callsPerBatch());
            }
        }
        return samples;
    }

    /** The names in the order round {@code round} samples them: each round starts one name later. */
    static List<String> roundOrder(List<String> names, int round) {
        int start = round % names.size();
        List<String> ordered = new ArrayList<>(names.subList(start, names.size()));
        ordered.addAll(names.subList(0, start));
        return ordered;
    }

    /** Each round's {@code numerator} time over its {@code denominator} time: batches of one round ran side by side. */
    static double[] roundRatios(double[] numerator, double[] denominator) {
        double[] ratios = new double[numerator.length];
        for (int round = 0; round < numerator.length; round++) {
            ratios[round] = numerator[round] / denominator[round];
        }
        return ratios;
    }

    private static double timeBatch(MeasuredPath path, int calls) {
        long answers = 0;
        long start = System.nanoTime();
        for (int call = 0; call < calls; call++) {
            answers += path.call(call);
        }
        long elapsed = System.nanoTime() - start;
        kept += answers;
        return calls == 0 ? 0 : (double) elapsed / calls;
    }
}
