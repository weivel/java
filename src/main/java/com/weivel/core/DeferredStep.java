package com.weivel.core;

/**
 * A step to run once every event drained so far has been dispatched. It is an entry of the dispatch queue like an
 * event, run by the one thread dispatching, in queue order and outside the layer lock, so it may call into the core.
 * A failure it answers or throws goes to the error sink.
 */
@FunctionalInterface
public interface DeferredStep {
    /**
     * Runs the step.
     *
     * @return whether it did its work; {@link Status#FAILED} is reported to the error sink
     */
    Status run();

    /** What a deferred step answers. */
    enum Status {
        /** The step did its work. */
        DONE,
        /** The step failed, which the error sink is told. */
        FAILED
    }

    /** Where a step handed to the dispatch queue stands once the call that deferred it returns. */
    enum Deferral {
        /** The deferring thread was the dispatcher, so the step has run, or a destroy made meanwhile dropped it. */
        RAN_HERE,
        /** A dispatch already running, or the thread arming the timer, runs it, possibly later. */
        LEFT_TO_THE_DISPATCH,
        /** The core was destroyed, so the step will never run. */
        DESTROYED
    }
}
