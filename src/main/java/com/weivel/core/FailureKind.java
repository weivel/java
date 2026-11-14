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
    DUE_ADVANCE,
    /** A plugin's listener, watcher or capacity answerer threw. */
    PLUGIN_CALLBACK,
    /** The host's log threw. */
    LOG,
    /** The core answered a handle the layer holds no object for, so it was left out. */
    OBJECT_UNKNOWN,
    /** A capacity question was left unanswered: the core did not take its answer, or no clock could time it. */
    CAPACITY_UNANSWERED,
    /** A plugin's deactivate threw, answered no stage, or its stage completed exceptionally; the release went on. */
    PLUGIN_DEACTIVATE,
    /** A C library the host loaded as host services could not be closed; the host went on. */
    LIBRARY_UNLOAD
}
