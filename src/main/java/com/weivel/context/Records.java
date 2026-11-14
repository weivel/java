package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.handles.Handles;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** The layer's objects a context keeps in the table under the handles the core answered its plugin. */
final class Records {
    private Records() {
    }

    /** A service the plugin registered: the provider's own object and what answers its capacity questions. */
    static final class Service {
        private final String id;
        private final Object service;
        private final Optional<CapacityAnswerer> answerer;

        Service(String id, Object service, Optional<CapacityAnswerer> answerer) {
            this.id = id;
            this.service = service;
            this.answerer = answerer;
        }

        public String id() {
            return id;
        }

        public Object service() {
            return service;
        }

        public Optional<CapacityAnswerer> answerer() {
            return answerer;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Service)) {
                return false;
            }
            Service that = (Service) other;
            return Objects.equals(id, that.id)
                    && Objects.equals(service, that.service)
                    && Objects.equals(answerer, that.answerer);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(id);
            hash = 31 * hash + Objects.hashCode(service);
            hash = 31 * hash + Objects.hashCode(answerer);
            return hash;
        }

        @Override
        public String toString() {
            return "Service[id=" + id + ", service=" + service + ", answerer=" + answerer + "]";
        }
    }

    /** An implementation of a capability the plugin provided: the provider's own object. */
    static final class Implementation {
        private final String id;
        private final Object implementation;

        Implementation(String id, Object implementation) {
            this.id = id;
            this.implementation = implementation;
        }

        public String id() {
            return id;
        }

        public Object implementation() {
            return implementation;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Implementation)) {
                return false;
            }
            Implementation that = (Implementation) other;
            return Objects.equals(id, that.id)
                    && Objects.equals(implementation, that.implementation);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(id);
            hash = 31 * hash + Objects.hashCode(implementation);
            return hash;
        }

        @Override
        public String toString() {
            return "Implementation[id=" + id + ", implementation=" + implementation + "]";
        }
    }

    /** A watch of a service. */
    static final class ServiceWatch {
        private final ServiceWatcher watcher;

        ServiceWatch(ServiceWatcher watcher) {
            this.watcher = watcher;
        }

        public ServiceWatcher watcher() {
            return watcher;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ServiceWatch)) {
                return false;
            }
            ServiceWatch that = (ServiceWatch) other;
            return Objects.equals(watcher, that.watcher);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(watcher);
            return hash;
        }

        @Override
        public String toString() {
            return "ServiceWatch[watcher=" + watcher + "]";
        }
    }

    /** A watch of a capability. */
    static final class CapabilityWatch {
        private final CapabilityWatcher watcher;

        CapabilityWatch(CapabilityWatcher watcher) {
            this.watcher = watcher;
        }

        public CapabilityWatcher watcher() {
            return watcher;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CapabilityWatch)) {
                return false;
            }
            CapabilityWatch that = (CapabilityWatch) other;
            return Objects.equals(watcher, that.watcher);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(watcher);
            return hash;
        }

        @Override
        public String toString() {
            return "CapabilityWatch[watcher=" + watcher + "]";
        }
    }

    /**
     * A want still waiting: what its settlement completes, and room for the one holding a settlement with a service
     * hands over, taken before the core was asked.
     */
    static final class Want {
        private final String id;
        private final CompletableFuture<Answer<Object>> settlement;
        private final Handles.Reservation holdingRoom;

        Want(String id, CompletableFuture<Answer<Object>> settlement, Handles.Reservation holdingRoom) {
            this.id = id;
            this.settlement = settlement;
            this.holdingRoom = holdingRoom;
        }

        public String id() {
            return id;
        }

        public CompletableFuture<Answer<Object>> settlement() {
            return settlement;
        }

        public Handles.Reservation holdingRoom() {
            return holdingRoom;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Want)) {
                return false;
            }
            Want that = (Want) other;
            return Objects.equals(id, that.id)
                    && Objects.equals(settlement, that.settlement)
                    && Objects.equals(holdingRoom, that.holdingRoom);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(id);
            hash = 31 * hash + Objects.hashCode(settlement);
            hash = 31 * hash + Objects.hashCode(holdingRoom);
            return hash;
        }

        @Override
        public String toString() {
            return "Want[id=" + id + ", settlement=" + settlement + ", holdingRoom=" + holdingRoom + "]";
        }
    }
}
