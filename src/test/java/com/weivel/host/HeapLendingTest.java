package com.weivel.host;

import static com.weivel.host.LibrariesTest.addressIn;
import static com.weivel.host.LibrariesTest.loadedEcho;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Whether a heap array reaches the echo library in place: the address {@code echo_where} reports is compared with the
 * array's own, read without the layer. The array may move between the read and the call, so one exact match in
 * several tries proves the lending, and no match in as many proves the copy, since a copy never lies inside the array.
 *
 * @implNote Gradle runs this class alone, in JVMs of their own with uncompressed references, since reading a heap
 * address needs them and the rest of the suite measures allocation under the JVM's defaults.
 */
class HeapLendingTest {
    private static final int TRIES = 50;

    @Test
    void aHeapArrayIsLentOnTheForeignFunctionApiAndCopiedOnJna() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LibraryService echo = loadedEcho(fixture);
            int lent = 0;
            for (int attempt = 0; attempt < TRIES; attempt++) {
                byte[] input = new byte[64];
                long array = MemoryAddresses.firstElementOf(input);
                long seen = addressIn(echo.call("where", input));
                assertNotEquals(0, seen);
                lent += seen == array ? 1 : 0;
            }

            if ("ffm".equals(System.getProperty("weivel.test.backend"))) {
                assertTrue(lent > 0, "no call saw the array's own address, so the heap was copied");
            } else {
                assertEquals(0, lent, "JNA cannot lend the heap, so every call must see a copy");
            }
        }
    }
}
