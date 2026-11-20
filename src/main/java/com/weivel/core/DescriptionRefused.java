package com.weivel.core;

/** A description that does not hold for this platform's layout, or this binding cannot bind: the caller's mistake. */
final class DescriptionRefused extends RuntimeException {
    private static final long serialVersionUID = 1L;

    DescriptionRefused(String definition, String detail) {
        super("the description of " + definition + " cannot be bound: " + detail);
    }
}
