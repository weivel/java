package com.weivel.standard;

import java.util.Objects;

/**
 * Why a provider of {@link Config}, {@link Secrets} or {@link Paths} declined, as {@code
 * weivel_standard_service_refusal} carries it: what happened, and what would make the call succeed.
 *
 * @implNote Thrown, where C answers {@code WEIVEL_STATUS_REFUSED}: a Java call answers a value or throws, and a
 * checked exception keeps a consumer from forgetting the refusal. Nothing is released, since the collector frees what
 * the header's per-kind releases free.
 */
public final class ServiceRefusal extends Exception {
    private static final long serialVersionUID = 1L;

    /** What happened. */
    private final String detail;
    /** What would make the call succeed. */
    private final String fix;

    /**
     * Makes the refusal.
     *
     * @param detail what happened
     * @param fix what would make the call succeed
     */
    public ServiceRefusal(String detail, String fix) {
        super(Objects.requireNonNull(detail, "detail"));
        this.detail = detail;
        this.fix = Objects.requireNonNull(fix, "fix");
    }

    /**
     * Answers {@code detail}.
     *
     * @return what happened
     */
    public String detail() {
        return detail;
    }

    /**
     * Answers {@code fix}.
     *
     * @return what would make the call succeed
     */
    public String fix() {
        return fix;
    }

    /**
     * The refusal of an operation appended after a definition's first shape that its provider does not implement.
     *
     * @param definition the definition, as its service is named
     * @param operation the operation, as {@code services.h} names it
     * @return the refusal
     */
    static ServiceRefusal notOffered(String definition, String operation) {
        return new ServiceRefusal("the " + definition + " provider does not offer " + operation,
                "implement " + operation + " in the provider");
    }
}
