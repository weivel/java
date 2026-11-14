package com.weivel.core;

/** How the core declined a call: the statuses of the core's that are neither a success nor an absence. */
public enum RefusalStatus {
    /** The core declined the request ({@code WEIVEL_STATUS_REFUSED}). */
    REFUSED,
    /**
     * A structure the layer's caller built and the layer handed on unchanged was malformed
     * ({@code WEIVEL_STATUS_MALFORMED}): the caller's mistake, reported to no sink.
     */
    MALFORMED,
    /** Memory ran out in the core and nothing changed ({@code WEIVEL_STATUS_OUT_OF_MEMORY}). */
    OUT_OF_MEMORY
}
