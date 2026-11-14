package com.weivel.host;

import com.weivel.context.ServiceRegistration;
import com.weivel.core.Answer;
import java.util.function.Consumer;

/** A service the host registered under {@code WEIVEL_HOST}, kept until it unregisters it or is destroyed. */
public final class HostService {
    private final String id;
    private final ServiceRegistration registration;
    private final Consumer<String> unregistered;

    HostService(String id, ServiceRegistration registration, Consumer<String> unregistered) {
        this.id = id;
        this.registration = registration;
        this.unregistered = unregistered;
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
     * Unregisters the service, which the core revokes from everyone holding it; a later activation plan no longer
     * counts it as provided by the host.
     *
     * @return nothing beyond its success, or the core's refusal, as for one unregistered already
     */
    public Answer<Void> unregister() {
        Answer<Void> answer = registration.unregister();
        if (answer instanceof Answer.Ok) {
            unregistered.accept(id);
        }
        return answer;
    }
}
