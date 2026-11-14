package com.weivel.core;

import java.util.Objects;

/** One entry of the dispatch queue: a drained event, or a deferred step. */
interface QueueEntry {

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
    final class StepEntry implements QueueEntry {
        private final DeferredStep step;

        /**
         * Makes the value.
         *
         * @param step its step
         */
        public StepEntry(DeferredStep step) {
            this.step = step;
        }

        /**
         * Answers {@code step}.
         *
         * @return its step
         */
        public DeferredStep step() {
            return step;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StepEntry)) {
                return false;
            }
            StepEntry that = (StepEntry) other;
            return Objects.equals(step, that.step);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(step);
            return hash;
        }

        @Override
        public String toString() {
            return "StepEntry[step=" + step + "]";
        }
    }
}
