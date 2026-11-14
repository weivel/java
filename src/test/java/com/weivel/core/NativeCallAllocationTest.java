package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A call through the foreign function backend allocates nothing on the Java heap. JNA boxes its arguments by
 * design, so the test runs only where the foreign function backend is selected.
 */
class NativeCallAllocationTest {
    private static final NativeSignature RELEASE = NativeSignature.of(NativeType.VOID, NativeType.ADDRESS);
    private static final int WARM_UP_CALLS = 200_000;
    private static final int MEASURED_CALLS = 100_000;
    /**
     * The most rounds measured before the test gives up; it stops at the first round that allocates nothing.
     *
     * @implNote A fixed number of rounds failed on macOS runners, where every one finished before the call path was
     *     compiled and the interpreter's method handle path allocates; a spreader allocates in every round, however
     *     many are run, so measuring until one round is clean still tells the two apart.
     */
    private static final int MOST_ROUNDS = 300;
    /** The smallest object a call could allocate, so an allocation per call costs at least this much each time. */
    private static final long SMALLEST_OBJECT_BYTES = 16;
    /**
     * Fewer bytes than one smallest object per hundred calls.
     *
     * @implNote A clean round was not reached on some macOS arm64 runs within 300 rounds, where the compiled call
     *     path settled into allocating a few kilobytes a round whatever the layer did; an allocation per call, as the
     *     spreader made (24 to 40 bytes a call), costs a hundred times this bound and more.
     */
    private static final long ALLOWED_BYTES = MEASURED_CALLS * SMALLEST_OBJECT_BYTES / 100;

    @Test
    void aFixedArityCallThroughTheForeignFunctionBackendAllocatesNothing() {
        assumeTrue("ffm".equals(NativeBackends.current().name()), "the foreign function backend is not selected");
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        try (OpenLibrary echo = NativeBackends.current().open(NativeFiles.echoBinary())) {
            NativeFunction release = echo.function("echo_release", RELEASE);
            callRepeatedly(release, WARM_UP_CALLS);

            long least = Long.MAX_VALUE;
            List<Long> firstRounds = new ArrayList<>();
            int rounds = 0;
            for (; rounds < MOST_ROUNDS && least > ALLOWED_BYTES; rounds++) {
                long before = threads.getThreadAllocatedBytes(thread);
                callRepeatedly(release, MEASURED_CALLS);
                long allocated = threads.getThreadAllocatedBytes(thread) - before;
                least = Math.min(least, allocated);
                if (firstRounds.size() < 8) {
                    firstRounds.add(allocated);
                }
            }

            String measured = MEASURED_CALLS + " calls allocated at least " + least + " bytes over " + rounds
                    + " rounds, the first " + firstRounds;
            System.out.println(measured);
            assertTrue(least <= ALLOWED_BYTES, measured);
        }
    }

    private static void callRepeatedly(NativeFunction release, int calls) {
        for (int index = 0; index < calls; index++) {
            release.call(0L);
        }
    }
}
