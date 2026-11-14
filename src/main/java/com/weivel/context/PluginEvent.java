package com.weivel.context;

import com.weivel.core.Refusal;
import java.util.Objects;
import java.util.Optional;

/** What the core reports about a plugin, as its listeners hear it. */
public sealed interface PluginEvent permits PluginEvent.Revoked, PluginEvent.ReachRefused, PluginEvent.Degraded {

    /**
     * A service or implementation the plugin held was withdrawn, so it must stop using it.
     *
     * @param object the object it had been handed, the provider's own
     */
    record Revoked(Object object) implements PluginEvent {
        /**
         * Checks that the object is present.
         *
         * @param object the object
         */
        public Revoked {
            Objects.requireNonNull(object, "object");
        }
    }

    /**
     * The core refused the plugin a service or capability it may not reach.
     *
     * @param kind a reach kind of the core's header: a service or a capability
     * @param id the id it reached for
     * @param refusal why, as the core said it
     */
    record ReachRefused(int kind, String id, Refusal refusal) implements PluginEvent {
        /**
         * Checks that every part is present.
         *
         * @param kind the reach kind
         * @param id the id
         * @param refusal the refusal
         */
        public ReachRefused {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(refusal, "refusal");
        }
    }

    /**
     * A constrained want settled with a provider that never met its constraints, its budget spent.
     *
     * @param id the service id it wanted
     * @param service the provider's own object it settled with
     * @param unmetConstraint the constraint it accepted unmet, as the core chose it
     */
    record Degraded(String id, Object service, Optional<String> unmetConstraint) implements PluginEvent {
        /**
         * Checks that every part is present.
         *
         * @param id the service id
         * @param service the service
         * @param unmetConstraint the unmet constraint, or empty
         */
        public Degraded {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(unmetConstraint, "unmetConstraint");
        }
    }
}
