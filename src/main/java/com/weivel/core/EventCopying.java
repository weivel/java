package com.weivel.core;

import java.lang.foreign.MemorySegment;

/**
 * How the wrapper copies a drained event out of the core's struct: whole, and failing that its fixed part alone. The
 * seam a test fails a copy through; {@link EventCopy} is the layer's own.
 */
interface EventCopying {
    /** Copies every handle, number, text and array of an event of a known kind. */
    CoreEvent whole(MemorySegment event, long sequence);

    /** Copies every handle, kind, code and number, each text the core gave replaced by the layer's fixed sentence. */
    CoreEvent fixedPart(MemorySegment event, long sequence);
}
