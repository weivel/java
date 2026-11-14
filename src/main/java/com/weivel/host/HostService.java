package com.weivel.host;

import com.weivel.core.Answer;
import java.util.function.Supplier;

/** A service the host registered under {@code WEIVEL_HOST}, kept until it unregisters it or is destroyed. */
public final class HostService {
    private final String id;
    private final Supplier<Answer<Void>> unregistration;

    HostService(String id, Supplier<Answer<Void>> unregistration) {
        this.id = id;
        this.unregistration = unregistration;
    }

    /**
     * Answers {@code id}.
     *
     * @return the service id
     */
    public String id() {
        return id;
    }

    /**
     * Unregisters the service, which the core revokes from everyone holding it; an activation plan made after the
     * core took the unregistration no longer counts it as provided by the host.
     *
     * @return nothing beyond its success, or the core's refusal, as for one unregistered already
     */
    public Answer<Void> unregister() {
        return unregistration.get();
    }
}
