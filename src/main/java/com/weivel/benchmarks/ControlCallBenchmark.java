package com.weivel.benchmarks;

import com.weivel.core.Answer;
import com.weivel.core.BareServiceGet;
import com.weivel.core.Core;
import com.weivel.core.CoreBinary;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreInternals;
import java.util.LinkedHashMap;

/**
 * The control call: {@code weivel_get_service} through the wrapper, with the core's timer armed as a host arms it,
 * over the same function of the core's table on the same core, plugin and id, called bare.
 */
public final class ControlCallBenchmark {
    /** Enough batches that the median of the per-round ratios settles, at a fraction of a second a path on FFM. */
    public static final Sampling DEFAULT_SAMPLING = new Sampling(100_000, 1_000, 201);

    private ControlCallBenchmark() {
    }

    /**
     * Times the call through the wrapper against the bare call.
     *
     * @param binary the core's loaded binary
     * @param sampling how the paths are sampled
     * @return the measurement
     * @throws IllegalStateException when either path answers anything but the provider's implementation handle
     */
    public static PairMeasurement measure(CoreBinary binary, Sampling sampling) {
        try (CoreWorld world = CoreWorld.open(binary)) {
            world.provide(Long::sum);
            Core core = world.core();
            long consumer = world.consumerPlugin();
            try (BareServiceGet bare = CoreWorld.ok(CoreInternals.bareServiceGet(core, consumer,
                    CoreWorld.ARITHMETIC), "the bare call's preparation")) {
                long implementation = throughTheWrapper(core, consumer);
                if (bare.call() != implementation) {
                    throw new IllegalStateException("the bare call answered another implementation than the wrapper");
                }
                LinkedHashMap<String, MeasuredPath> paths = new LinkedHashMap<>();
                paths.put(PairMeasurement.BARE, call -> bare.call());
                paths.put(PairMeasurement.WEIVEL, call -> throughTheWrapper(core, consumer));
                return PairMeasurement.of(Interleaved.sample(paths, sampling));
            }
        }
    }

    private static long throughTheWrapper(Core core, long consumer) {
        Answer<Long> answer = core.call(CoreCalls.getService(consumer, CoreWorld.ARITHMETIC));
        return CoreWorld.ok(answer, "weivel_get_service through the wrapper");
    }
}
