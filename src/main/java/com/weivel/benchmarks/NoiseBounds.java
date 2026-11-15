package com.weivel.benchmarks;

/** One platform's noise bounds: how far from 1 two identical paths are on a quiet run, and the most a run may be. */
public final class NoiseBounds {
    private final double quiet;
    private final double worst;

    NoiseBounds(double quiet, double worst) {
        this.quiet = quiet;
        this.worst = worst;
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

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NoiseBounds)) {
            return false;
        }
        NoiseBounds bounds = (NoiseBounds) other;
        return Double.compare(quiet, bounds.quiet) == 0 && Double.compare(worst, bounds.worst) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(quiet) + Double.hashCode(worst);
    }

    @Override
    public String toString() {
        return "NoiseBounds[quiet=" + quiet + ", worst=" + worst + "]";
    }
}
