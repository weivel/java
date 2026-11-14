package com.weivel.core;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
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
    void aCallThroughTheTableCreatesAndDestroysACore() throws Throwable {
        CoreLoad.Loaded loaded = assertInstanceOf(CoreLoad.Loaded.class, CoreBinary.load(NativeFiles.coreBinary()));
        try (CoreBinary binary = loaded.binary(); Arena arena = Arena.ofConfined()) {
            CoreFunctions functions = binary.functions();
            MemorySegment options = arena.allocate(CoreLayouts.CORE_OPTIONS);
            options.set(JAVA_INT, CoreLayouts.CORE_OPTIONS.byteOffset(PathElement.groupElement("size")),
                    (int) CoreLayouts.CORE_OPTIONS.byteSize());
            MemorySegment coreSlot = arena.allocate(ADDRESS);
            MemorySegment pendingEvents = arena.allocate(JAVA_INT);

            int created = (int) functions.handle(CoreFunction.CORE_CREATE).invokeExact(options, coreSlot, pendingEvents);
            MemorySegment core = coreSlot.get(ADDRESS, 0);
            int destroyed = (int) functions.handle(CoreFunction.CORE_DESTROY).invokeExact(core);

            assertEquals(STATUS_OK, created);
            assertNotEquals(MemorySegment.NULL, core);
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
                () -> { int ignored = (int) functions.handle(CoreFunction.CORE_DESTROY).invokeExact(MemorySegment.NULL); });
    }
}
