package com.weivel.handles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The table's room, tokens and refusals, on handles no core answered, since none of it asks the core anything. */
class HandlesRoomTest {
    private static final int SMALLEST_TOKEN_CAPACITY = 16;
    private static final long OWNER = 7;

    private final RunningOutMemory memory = new RunningOutMemory();
    private final Handles handles = new Handles(memory);

    @Test
    void aTableThatRunsOutOfMemoryWhileItsTokensGrowKeepsEveryTokenAndCanStillGrow() {
        List<HandleEntry> made = new ArrayList<>();
        for (long handle = 1; handle <= SMALLEST_TOKEN_CAPACITY; handle++) {
            made.add(handles.reserve().add(HandleKind.WATCH, handle, OWNER, "object-" + handle));
        }
        int entryTableGrowth = 3;

        memory.runOutAfter(entryTableGrowth + 1);
        assertThrows(OutOfMemoryError.class, handles::reserve);
        memory.neverRunOut();

        for (HandleEntry entry : made) {
            assertSame(entry, handles.findByToken(entry.token()));
        }
        HandleEntry later = handles.reserve().add(HandleKind.WATCH, 100, OWNER, "later");
        assertSame(later, handles.findByToken(later.token()));
        assertSame(later, handles.find(100));
    }

    @Test
    void aCancelledReservationGivesItsRoomBack() {
        int before = memory.allocations();

        for (int round = 0; round < 100; round++) {
            handles.reserve().cancel();
        }

        assertEquals(before, memory.allocations());
    }

    @Test
    void anEndedTokenAnswersNothingBeforeItsIndexIsTakenAgain() {
        HandleEntry entry = handles.reserve().add(HandleKind.WATCH, 5, OWNER, "ended");
        long token = entry.token();

        handles.end(5);

        assertNull(handles.findByToken(token));
    }

    @Test
    void makingAnEntryForAHandleTheTableHoldsIsADefect() {
        handles.reserve().add(HandleKind.WATCH, 5, OWNER, "first");

        assertThrows(IllegalStateException.class,
                () -> handles.reserve().add(HandleKind.WATCH, 5, OWNER, "second"));
        assertEquals("first", handles.find(5).object());
    }

    @Test
    void everyHoldingIsKeptHoweverManyThereAre() {
        int count = 3 * SMALLEST_TOKEN_CAPACITY;
        for (long implementation = 1; implementation <= count; implementation++) {
            handles.reserve().hold(OWNER, implementation, "held-" + implementation);
        }

        for (long implementation = 1; implementation <= count; implementation++) {
            assertEquals("held-" + implementation, handles.held(OWNER, implementation));
        }
        assertEquals(count, handles.holdingCount());
    }

    @Test
    void endingTheEntriesOfNoOwnerIsRefused() {
        assertNotNull(handles.reserve().add(HandleKind.PLUGIN, 9, Handles.NO_OWNER, "plugin"));

        assertThrows(IllegalStateException.class, () -> handles.endOwned(Handles.NO_OWNER));
        assertThrows(IllegalStateException.class,
                () -> handles.endOwned(Handles.NO_OWNER, EnumSet.of(HandleKind.PLUGIN)));
        assertEquals("plugin", handles.find(9).object());
    }
}
