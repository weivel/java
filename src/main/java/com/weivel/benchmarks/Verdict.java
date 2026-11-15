package com.weivel.benchmarks;

/** What a gate decided of one run: whether it passed, and the sentence saying what was measured against what. */
public final class Verdict {
    private final boolean passed;
    private final String reason;

    private Verdict(boolean passed, String reason) {
        this.passed = passed;
        this.reason = reason;
    }

    static Verdict passed(String reason) {
        return new Verdict(true, reason);
    }

    static Verdict failed(String reason) {
        return new Verdict(false, reason);
    }

    /**
     * Answers {@code passed}.
     *
     * @return whether the run passed the gate
     */
    public boolean passed() {
        return passed;
    }

    /**
     * Answers {@code reason}.
     *
     * @return what was measured against what, or why nothing could be judged
     */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return (passed ? "passed: " : "failed: ") + reason;
    }
}
