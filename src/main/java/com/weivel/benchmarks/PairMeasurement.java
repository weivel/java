package com.weivel.benchmarks;

import java.util.Map;

/** A call weivel makes timed against the same C function called bare, side by side, round by round. */
public final class PairMeasurement {
    static final String BARE = "bare";
    static final String WEIVEL = "weivel";

    private final double[] bare;
    private final double[] weivel;
    private final double[] roundRatios;

    private PairMeasurement(double[] bare, double[] weivel) {
        this.bare = bare;
        this.weivel = weivel;
        this.roundRatios = Interleaved.roundRatios(weivel, bare);
    }

    static PairMeasurement of(Map<String, double[]> samples) {
        return new PairMeasurement(samples.get(BARE), samples.get(WEIVEL));
    }

    /**
     * Answers {@code bare}.
     *
     * @return the bare call's nanoseconds per call, by its rounds
     */
    public Summary bare() {
        return Summary.of(bare);
    }

    /**
     * Answers {@code weivel}.
     *
     * @return the weivel call's nanoseconds per call, by its rounds
     */
    public Summary weivel() {
        return Summary.of(weivel);
    }

    /**
     * Each round's weivel batch time over its bare batch time, the samples a ratio gate is judged on.
     *
     * @return a copy, one ratio per round
     */
    public double[] roundRatios() {
        return roundRatios.clone();
    }
}
