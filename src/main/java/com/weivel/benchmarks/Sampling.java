package com.weivel.benchmarks;

/** How a path is sampled: warm-up calls first, discarded, then one timed batch of calls per round. */
public final class Sampling {
    private final int warmupCalls;
    private final int callsPerBatch;
    private final int rounds;

    /**
     * A sampling.
     *
     * @param warmupCalls the calls made of each path before any is timed
     * @param callsPerBatch the calls one timed batch makes
     * @param rounds how many rounds, each timing one batch of every path
     * @throws IllegalArgumentException for a negative warm-up, or a batch or a round count below one
     */
    public Sampling(int warmupCalls, int callsPerBatch, int rounds) {
        if (warmupCalls < 0 || callsPerBatch < 1 || rounds < 1) {
            throw new IllegalArgumentException("a sampling of " + warmupCalls + " warm-up calls, " + callsPerBatch
                    + " calls a batch and " + rounds + " rounds measures nothing");
        }
        this.warmupCalls = warmupCalls;
        this.callsPerBatch = callsPerBatch;
        this.rounds = rounds;
    }

    /**
     * Answers {@code warmupCalls}.
     *
     * @return the calls made of each path before any is timed
     */
    public int warmupCalls() {
        return warmupCalls;
    }

    /**
     * Answers {@code callsPerBatch}.
     *
     * @return the calls one timed batch makes
     */
    public int callsPerBatch() {
        return callsPerBatch;
    }

    /**
     * Answers {@code rounds}.
     *
     * @return how many rounds are timed
     */
    public int rounds() {
        return rounds;
    }

    /**
     * How many calls a measurement makes of each path, warm-up included.
     *
     * @return the count
     */
    public long callsPerPath() {
        return warmupCalls + (long) callsPerBatch * rounds;
    }

    @Override
    public String toString() {
        return "Sampling[warmupCalls=" + warmupCalls + ", callsPerBatch=" + callsPerBatch + ", rounds=" + rounds + "]";
    }
}
