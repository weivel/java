package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ForeignLibraryTest {
    @Test
    void noneOfTheCoresSymbolsCanBeOpenedPastTheWrapper() {
        Answer<ForeignLibrary> opened = ForeignLibrary.open(NativeFiles.coreBinary(),
                Collections.singletonList("weivel_core_create"));

        assertInstanceOf(Answer.Mistake.class, opened);
        assertTrue(((Answer.Mistake<?>) opened).detail().startsWith("weivel_core_create is the core's"),
                opened.toString());
    }

    @Test
    void aLibraryLackingASymbolNamedIsRefusedNamingTheFirst() {
        Answer<ForeignLibrary> opened = ForeignLibrary.open(NativeFiles.echoBinary(),
                Arrays.asList("echo_echo", "echo_nothing", "echo_none"));

        assertInstanceOf(Answer.Mistake.class, opened);
        assertTrue(((Answer.Mistake<?>) opened).detail().endsWith("exports no function echo_nothing"),
                opened.toString());
    }

    @Test
    void onlyASymbolNamedWhenTheLibraryWasOpenedIsBound() {
        try (ForeignLibrary library = TestCores.ok(ForeignLibrary.open(NativeFiles.echoBinary(),
                Collections.singletonList("echo_released")))) {
            assertThrows(IllegalArgumentException.class,
                    () -> library.function("echo_echo", NativeType.INT, NativeType.ADDRESS));
        }
    }

    @Test
    void aManifestLaidOutInNativeMemoryIsCopiedWhole() {
        Manifest manifest = new Manifest("copied", 3, java.util.Optional.of("entry.jar"),
                Arrays.asList("app"), Arrays.asList("screens"), Collections.<String>emptyList(),
                Collections.singletonList(new Manifest.ServiceOffer("copied:one",
                        Collections.singletonList(new NamedNumber("latency", 2.5)))),
                Collections.singletonList(new Manifest.ServiceWant("clock", java.util.Optional.of("local:clock"),
                        Choice.YES, Choice.NO, Collections.<NamedNumber>emptyList())),
                Arrays.asList("network"), java.util.Optional.of("local:copied"));
        try (NativeArena arena = new NativeArena(NativeBackends.current());
             ForeignMemory memory = new ForeignMemory(new NativeArena(NativeBackends.current()))) {
            NativeRegion laidOut = Marshal.manifest(arena, manifest);

            assertEquals(manifest, memory.manifest(laidOut.address()));
        }
    }

    @Test
    void closedMemoryRefusesEveryAccess() {
        ForeignMemory memory = new ForeignMemory(new NativeArena(NativeBackends.current()));
        long address = memory.allocate(16);
        long text = memory.text("closed");
        memory.close();

        assertThrows(IllegalStateException.class, () -> memory.allocate(8));
        assertThrows(IllegalStateException.class, () -> memory.text("late"));
        assertThrows(IllegalStateException.class, () -> memory.bytes(new byte[1]));
        assertThrows(IllegalStateException.class, () -> memory.write(address, 0, new byte[1]));
        assertThrows(IllegalStateException.class, () -> memory.getInt(address, 0));
        assertThrows(IllegalStateException.class, () -> memory.putInt(address, 0, 1));
        assertThrows(IllegalStateException.class, () -> memory.getLong(address, 0));
        assertThrows(IllegalStateException.class, () -> memory.putLong(address, 0, 1));
        assertThrows(IllegalStateException.class, () -> memory.readBytes(address, 0, 1));
        assertThrows(IllegalStateException.class, () -> memory.readText(text));
        assertThrows(IllegalStateException.class, () -> memory.manifest(address));
        assertThrows(IllegalStateException.class, memory::close);
    }
}
