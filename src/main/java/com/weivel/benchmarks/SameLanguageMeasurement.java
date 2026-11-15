package com.weivel.benchmarks;

import java.util.Map;

/**
 * A call on a service resolved through weivel timed against the same call on the provider's object, and that direct
 * call timed twice: how far the two identical direct paths differ is the run's noise.
 */
public final class SameLanguageMeasurement {
    static final String DIRECT = "direct";
    static final String DIRECT_AGAIN = "directAgain";
    static final String THROUGH_WEIVEL = "throughWeivel";

    private final double[] noiseRatios;
    private final double[] sameLanguageRatios;

    private SameLanguageMeasurement(double[] direct, double[] directAgain, double[] throughWeivel) {
        this.noiseRatios = Interleaved.roundRatios(directAgain, direct);
        this.sameLanguageRatios = Interleaved.roundRatios(throughWeivel, direct);
    }

    static SameLanguageMeasurement of(Map<String, double[]> samples) {
        return new SameLanguageMeasurement(samples.get(DIRECT), samples.get(DIRECT_AGAIN),
                samples.get(THROUGH_WEIVEL));
    }

    /**
     * Each round's second direct batch over its first.
     *
     * @return a copy, one ratio per round
     */
    public double[] noiseRatios() {
        return noiseRatios.clone();
    }

    /**
     * Each round's batch through the resolved service over its direct batch.
     *
     * @return a copy, one ratio per round
     */
    public double[] sameLanguageRatios() {
        return sameLanguageRatios.clone();
    }
}
