package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

/**
 * A call through the foreign function backend allocates nothing on the Java heap. JNA boxes its arguments by
 * design, so the test runs only where the foreign function backend is selected.
 */
class NativeCallAllocationTest {
    private static final NativeSignature RELEASE = NativeSignature.of(NativeType.VOID, NativeType.ADDRESS);
    private static final int WARM_UP_CALLS = 200_000;
    private static final int MEASURED_CALLS = 100_000;
    /** What reading the allocation counter itself may cost, measured as 0 on JDK 25; a spreader costs 40 B a call. */
    private static final long ALLOWED_BYTES = 1024;

    @Test
    void aFixedArityCallThroughTheForeignFunctionBackendAllocatesNothing() {
        assumeTrue("ffm".equals(NativeBackends.current().name()), "the foreign function backend is not selected");
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        try (OpenLibrary echo = NativeBackends.current().open(NativeFiles.echoBinary())) {
            NativeFunction release = echo.function("echo_release", RELEASE);
            callRepeatedly(release, WARM_UP_CALLS);

            long before = threads.getThreadAllocatedBytes(thread);
            callRepeatedly(release, MEASURED_CALLS);
            long allocated = threads.getThreadAllocatedBytes(thread) - before;

            System.out.println(MEASURED_CALLS + " calls allocated " + allocated + " bytes");
            assertTrue(allocated <= ALLOWED_BYTES, MEASURED_CALLS + " calls allocated " + allocated + " bytes");
        }
    }

    private static void callRepeatedly(NativeFunction release, int calls) {
        for (int index = 0; index < calls; index++) {
            release.call(0L);
        }
    }
}
