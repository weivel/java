package com.weivel.core;

/** What a failure told to the error sink is about. New kinds are only appended. */
public enum FailureKind {
    /** A listener threw while it heard an event. */
    LISTENER,
    /** A deferred step answered a failure or threw. */
    STEP,
    /** An event's texts or arrays could not be copied, so it was heard marked incomplete. */
    EVENT_INCOMPLETE,
    /** Not even an event's handles, kind, codes and numbers could be held, so it was dropped. */
    EVENT_DROPPED,
    /** A defect of the layer: the core called a call the layer built malformed, or the layer failed in its own code. */
    LAYER_DEFECT,
    /** A deadline hook, the clock or the arming of the timer, threw. */
    DEADLINE_HOOK,
    /** The core did not take the time the wrapper handed it for a deadline that fell due, which stays due. */
    DUE_ADVANCE
}
