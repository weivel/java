package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LayoutTest {
    @Test
    void aJvmWithEightByteAddressesIsAccepted() {
        assertEquals(8, Layout.requireAddressSize(8));
    }

    @Test
    void aJvmWithAnyOtherAddressSizeIsRefusedSayingWhatItNeeds() {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> Layout.requireAddressSize(4));

        assertTrue(refused.getMessage().contains("64-bit JVM"), refused.getMessage());
        assertTrue(refused.getMessage().contains("4"), refused.getMessage());
    }

    @Test
    void theBackendThisJavaSelectsHasEightByteAddresses() {
        assertEquals(8, NativeBackends.current().addressSize());
    }
}
