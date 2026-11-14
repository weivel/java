package com.weivel.core;

/** One entry of the dispatch queue: a drained event, or a deferred step. */
sealed interface QueueEntry permits QueueEntry.EventEntry, QueueEntry.StepEntry {

    /**
     * A drained event, fenced by the deadline serial of the announcement its call drained, 0 when it drained none.
     *
     * @implNote The fence is written under the layer lock before the entry joins the queue, and read under it after.
     */
    final class EventEntry implements QueueEntry {
        private final CoreEvent event;
        private long fence;

        EventEntry(CoreEvent event) {
            this.event = event;
        }

        CoreEvent event() {
            return event;
        }

        long fence() {
            return fence;
        }

        void fenceAt(long serial) {
            fence = serial;
        }
    }

    /** A deferred step. */
    record StepEntry(DeferredStep step) implements QueueEntry {
    }
}
