package com.weivel.context;

import com.weivel.core.Refusal;
import java.util.Objects;
import java.util.Optional;

/** What the core reports about a plugin, as its listeners hear it. */
public interface PluginEvent {

    /**
     * A service or implementation the plugin held was withdrawn, so it must stop using it.
     */
    final class Revoked implements PluginEvent {
        private final Object object;
        private final boolean awaitsRelease;

        /**
         * Checks that the object is present.
         *
         * @param object the object
         * @param awaitsRelease whether its provider awaits the plugin's release of it
         */
        public Revoked(Object object, boolean awaitsRelease) {
            Objects.requireNonNull(object, "object");
            this.object = object;
            this.awaitsRelease = awaitsRelease;
        }

        /**
         * Answers {@code object}.
         *
         * @return the object it had been handed, the provider's own
         */
        public Object object() {
            return object;
        }

        /**
         * Answers {@code awaitsRelease}.
         *
         * @return whether its provider awaits each holder's release, so the plugin lets go of the object with
         *     {@link PluginContext#releaseHolding} once it no longer uses it or anything it answered
         */
        public boolean awaitsRelease() {
            return awaitsRelease;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Revoked)) {
                return false;
            }
            Revoked that = (Revoked) other;
            return Objects.equals(object, that.object) && awaitsRelease == that.awaitsRelease;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(object);
            hash = 31 * hash + Boolean.hashCode(awaitsRelease);
            return hash;
        }

        @Override
        public String toString() {
            return "Revoked[object=" + object + ", awaitsRelease=" + awaitsRelease + "]";
        }
    }

    /**
     * Nothing holds a service the plugin registered awaiting releases any more, so whatever stood behind it may be
     * freed.
     */
    final class HoldingsReleased implements PluginEvent {
        private final Object service;

        /**
         * Checks that the object is present.
         *
         * @param service the object the plugin registered
         */
        public HoldingsReleased(Object service) {
            this.service = Objects.requireNonNull(service, "service");
        }

        /**
         * Answers {@code service}.
         *
         * @return the object the plugin registered
         */
        public Object service() {
            return service;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof HoldingsReleased && service == ((HoldingsReleased) other).service;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(service);
        }

        @Override
        public String toString() {
            return "HoldingsReleased[service=" + service + "]";
        }
    }

    /**
     * The core refused the plugin a service or capability it may not reach.
     */
    final class ReachRefused implements PluginEvent {
        private final int kind;
        private final String id;
        private final Refusal refusal;

        /**
         * Checks that every part is present.
         *
         * @param kind the reach kind
         * @param id the id
         * @param refusal the refusal
         */
        public ReachRefused(int kind, String id, Refusal refusal) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(refusal, "refusal");
            this.kind = kind;
            this.id = id;
            this.refusal = refusal;
        }

        /**
         * Answers {@code kind}.
         *
         * @return a reach kind of the core's header: a service or a capability
         */
        public int kind() {
            return kind;
        }

        /**
         * Answers {@code id}.
         *
         * @return the id it reached for
         */
        public String id() {
            return id;
        }

        /**
         * Answers {@code refusal}.
         *
         * @return why, as the core said it
         */
        public Refusal refusal() {
            return refusal;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ReachRefused)) {
                return false;
            }
            ReachRefused that = (ReachRefused) other;
            return kind == that.kind
                    && Objects.equals(id, that.id)
                    && Objects.equals(refusal, that.refusal);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Integer.hashCode(kind);
            hash = 31 * hash + Objects.hashCode(id);
            hash = 31 * hash + Objects.hashCode(refusal);
            return hash;
        }

        @Override
        public String toString() {
            return "ReachRefused[kind=" + kind + ", id=" + id + ", refusal=" + refusal + "]";
        }
    }

    /**
     * A constrained want settled with a provider that never met its constraints, its budget spent.
     */
    final class Degraded implements PluginEvent {
        private final String id;
        private final Object service;
        private final Optional<String> unmetConstraint;

        /**
         * Checks that every part is present.
         *
         * @param id the service id
         * @param service the service
         * @param unmetConstraint the unmet constraint, or empty
         */
        public Degraded(String id, Object service, Optional<String> unmetConstraint) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(unmetConstraint, "unmetConstraint");
            this.id = id;
            this.service = service;
            this.unmetConstraint = unmetConstraint;
        }

        /**
         * Answers {@code id}.
         *
         * @return the service id it wanted
         */
        public String id() {
            return id;
        }

        /**
         * Answers {@code service}.
         *
         * @return the provider's own object it settled with
         */
        public Object service() {
            return service;
        }

        /**
         * Answers {@code unmetConstraint}.
         *
         * @return the constraint it accepted unmet, as the core chose it
         */
        public Optional<String> unmetConstraint() {
            return unmetConstraint;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Degraded)) {
                return false;
            }
            Degraded that = (Degraded) other;
            return Objects.equals(id, that.id)
                    && Objects.equals(service, that.service)
                    && Objects.equals(unmetConstraint, that.unmetConstraint);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(id);
            hash = 31 * hash + Objects.hashCode(service);
            hash = 31 * hash + Objects.hashCode(unmetConstraint);
            return hash;
        }

        @Override
        public String toString() {
            return "Degraded[id=" + id + ", service=" + service + ", unmetConstraint=" + unmetConstraint + "]";
        }
    }
}
