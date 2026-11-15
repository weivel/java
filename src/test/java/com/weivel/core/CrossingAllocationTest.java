package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A crossing lays its arguments out in the wrapper's own scratch memory, so a warm call allocates no native memory. */
class CrossingAllocationTest {
    private static final int WARM_UP_CALLS = 1_000;
    private static final int MEASURED_CALLS = 1_000;
    private static final String ID = "provider:arithmetic";

    @Test
    void aGetServiceCrossingAllocatesNoNativeMemoryOnceWarm() {
        CountingAccess access = new CountingAccess();
        try (OpenLibrary library = access.open(NativeFiles.coreBinary())) {
            Core core = TestCores.ok(Core.wrapped(Wrapper.start(CoreFunctions.link(access, library),
                    EventCopy.INSTANCE, CoreOptions.defaults(), Optional.<CoreListener>empty(), failure -> { })));
            try {
                getServiceRepeatedly(core, WARM_UP_CALLS);
                long before = access.allocations();

                getServiceRepeatedly(core, MEASURED_CALLS);

                assertEquals(before, access.allocations(), MEASURED_CALLS + " warm get_service calls allocated");
            } finally {
                core.destroy();
                core.close();
            }
        }
    }

    private static void getServiceRepeatedly(Core core, int calls) {
        for (int call = 0; call < calls; call++) {
            Answer<Long> answer = core.call(CoreCalls.getService(CoreConstants.HOST, ID));
            assertFalse(answer instanceof Answer.Refused || answer instanceof Answer.Defect, String.valueOf(answer));
        }
    }
}
