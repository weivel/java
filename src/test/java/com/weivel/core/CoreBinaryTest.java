package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreBinaryTest {
    private static final int STATUS_OK = 0;

    @Test
    void theRealCoreLoads() {
        CoreLoad load = CoreBinary.load(NativeFiles.coreBinary());

        CoreLoad.Loaded loaded = assertInstanceOf(CoreLoad.Loaded.class, load);
        loaded.binary().close();
    }

    @Test
    void aCallThroughTheTableCreatesAndDestroysACore() {
        CoreLoad.Loaded loaded = assertInstanceOf(CoreLoad.Loaded.class, CoreBinary.load(NativeFiles.coreBinary()));
        try (CoreBinary binary = loaded.binary();
             NativeArena arena = new NativeArena(binary.functions().access())) {
            CoreFunctions functions = binary.functions();
            NativeRegion options = arena.allocate(CoreLayouts.CORE_OPTIONS);
            options.putInt(CoreLayouts.CORE_OPTIONS.offsetOf("size"), (int) CoreLayouts.CORE_OPTIONS.byteSize());
            NativeRegion coreSlot = arena.allocate(Layout.ADDRESS);
            NativeRegion pendingEvents = arena.allocate(Layout.INT);

            int created = functions.status(CoreFunction.CORE_CREATE, options.address(), coreSlot.address(),
                    pendingEvents.address());
            long core = coreSlot.getAddress(0);
            int destroyed = functions.status(CoreFunction.CORE_DESTROY, core);

            assertEquals(STATUS_OK, created);
            assertNotEquals(0, core);
            assertEquals(STATUS_OK, destroyed);
        }
    }

    @Test
    void aFileThatIsNoLibraryFailsAsAValue() {
        Path header = NativeFiles.coreHeader();

        CoreLoad load = CoreBinary.load(header);

        CoreLoad.NotALibrary notALibrary = assertInstanceOf(CoreLoad.NotALibrary.class, load);
        assertEquals(header, notALibrary.path());
    }

    @Test
    void aPathNamingNothingFailsAsAValue(@TempDir Path directory) {
        Path nothing = directory.resolve("nothing-here");

        CoreLoad load = CoreBinary.load(nothing);

        assertEquals(nothing, assertInstanceOf(CoreLoad.NotALibrary.class, load).path());
    }

    @Test
    void theStandardsBinaryFailsNamingTheFirstMissingCoreFunction() {
        Path standard = NativeFiles.standardBinary();

        CoreLoad load = CoreBinary.load(standard);

        CoreLoad.MissingFunction missing = assertInstanceOf(CoreLoad.MissingFunction.class, load);
        assertEquals("weivel_core_create", missing.function());
        assertEquals(standard, missing.path());
    }

    @Test
    void closingTheBinaryClosesWhatTheLoadOpened() {
        CoreLoad.Loaded loaded = assertInstanceOf(CoreLoad.Loaded.class, CoreBinary.load(NativeFiles.coreBinary()));
        CoreFunctions functions = loaded.binary().functions();

        loaded.binary().close();

        assertThrows(IllegalStateException.class,
                () -> functions.status(CoreFunction.CORE_DESTROY, 0));
    }
}
