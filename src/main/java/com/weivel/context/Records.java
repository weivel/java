package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.handles.Handles;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** The layer's objects a context keeps in the table under the handles the core answered its plugin. */
final class Records {
    private Records() {
    }

    /** A service the plugin registered: the provider's own object and what answers its capacity questions. */
    record Service(String id, Object service, Optional<CapacityAnswerer> answerer) {
    }

    /** An implementation of a capability the plugin provided: the provider's own object. */
    record Implementation(String id, Object implementation) {
    }

    /** A watch of a service. */
    record ServiceWatch(ServiceWatcher watcher) {
    }

    /** A watch of a capability. */
    record CapabilityWatch(CapabilityWatcher watcher) {
    }

    /**
     * A want still waiting: what its settlement completes, and room for the one holding a settlement with a service
     * hands over, taken before the core was asked.
     */
    record Want(String id, CompletableFuture<Answer<Object>> settlement, Handles.Reservation holdingRoom) {
    }
}
