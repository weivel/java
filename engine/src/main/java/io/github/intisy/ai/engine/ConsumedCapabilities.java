package io.github.intisy.ai.engine;

import java.util.List;

/**
 * One plugin's view of the capabilities it consumes, fenced to the ids its manifest declares.
 *
 * @implNote Answers only implementations whose plugin's activation was verified (invariant 14), so a
 * consumer is never handed one the host is about to quarantine for a declaration mismatch.
 */
public interface ConsumedCapabilities {

    /** Called when an implementation joins or leaves a watched capability. */
    interface Listener {
        /**
         * @param implementation the implementation that joined, or the one that left when {@code provided} is false
         * @param provided true when the implementation just joined, false when it just left
         */
        void changed(Object implementation, boolean provided);
    }

    /**
     * @param id the capability id to read
     * @return the verified implementations in the order their activations were verified, or empty
     *         when there are none or the id is not declared
     */
    List<Object> all(String id);

    /**
     * Hears every implementation that joins or leaves from now on, never the ones already there.
     *
     * @param id the capability id to watch
     * @param listener called on each change
     * @return a handle that stops the watch when cancelled
     */
    Scheduler.Cancellable watch(String id, Listener listener);
}
