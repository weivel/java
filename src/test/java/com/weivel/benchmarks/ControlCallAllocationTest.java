package com.weivel.benchmarks;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.BareServiceGet;
import com.weivel.core.Core;
import com.weivel.core.CoreBinary;
import com.weivel.core.CoreCall;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreInternals;
import com.weivel.core.CoreLoad;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/**
 * The Java heap a warm {@code weivel_get_service} through the wrapper allocates per call, in the world the control
 * call is timed in: no more than the answer the caller receives and the call it builds, and on JNA what JNA allocates
 * for the native call itself.
 */
class ControlCallAllocationTest {
    private static final int WARM_UP_CALLS = 300_000;
    private static final int MEASURED_CALLS = 100_000;
    /**
     * The most rounds measured; the test stops at the first round within the bound.
     *
     * @implNote One round alone is not a measurement: a round the JIT recompiles in allocates more than a settled one,
     *     so the least of rounds is what the call itself allocates.
     */
    private static final int MOST_ROUNDS = 100;
    /** What reading the allocation counter around a round may cost, about 0.01 bytes a call over a round. */
    private static final long COUNTER_READ_BYTES = 1024;
    /** The {@code Answer.Ok} the caller receives, 16 bytes, and the boxed handle it carries, 24. */
    private static final double ANSWER_BYTES = 40;
    /** The {@code CoreCall} the caller builds, 24 bytes, and its crossing, which holds the id and plugin, 32. */
    private static final double BUILT_CALL_BYTES = 56;
    /**
     * What JNA allocates for one five-argument call: the argument array, its boxes and JNA's own copies.
     *
     * @implNote Measured with escape analysis off, where nothing of it is removed; with it on, how much the JIT
     *     removes differs from one JVM to the next, between 256 and 392 bytes, so the bound takes the most.
     */
    private static final double JNA_CALL_BYTES = 392;

    @Test
    void aWarmGetServiceThroughTheWrapperAllocatesOnlyItsAnswerAndItsCall() {
        try (CoreBinary binary = assertInstanceOf(CoreLoad.Loaded.class,
                CoreBinary.load(Paths.get(System.getProperty("weivel.test.coreBinary")))).binary();
             CoreWorld world = CoreWorld.open(binary)) {
            world.provide(Long::sum);
            Core core = world.core();
            long consumer = world.consumerPlugin();
            try (BareServiceGet bare = CoreWorld.ok(CoreInternals.bareServiceGet(core, consumer,
                    CoreWorld.ARITHMETIC), "the bare call's preparation")) {
                double nativeCallBytes = "jna".equals(binary.backend()) ? JNA_CALL_BYTES : 0;
                CoreCall<Long> reused = CoreCalls.getService(consumer, CoreWorld.ARITHMETIC);

                double built = leastBytesPerCall(() -> ok(core.call(CoreCalls.getService(consumer,
                        CoreWorld.ARITHMETIC))), nativeCallBytes + ANSWER_BYTES + BUILT_CALL_BYTES);
                double again = leastBytesPerCall(() -> ok(core.call(reused)), nativeCallBytes + ANSWER_BYTES);
                double bareCall = leastBytesPerCall(bare::call, nativeCallBytes);

                String measured = String.format(Locale.ROOT, "%s: a call built each time %.2f bytes, a call made "
                        + "again %.2f, the bare call %.2f", binary.backend(), built, again, bareCall);
                System.out.println(measured);
                assertTrue(within(built, nativeCallBytes + ANSWER_BYTES + BUILT_CALL_BYTES), measured);
                assertTrue(within(again, nativeCallBytes + ANSWER_BYTES), measured);
            }
        }
    }

    private static boolean within(double bytesPerCall, double allowed) {
        return bytesPerCall * MEASURED_CALLS <= allowed * MEASURED_CALLS + COUNTER_READ_BYTES;
    }

    private static double leastBytesPerCall(LongSupplier call, double allowed) {
        callRepeatedly(call, WARM_UP_CALLS);
        long least = Long.MAX_VALUE;
        for (int round = 0; round < MOST_ROUNDS && !within((double) least / MEASURED_CALLS, allowed); round++) {
            long before = allocatedBytes();
            callRepeatedly(call, MEASURED_CALLS);
            least = Math.min(least, allocatedBytes() - before);
        }
        return (double) least / MEASURED_CALLS;
    }

    private static void callRepeatedly(LongSupplier call, int calls) {
        long sum = 0;
        for (int index = 0; index < calls; index++) {
            sum += call.getAsLong();
        }
        if (sum == 0) {
            throw new IllegalStateException("every call answered handle 0");
        }
    }

    private static long ok(Answer<Long> answer) {
        return CoreWorld.ok(answer, "weivel_get_service through the wrapper");
    }

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
                .getThreadAllocatedBytes(Thread.currentThread().getId());
    }
}
