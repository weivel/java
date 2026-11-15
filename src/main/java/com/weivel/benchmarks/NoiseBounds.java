package com.weivel.benchmarks;

/**
 * One platform's noise bounds: how far from 1 two identical paths are on a quiet run, the most a run may be, and how
 * many times its noise a same-language pair there may differ by.
 */
public final class NoiseBounds {
    private final double quiet;
    private final double worst;
    private final double margin;

    NoiseBounds(double quiet, double worst, double margin) {
        this.quiet = quiet;
        this.worst = worst;
        this.margin = margin;
    }

    /**
     * Answers {@code quiet}.
     *
     * @return the platform's quiet noise
     */
    public double quiet() {
        return quiet;
    }

    /**
     * Answers {@code worst}.
     *
     * @return the noise past which a run is too noisy to judge
     */
    public double worst() {
        return worst;
    }

    /**
     * Answers {@code margin}.
     *
     * @return how many times its noise, or the quiet noise when it was quieter, a same-language pair may differ by
     */
    public double margin() {
        return margin;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NoiseBounds)) {
            return false;
        }
        NoiseBounds bounds = (NoiseBounds) other;
        return Double.compare(quiet, bounds.quiet) == 0 && Double.compare(worst, bounds.worst) == 0
                && Double.compare(margin, bounds.margin) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Double.hashCode(quiet) + Double.hashCode(worst)) + Double.hashCode(margin);
    }

    @Override
    public String toString() {
        return "NoiseBounds[quiet=" + quiet + ", worst=" + worst + ", margin=" + margin + "]";
    }
}
