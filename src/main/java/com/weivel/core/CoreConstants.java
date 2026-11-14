package com.weivel.core;

/** The values of the core's header that a Java caller reads in events, refusals and answers, under its names. */
public final class CoreConstants {
    /** The host itself: the reserved handle {@code WEIVEL_HOST}, which no plugin is ever issued. */
    public static final long HOST = -1L;

    /** {@code WEIVEL_LOG_DEBUG}. */
    public static final int LOG_DEBUG = 1;
    /** {@code WEIVEL_LOG_INFO}. */
    public static final int LOG_INFO = 2;
    /** {@code WEIVEL_LOG_WARNING}. */
    public static final int LOG_WARNING = 3;
    /** {@code WEIVEL_LOG_ERROR}. */
    public static final int LOG_ERROR = 4;

    /** {@code WEIVEL_EVENT_LOG}. */
    public static final int EVENT_LOG = 1;
    /** {@code WEIVEL_EVENT_REFUSAL}. */
    public static final int EVENT_REFUSAL = 2;
    /** {@code WEIVEL_EVENT_WANT_SETTLED}. */
    public static final int EVENT_WANT_SETTLED = 3;
    /** {@code WEIVEL_EVENT_WATCH}. */
    public static final int EVENT_WATCH = 4;
    /** {@code WEIVEL_EVENT_REVOKED}. */
    public static final int EVENT_REVOKED = 5;
    /** {@code WEIVEL_EVENT_REACH_REFUSED}. */
    public static final int EVENT_REACH_REFUSED = 6;
    /** {@code WEIVEL_EVENT_CAPACITY_WANTED}. */
    public static final int EVENT_CAPACITY_WANTED = 7;
    /** {@code WEIVEL_EVENT_CAPABILITY}. */
    public static final int EVENT_CAPABILITY = 8;
    /** {@code WEIVEL_EVENT_DEADLINE}. */
    public static final int EVENT_DEADLINE = 9;

    /** {@code WEIVEL_REFUSAL_NONE}. */
    public static final int REFUSAL_NONE = 0;
    /** {@code WEIVEL_REFUSAL_MALFORMED_CALL}. */
    public static final int REFUSAL_MALFORMED_CALL = 1;
    /** {@code WEIVEL_REFUSAL_STALE_HANDLE}. */
    public static final int REFUSAL_STALE_HANDLE = 2;
    /** {@code WEIVEL_REFUSAL_MALFORMED_MANIFEST}. */
    public static final int REFUSAL_MALFORMED_MANIFEST = 3;
    /** {@code WEIVEL_REFUSAL_INCOMPATIBLE}. */
    public static final int REFUSAL_INCOMPATIBLE = 4;
    /** {@code WEIVEL_REFUSAL_NAME_TAKEN}. */
    public static final int REFUSAL_NAME_TAKEN = 5;
    /** {@code WEIVEL_REFUSAL_UNMET_NEED}. */
    public static final int REFUSAL_UNMET_NEED = 6;
    /** {@code WEIVEL_REFUSAL_REPEATED_PLUGIN}. */
    public static final int REFUSAL_REPEATED_PLUGIN = 7;
    /** {@code WEIVEL_REFUSAL_PLUGIN_STATE}. */
    public static final int REFUSAL_PLUGIN_STATE = 8;
    /** {@code WEIVEL_REFUSAL_IDENTITY_TAKEN}. */
    public static final int REFUSAL_IDENTITY_TAKEN = 9;
    /** {@code WEIVEL_REFUSAL_OUTSIDE_NAMESPACE}. */
    public static final int REFUSAL_OUTSIDE_NAMESPACE = 10;
    /** {@code WEIVEL_REFUSAL_SERVICE_TAKEN}. */
    public static final int REFUSAL_SERVICE_TAKEN = 11;
    /** {@code WEIVEL_REFUSAL_UNDECLARED}. */
    public static final int REFUSAL_UNDECLARED = 12;
    /** {@code WEIVEL_REFUSAL_WRONG_PROVIDER}. */
    public static final int REFUSAL_WRONG_PROVIDER = 13;
    /** {@code WEIVEL_REFUSAL_STOPPED}. */
    public static final int REFUSAL_STOPPED = 14;
    /** {@code WEIVEL_REFUSAL_TIMED_OUT}. */
    public static final int REFUSAL_TIMED_OUT = 15;
    /** {@code WEIVEL_REFUSAL_UNMET_CONSTRAINT}. */
    public static final int REFUSAL_UNMET_CONSTRAINT = 16;
    /** {@code WEIVEL_REFUSAL_NOT_SERVED}. */
    public static final int REFUSAL_NOT_SERVED = 17;
    /** {@code WEIVEL_REFUSAL_ACTIVATION_MISMATCH}. */
    public static final int REFUSAL_ACTIVATION_MISMATCH = 18;
    /** {@code WEIVEL_REFUSAL_PROVIDED_TWICE}. */
    public static final int REFUSAL_PROVIDED_TWICE = 19;

    /** {@code WEIVEL_CHANGE_REGISTERED}. */
    public static final int CHANGE_REGISTERED = 1;
    /** {@code WEIVEL_CHANGE_UNREGISTERED}. */
    public static final int CHANGE_UNREGISTERED = 2;

    /** {@code WEIVEL_REACH_SERVICE}. */
    public static final int REACH_SERVICE = 1;
    /** {@code WEIVEL_REACH_CAPABILITY}. */
    public static final int REACH_CAPABILITY = 2;

    /** {@code WEIVEL_HANDLE_WAITING}. */
    public static final int HANDLE_WAITING = 1;
    /** {@code WEIVEL_HANDLE_SETTLED}. */
    public static final int HANDLE_SETTLED = 2;
    /** {@code WEIVEL_HANDLE_WITHDRAWN}. */
    public static final int HANDLE_WITHDRAWN = 3;
    /** {@code WEIVEL_HANDLE_WATCHING}. */
    public static final int HANDLE_WATCHING = 4;
    /** {@code WEIVEL_HANDLE_STOPPED}. */
    public static final int HANDLE_STOPPED = 5;

    private CoreConstants() {
    }
}
