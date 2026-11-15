package com.weivel.benchmarks;

import com.weivel.core.CoreBinary;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.function.LongBinaryOperator;
import java.util.function.Supplier;

/**
 * The same-language gate: a consumer resolving a provider's service is answered the provider's own object, so a call
 * through weivel is a plain Java call on it. The timing beside it can show only noise, since every path makes the very
 * same call on the very same object.
 */
public final class SameLanguageBenchmark {
    /** A million calls a batch, so a batch outlasts the clock's resolution on every platform many times over. */
    public static final Sampling DEFAULT_SAMPLING = new Sampling(20_000_000, 1_000_000, 101);

    private SameLanguageBenchmark() {
    }

    /**
     * Resolves the service and answers what was resolved, beside the provider's object it must be.
     *
     * @param binary the core's loaded binary
     * @return the provider's object and the consumer's resolution of it
     */
    static Resolution resolve(CoreBinary binary) {
        return whileResolved(binary, resolution -> resolution);
    }

    private static <T> T whileResolved(CoreBinary binary, Function<Resolution, T> use) {
        try (CoreWorld world = CoreWorld.open(binary)) {
            LongBinaryOperator arithmetic = Long::sum;
            world.provide(arithmetic);
            return use.apply(new Resolution(arithmetic, CoreWorld.ok(world.consumer().get(CoreWorld.ARITHMETIC),
                    "the consumer's get")));
        }
    }

    /**
     * Times a call on the service a consumer resolved against the same call on the provider's object, and that direct
     * call twice.
     *
     * @param binary the core's loaded binary
     * @param sampling how the paths are sampled
     * @return the measurement
     * @throws IllegalStateException when the consumer is not answered the provider's own object
     */
    public static SameLanguageMeasurement measure(CoreBinary binary, Sampling sampling) {
        return whileResolved(binary, resolution -> measure(resolution, sampling));
    }

    static SameLanguageMeasurement measure(Resolution resolution, Sampling sampling) {
        if (!resolution.isTheProvidersObject()) {
            throw new IllegalStateException("the consumer was not answered the provider's own object");
        }
        LongBinaryOperator direct = resolution.provided();
        LongBinaryOperator resolved = (LongBinaryOperator) resolution.resolved();
        LinkedHashMap<String, Supplier<MeasuredPath>> paths = new LinkedHashMap<>();
        paths.put(SameLanguageMeasurement.DIRECT, () -> new Adding(direct));
        paths.put(SameLanguageMeasurement.DIRECT_AGAIN, () -> new Adding(direct));
        paths.put(SameLanguageMeasurement.THROUGH_WEIVEL, () -> new Adding(resolved));
        return SameLanguageMeasurement.of(Interleaved.sampleFresh(paths, sampling));
    }

    /**
     * One path's call on the object it was given.
     *
     * @implNote Every path is an instance of this one class, so all three run the very same compiled code and differ
     * only in which reference they hold; a lambda per path would compile each apart and time their layouts.
     */
    private static final class Adding implements MeasuredPath {
        private final LongBinaryOperator target;

        Adding(LongBinaryOperator target) {
            this.target = target;
        }

        @Override
        public long call(int index) {
            return target.applyAsLong(index, 1);
        }
    }

    /** The object a provider registered, and what a consumer's get resolved under the same id. */
    static final class Resolution {
        private final LongBinaryOperator provided;
        private final Object resolved;

        Resolution(LongBinaryOperator provided, Object resolved) {
            this.provided = provided;
            this.resolved = resolved;
        }

        /**
         * Answers {@code provided}.
         *
         * @return the provider's own object
         */
        public LongBinaryOperator provided() {
            return provided;
        }

        /**
         * Answers {@code resolved}.
         *
         * @return what the consumer's get answered
         */
        public Object resolved() {
            return resolved;
        }

        /**
         * Whether the consumer was answered the provider's own object, by reference.
         *
         * @return whether the two are the same object
         */
        public boolean isTheProvidersObject() {
            return provided == resolved;
        }
    }
}
