package com.weivel.benchmarks;

import java.util.Arrays;

/** Samples summarised: the smallest, the quartiles, the median, the 99th percentile and the largest. */
public final class Summary {
    private final double minimum;
    private final double lowerQuartile;
    private final double median;
    private final double upperQuartile;
    private final double percentile99;
    private final double maximum;

    private Summary(double[] sorted) {
        this.minimum = sorted[0];
        this.lowerQuartile = percentile(sorted, 0.25);
        this.median = percentile(sorted, 0.5);
        this.upperQuartile = percentile(sorted, 0.75);
        this.percentile99 = percentile(sorted, 0.99);
        this.maximum = sorted[sorted.length - 1];
    }

    /**
     * Summarises samples.
     *
     * @param samples at least one
     * @return the summary
     * @throws IllegalArgumentException for no sample, which has no median
     */
    public static Summary of(double[] samples) {
        if (samples.length == 0) {
            throw new IllegalArgumentException("nothing was measured, so there is nothing to summarise");
        }
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        return new Summary(sorted);
    }

    private static double percentile(double[] sorted, double fraction) {
        return sorted[Math.max((int) Math.ceil(fraction * sorted.length) - 1, 0)];
    }

    /**
     * Answers {@code minimum}.
     *
     * @return the smallest sample
     */
    public double minimum() {
        return minimum;
    }

    /**
     * Answers {@code lowerQuartile}.
     *
     * @return the sample a quarter of the way up
     */
    public double lowerQuartile() {
        return lowerQuartile;
    }

    /**
     * Answers {@code median}.
     *
     * @return the middle sample, the lower of the two middle ones for an even count
     */
    public double median() {
        return median;
    }

    /**
     * Answers {@code upperQuartile}.
     *
     * @return the sample three quarters of the way up
     */
    public double upperQuartile() {
        return upperQuartile;
    }

    /**
     * Answers {@code percentile99}.
     *
     * @return the sample 99 percent of the way up
     */
    public double percentile99() {
        return percentile99;
    }

    /**
     * Answers {@code maximum}.
     *
     * @return the largest sample
     */
    public double maximum() {
        return maximum;
    }

    @Override
    public String toString() {
        return "Summary[median=" + median + ", lowerQuartile=" + lowerQuartile + ", upperQuartile=" + upperQuartile
                + ", percentile99=" + percentile99 + "]";
    }
}
