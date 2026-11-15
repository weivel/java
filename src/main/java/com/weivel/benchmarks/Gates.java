package com.weivel.benchmarks;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The timing gates, judged on per-round medians as the kit's {@code RUNNER.md} defines them. A run with nothing
 * measured fails, and so does a run on a backend or platform the budget gives no line: no bound is never a pass.
 */
public final class Gates {
    private Gates() {
    }

    /**
     * Judges a ratio gate: the median of the per-round ratios against the gate's bound on the backend and platform.
     *
     * @param budget the kit's budget
     * @param gate the gate
     * @param backend the backend the run was on
     * @param platform the kit's platform the run was on; empty for a system the kit names no bound for
     * @param roundRatios one ratio per round
     * @return the verdict
     */
    public static Verdict ratio(LanguageBudget budget, RatioGate gate, String backend, Optional<String> platform,
                                double[] roundRatios) {
        String name = gate.kitNameFor(backend);
        if (roundRatios.length == 0) {
            return Verdict.failed("nothing was measured of " + name + ", so it cannot pass");
        }
        if (!platform.isPresent()) {
            return Verdict.failed("this system is no kit platform, so " + name + " has no bound and cannot pass");
        }
        OptionalDouble bound = budget.ratioBound(gate, backend, platform.get());
        if (!bound.isPresent()) {
            return Verdict.failed("no CI run has measured " + name + " on " + platform.get()
                    + ", so it has no bound and cannot pass");
        }
        double median = Summary.of(roundRatios).median();
        String measured = name + " on " + platform.get() + " ran " + format(median) + " times the bare call, against "
                + "a bound of " + format(bound.getAsDouble());
        return median <= bound.getAsDouble() ? Verdict.passed(measured) : Verdict.failed(measured);
    }

    /**
     * Judges a same-language pair: refused as too noisy when its two identical direct paths differ by more than the
     * platform's worst noise, and otherwise passed when the call through weivel is within the noise margin times the
     * run's own noise, or the platform's quiet noise when the run was quieter.
     *
     * @param budget the kit's budget
     * @param platform the kit's platform the run was on; empty for a system the kit names no bound for
     * @param noiseRatios each round's second direct batch over its first
     * @param sameLanguageRatios each round's batch through weivel over its direct batch
     * @return the verdict
     */
    public static Verdict sameLanguage(LanguageBudget budget, Optional<String> platform, double[] noiseRatios,
                                       double[] sameLanguageRatios) {
        if (noiseRatios.length == 0 || sameLanguageRatios.length == 0) {
            return Verdict.failed("nothing was measured of the same-language call, so it cannot pass");
        }
        Optional<NoiseBounds> bounds = platform.isPresent() ? budget.noiseBounds(platform.get())
                : Optional.<NoiseBounds>empty();
        if (!bounds.isPresent()) {
            return Verdict.failed("the budget gives no noise line for " + platform.orElse("this system")
                    + ", so the same-language call cannot pass");
        }
        double noise = deviation(Summary.of(noiseRatios).median());
        if (noise > bounds.get().worst()) {
            return Verdict.failed("the run was too noisy to judge: its two identical direct paths differed by "
                    + format(noise) + ", over the worst noise of " + format(bounds.get().worst()));
        }
        double ratio = Summary.of(sameLanguageRatios).median();
        double bound = 1 + bounds.get().margin() * Math.max(bounds.get().quiet(), noise);
        String measured = "the same-language call ran " + format(ratio) + " times the direct call, with noise "
                + format(noise) + ", against a bound of " + format(bound);
        return ratio <= bound && ratio >= 1 / bound ? Verdict.passed(measured) : Verdict.failed(measured);
    }

    /**
     * How far a ratio is from 1, the same either way: 1.1 and 1/1.1 are both 0.1 away.
     *
     * @param ratio the ratio
     * @return the distance
     */
    public static double deviation(double ratio) {
        return Math.max(ratio, 1 / ratio) - 1;
    }

    private static String format(double number) {
        return String.format(Locale.ROOT, "%.6f", number);
    }
}
