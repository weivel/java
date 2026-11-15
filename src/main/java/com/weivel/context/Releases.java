package com.weivel.context;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registrations made awaiting releases, kept from the core's answer until their
 * {@code WEIVEL_EVENT_HOLDINGS_RELEASED} is dispatched, and each revoked holding of one, kept from its revocation
 * until its holder lets go of the object or the holder's release, quarantine or forget ends it in the core.
 *
 * @implNote Both are concurrent maps, read and changed from the dispatch and from any thread a plugin calls on, under
 * no lock of the layer's, as {@link ContextState} keeps whatever changes outside its locks.
 */
final class Releases {
    private final Map<Long, Awaiting> awaiting = new ConcurrentHashMap<>();
    private final Map<Holding, Object> revoked = new ConcurrentHashMap<>();

    /** Records a registration the core just answered, from the call's answered step, before any event is dispatched. */
    void await(long registration, long owner, String id, Object service, Runnable released) {
        awaiting.put(registration, new Awaiting(owner, id, service, released));
    }

    boolean awaits(long registration) {
        return awaiting.containsKey(registration);
    }

    /** Takes the registration whose last holding ended: nothing names it from here on. */
    Optional<Awaiting> released(long registration) {
        return Optional.ofNullable(awaiting.remove(registration));
    }

    void keepRevoked(long holder, long registration, Object object) {
        revoked.put(new Holding(holder, registration), object);
    }

    /**
     * Takes the revoked holding {@code holder} keeps of {@code registration}, so it is taken once: the registration
     * names it, since one object may be registered under several registrations awaiting releases.
     *
     * @return whether the holder kept such a holding, of that very object
     */
    boolean takeRevoked(long holder, long registration, Object object) {
        return revoked.remove(new Holding(holder, registration), object);
    }

    /** How many revoked holdings are kept: what a test of this package reads. */
    int revokedCount() {
        return revoked.size();
    }

    /** Drops every revoked holding of {@code holder}, which the core ended with the holder's release or forget. */
    void dropRevokedOf(long holder) {
        Iterator<Holding> holdings = revoked.keySet().iterator();
        while (holdings.hasNext()) {
            if (holdings.next().holder == holder) {
                holdings.remove();
            }
        }
    }

    /**
     * A registration awaiting releases: its owner, its id, the object it registered, and what runs once nothing holds
     * it.
     */
    static final class Awaiting {
        private final long owner;
        private final String id;
        private final Object service;
        private final Runnable released;

        Awaiting(long owner, String id, Object service, Runnable released) {
            this.owner = owner;
            this.id = id;
            this.service = service;
            this.released = released;
        }

        long owner() {
            return owner;
        }

        String id() {
            return id;
        }

        Object service() {
            return service;
        }

        Runnable released() {
            return released;
        }
    }

    /** One holder's holding of one registration, the pair the core keys it by. */
    private static final class Holding {
        private final long holder;
        private final long registration;

        Holding(long holder, long registration) {
            this.holder = holder;
            this.registration = registration;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Holding)) {
                return false;
            }
            Holding that = (Holding) other;
            return holder == that.holder && registration == that.registration;
        }

        @Override
        public int hashCode() {
            return Objects.hash(holder, registration);
        }
    }
}
