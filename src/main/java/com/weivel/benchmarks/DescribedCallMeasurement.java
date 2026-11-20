package com.weivel.benchmarks;

import java.util.Map;

/**
 * The described call timed against the same operation through a binding written by hand, with the echo library's
 * bytes call through the host sampled in the same rounds, for its time beside theirs.
 */
public final class DescribedCallMeasurement {
    static final String ECHO = "echo";

    private final PairMeasurement pair;
    private final double[] echo;

    private DescribedCallMeasurement(PairMeasurement pair, double[] echo) {
        this.pair = pair;
        this.echo = echo;
    }

    static DescribedCallMeasurement of(Map<String, double[]> samples) {
        return new DescribedCallMeasurement(PairMeasurement.of(samples), samples.get(ECHO));
    }

    /**
     * Answers {@code pair}.
     *
     * @return the call through the bound object over the call through the binding written by hand
     */
    public PairMeasurement pair() {
        return pair;
    }

    /**
     * Answers {@code echo}.
     *
     * @return the echo library's bytes call through the host, in nanoseconds per call, by its rounds
     */
    public Summary echo() {
        return Summary.of(echo);
    }
}
