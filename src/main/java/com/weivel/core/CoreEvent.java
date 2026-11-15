package com.weivel.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One event of the core, copied into Java values as it was drained and released in the core before the next was
 * drained. One record per kind the layer knows; an event of a kind it does not know is released and heard by no one.
 *
 * <p>An event is heard even when its texts or arrays could not be copied, because a settlement or a revocation that
 * went unheard would never be heard again. It is then {@linkplain #incomplete() incomplete}: every handle, kind, code
 * and number is exact, each text the core gave reads {@link #TEXT_NOT_COPIED}, a text the core left out stays empty,
 * and a capacity question carries no constraints at all.
 */
public interface CoreEvent {

    /** What stands in place of a text in an event the layer could not copy whole. */
    String TEXT_NOT_COPIED = "the layer could not copy this text";

    /**
     * The event's place in the order this core's events were drained, counted from 1.
     *
     * @return the drain sequence number
     */
    long sequence();

    /**
     * Whether the event's texts or arrays could not be copied, so each text reads {@link #TEXT_NOT_COPIED}.
     *
     * @return whether the event is incomplete
     */
    boolean incomplete();

    /**
     * A diagnostic of the core's ({@code WEIVEL_EVENT_LOG}).
     */
    final class LogEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final int level;
        private final String text;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete whether the text could not be copied
         * @param level a log level of the core's header
         * @param text the diagnostic
         */
        public LogEvent(long sequence, boolean incomplete, int level, String text) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.level = level;
            this.text = text;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return whether the text could not be copied
         */
        public boolean incomplete() {
            return incomplete;
        }

        /**
         * Answers {@code level}.
         *
         * @return a log level of the core's header
         */
        public int level() {
            return level;
        }

        /**
         * Answers {@code text}.
         *
         * @return the diagnostic
         */
        public String text() {
            return text;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LogEvent)) {
                return false;
            }
            LogEvent that = (LogEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && level == that.level
                    && Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Integer.hashCode(level);
            hash = 31 * hash + Objects.hashCode(text);
            return hash;
        }

        @Override
        public String toString() {
            return "LogEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", level=" + level + ", text="
                    + text + "]";
        }
    }

    /**
     * A refusal ({@code WEIVEL_EVENT_REFUSAL}).
     */
    final class RefusalEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final Refusal refusal;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete whether the texts could not be copied
         * @param refusal the refusal
         */
        public RefusalEvent(long sequence, boolean incomplete, Refusal refusal) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.refusal = refusal;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return whether the texts could not be copied
         */
        public boolean incomplete() {
            return incomplete;
        }

        /**
         * Answers {@code refusal}.
         *
         * @return the refusal
         */
        public Refusal refusal() {
            return refusal;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RefusalEvent)) {
                return false;
            }
            RefusalEvent that = (RefusalEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && Objects.equals(refusal, that.refusal);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Objects.hashCode(refusal);
            return hash;
        }

        @Override
        public String toString() {
            return "RefusalEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", refusal=" + refusal + "]";
        }
    }

    /**
     * A want settled, with a service or with a refusal, exactly once ({@code WEIVEL_EVENT_WANT_SETTLED}).
     */
    final class WantSettledEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final long want;
        private final long plugin;
        private final long implementation;
        private final Optional<Refusal> refusal;
        private final boolean degraded;
        private final Optional<String> unmetConstraint;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete whether the texts could not be copied
         * @param want the want's handle
         * @param plugin the plugin that wanted
         * @param implementation the registration it settled with, 0 when it was rejected
         * @param refusal why it was rejected; empty when it settled with a service
         * @param degraded whether a constrained want accepted a provider that never met its constraints
         * @param unmetConstraint for a degraded want, the constraint it accepted unmet; empty otherwise
         */
        public WantSettledEvent(long sequence, boolean incomplete, long want, long plugin, long implementation,
                Optional<Refusal> refusal, boolean degraded, Optional<String> unmetConstraint) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.want = want;
            this.plugin = plugin;
            this.implementation = implementation;
            this.refusal = refusal;
            this.degraded = degraded;
            this.unmetConstraint = unmetConstraint;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return whether the texts could not be copied
         */
        public boolean incomplete() {
            return incomplete;
        }

        /**
         * Answers {@code want}.
         *
         * @return the want's handle
         */
        public long want() {
            return want;
        }

        /**
         * Answers {@code plugin}.
         *
         * @return the plugin that wanted
         */
        public long plugin() {
            return plugin;
        }

        /**
         * Answers {@code implementation}.
         *
         * @return the registration it settled with, 0 when it was rejected
         */
        public long implementation() {
            return implementation;
        }

        /**
         * Answers {@code refusal}.
         *
         * @return why it was rejected; empty when it settled with a service
         */
        public Optional<Refusal> refusal() {
            return refusal;
        }

        /**
         * Answers {@code degraded}.
         *
         * @return whether a constrained want accepted a provider that never met its constraints
         */
        public boolean degraded() {
            return degraded;
        }

        /**
         * Answers {@code unmetConstraint}.
         *
         * @return for a degraded want, the constraint it accepted unmet; empty otherwise
         */
        public Optional<String> unmetConstraint() {
            return unmetConstraint;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WantSettledEvent)) {
                return false;
            }
            WantSettledEvent that = (WantSettledEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && want == that.want
                    && plugin == that.plugin
                    && implementation == that.implementation
                    && Objects.equals(refusal, that.refusal)
                    && degraded == that.degraded
                    && Objects.equals(unmetConstraint, that.unmetConstraint);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Long.hashCode(want);
            hash = 31 * hash + Long.hashCode(plugin);
            hash = 31 * hash + Long.hashCode(implementation);
            hash = 31 * hash + Objects.hashCode(refusal);
            hash = 31 * hash + Boolean.hashCode(degraded);
            hash = 31 * hash + Objects.hashCode(unmetConstraint);
            return hash;
        }

        @Override
        public String toString() {
            return "WantSettledEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", want=" + want
                    + ", plugin=" + plugin + ", implementation=" + implementation + ", refusal=" + refusal
                    + ", degraded=" + degraded + ", unmetConstraint=" + unmetConstraint + "]";
        }
    }

    /**
     * A watched service was registered or unregistered ({@code WEIVEL_EVENT_WATCH}).
     */
    final class WatchEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final long watch;
        private final long plugin;
        private final long implementation;
        private final int change;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete always false, since the event carries no text
         * @param watch the watch's handle
         * @param plugin the plugin that watches
         * @param implementation the registration
         * @param change a change of the core's header: registered or unregistered
         */
        public WatchEvent(long sequence, boolean incomplete, long watch, long plugin, long implementation, int change) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.watch = watch;
            this.plugin = plugin;
            this.implementation = implementation;
            this.change = change;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return always false, since the event carries no text
         */
        public boolean incomplete() {
            return incomplete;
        }

        /**
         * Answers {@code watch}.
         *
         * @return the watch's handle
         */
        public long watch() {
            return watch;
        }

        /**
         * Answers {@code plugin}.
         *
         * @return the plugin that watches
         */
        public long plugin() {
            return plugin;
        }

        /**
         * Answers {@code implementation}.
         *
         * @return the registration
         */
        public long implementation() {
            return implementation;
        }

        /**
         * Answers {@code change}.
         *
         * @return a change of the core's header: registered or unregistered
         */
        public int change() {
            return change;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WatchEvent)) {
                return false;
            }
            WatchEvent that = (WatchEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && watch == that.watch
                    && plugin == that.plugin
                    && implementation == that.implementation
                    && change == that.change;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Long.hashCode(watch);
            hash = 31 * hash + Long.hashCode(plugin);
            hash = 31 * hash + Long.hashCode(implementation);
            hash = 31 * hash + Integer.hashCode(change);
            return hash;
        }

        @Override
        public String toString() {
            return "WatchEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", watch=" + watch + ", plugin="
                    + plugin + ", implementation=" + implementation + ", change=" + change + "]";
        }
    }

    /**
     * Something a plugin held was withdrawn ({@code WEIVEL_EVENT_REVOKED}).
     */
    final class RevokedEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final long plugin;
        private final long implementation;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete always false, since the event carries no text
         * @param plugin the plugin that held it, possibly one already forgotten
         * @param implementation the registration or capability implementation that is gone
         */
        public RevokedEvent(long sequence, boolean incomplete, long plugin, long implementation) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.plugin = plugin;
            this.implementation = implementation;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return always false, since the event carries no text
         */
        public boolean incomplete() {
            return incomplete;
        }

        /**
         * Answers {@code plugin}.
         *
         * @return the plugin that held it, possibly one already forgotten
         */
        public long plugin() {
            return plugin;
        }

        /**
         * Answers {@code implementation}.
         *
         * @return the registration or capability implementation that is gone
         */
        public long implementation() {
            return implementation;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RevokedEvent)) {
                return false;
            }
            RevokedEvent that = (RevokedEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && plugin == that.plugin
                    && implementation == that.implementation;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Long.hashCode(plugin);
            hash = 31 * hash + Long.hashCode(implementation);
            return hash;
        }

        @Override
        public String toString() {
            return "RevokedEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", plugin=" + plugin
                    + ", implementation=" + implementation + "]";
        }
    }

    /**
     * A plugin reached for what it may not ({@code WEIVEL_EVENT_REACH_REFUSED}).
     */
    final class ReachRefusedEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final int kind;
        private final long plugin;
        private final String id;
        private final Refusal refusal;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete whether the texts could not be copied
         * @param kind a reach kind of the core's header: a service or a capability
         * @param plugin the plugin that reached
         * @param id the id it reached for
         * @param refusal why it was refused
         */
        public ReachRefusedEvent(long sequence, boolean incomplete, int kind, long plugin, String id, Refusal refusal) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.kind = kind;
            this.plugin = plugin;
            this.id = id;
            this.refusal = refusal;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return whether the texts could not be copied
         */
        public boolean incomplete() {
            return incomplete;
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
         * Answers {@code plugin}.
         *
         * @return the plugin that reached
         */
        public long plugin() {
            return plugin;
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
         * @return why it was refused
         */
        public Refusal refusal() {
            return refusal;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ReachRefusedEvent)) {
                return false;
            }
            ReachRefusedEvent that = (ReachRefusedEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && kind == that.kind
                    && plugin == that.plugin
                    && Objects.equals(id, that.id)
                    && Objects.equals(refusal, that.refusal);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Integer.hashCode(kind);
            hash = 31 * hash + Long.hashCode(plugin);
            hash = 31 * hash + Objects.hashCode(id);
            hash = 31 * hash + Objects.hashCode(refusal);
            return hash;
        }

        @Override
        public String toString() {
            return "ReachRefusedEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", kind=" + kind
                    + ", plugin=" + plugin + ", id=" + id + ", refusal=" + refusal + "]";
        }
    }

    /**
     * A constrained want asks a provider's object a question ({@code WEIVEL_EVENT_CAPACITY_WANTED}). An incomplete
     * one carries no constraints and must be left unanswered.
     */
    final class CapacityWantedEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final long want;
        private final long plugin;
        private final long registration;
        private final long request;
        private final int question;
        private final List<NamedNumber> constraints;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete whether the constraints could not be copied
         * @param want the want asking
         * @param plugin the plugin that wants
         * @param registration the provider's registration asked
         * @param request the request an answer names
         * @param question a capacity question of the core's header
         * @param constraints the consumer's constraints, empty for an unconstrained ask
         */
        public CapacityWantedEvent(long sequence, boolean incomplete, long want, long plugin, long registration,
                long request, int question, List<NamedNumber> constraints) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.want = want;
            this.plugin = plugin;
            this.registration = registration;
            this.request = request;
            this.question = question;
            this.constraints = constraints;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return whether the constraints could not be copied
         */
        public boolean incomplete() {
            return incomplete;
        }

        /**
         * Answers {@code want}.
         *
         * @return the want asking
         */
        public long want() {
            return want;
        }

        /**
         * Answers {@code plugin}.
         *
         * @return the plugin that wants
         */
        public long plugin() {
            return plugin;
        }

        /**
         * Answers {@code registration}.
         *
         * @return the provider's registration asked
         */
        public long registration() {
            return registration;
        }

        /**
         * Answers {@code request}.
         *
         * @return the request an answer names
         */
        public long request() {
            return request;
        }

        /**
         * Answers {@code question}.
         *
         * @return a capacity question of the core's header
         */
        public int question() {
            return question;
        }

        /**
         * Answers {@code constraints}.
         *
         * @return the consumer's constraints, empty for an unconstrained ask
         */
        public List<NamedNumber> constraints() {
            return constraints;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CapacityWantedEvent)) {
                return false;
            }
            CapacityWantedEvent that = (CapacityWantedEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && want == that.want
                    && plugin == that.plugin
                    && registration == that.registration
                    && request == that.request
                    && question == that.question
                    && Objects.equals(constraints, that.constraints);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Long.hashCode(want);
            hash = 31 * hash + Long.hashCode(plugin);
            hash = 31 * hash + Long.hashCode(registration);
            hash = 31 * hash + Long.hashCode(request);
            hash = 31 * hash + Integer.hashCode(question);
            hash = 31 * hash + Objects.hashCode(constraints);
            return hash;
        }

        @Override
        public String toString() {
            return "CapacityWantedEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", want=" + want
                    + ", plugin=" + plugin + ", registration=" + registration + ", request=" + request + ", question="
                    + question + ", constraints=" + constraints + "]";
        }
    }

    /**
     * A watched capability gained or lost a verified implementation ({@code WEIVEL_EVENT_CAPABILITY}).
     */
    final class CapabilityEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final long watch;
        private final long plugin;
        private final long implementation;
        private final long provider;
        private final int change;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete always false, since the event carries no text
         * @param watch the watch's handle
         * @param plugin the plugin that watches
         * @param implementation the implementation that joined or left
         * @param provider the plugin providing it
         * @param change a capability change of the core's header: provided or withdrawn
         */
        public CapabilityEvent(long sequence, boolean incomplete, long watch, long plugin, long implementation,
                long provider, int change) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.watch = watch;
            this.plugin = plugin;
            this.implementation = implementation;
            this.provider = provider;
            this.change = change;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return always false, since the event carries no text
         */
        public boolean incomplete() {
            return incomplete;
        }

        /**
         * Answers {@code watch}.
         *
         * @return the watch's handle
         */
        public long watch() {
            return watch;
        }

        /**
         * Answers {@code plugin}.
         *
         * @return the plugin that watches
         */
        public long plugin() {
            return plugin;
        }

        /**
         * Answers {@code implementation}.
         *
         * @return the implementation that joined or left
         */
        public long implementation() {
            return implementation;
        }

        /**
         * Answers {@code provider}.
         *
         * @return the plugin providing it
         */
        public long provider() {
            return provider;
        }

        /**
         * Answers {@code change}.
         *
         * @return a capability change of the core's header: provided or withdrawn
         */
        public int change() {
            return change;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CapabilityEvent)) {
                return false;
            }
            CapabilityEvent that = (CapabilityEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && watch == that.watch
                    && plugin == that.plugin
                    && implementation == that.implementation
                    && provider == that.provider
                    && change == that.change;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Long.hashCode(watch);
            hash = 31 * hash + Long.hashCode(plugin);
            hash = 31 * hash + Long.hashCode(implementation);
            hash = 31 * hash + Long.hashCode(provider);
            hash = 31 * hash + Integer.hashCode(change);
            return hash;
        }

        @Override
        public String toString() {
            return "CapabilityEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", watch=" + watch
                    + ", plugin=" + plugin + ", implementation=" + implementation + ", provider=" + provider
                    + ", change=" + change + "]";
        }
    }

    /**
     * The core's earliest deadline changed ({@code WEIVEL_EVENT_DEADLINE}).
     */
    final class DeadlineEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final long at;
        private final boolean set;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete always false, since the event carries no text
         * @param at the deadline in the caller's milliseconds, 0 when none is set
         * @param set whether a deadline is set
         */
        public DeadlineEvent(long sequence, boolean incomplete, long at, boolean set) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.at = at;
            this.set = set;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return always false, since the event carries no text
         */
        public boolean incomplete() {
            return incomplete;
        }

        /**
         * Answers {@code at}.
         *
         * @return the deadline in the caller's milliseconds, 0 when none is set
         */
        public long at() {
            return at;
        }

        /**
         * Answers {@code set}.
         *
         * @return whether a deadline is set
         */
        public boolean set() {
            return set;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DeadlineEvent)) {
                return false;
            }
            DeadlineEvent that = (DeadlineEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && at == that.at
                    && set == that.set;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Long.hashCode(at);
            hash = 31 * hash + Boolean.hashCode(set);
            return hash;
        }

        @Override
        public String toString() {
            return "DeadlineEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", at=" + at + ", set=" + set
                    + "]";
        }
    }

    /**
     * Nothing holds a registration made awaiting releases any more ({@code WEIVEL_EVENT_HOLDINGS_RELEASED}).
     */
    final class HoldingsReleasedEvent implements CoreEvent {
        private final long sequence;
        private final boolean incomplete;
        private final long owner;
        private final long registration;

        /**
         * Makes the value.
         *
         * @param sequence the drain sequence number
         * @param incomplete always false, since the event carries no text
         * @param owner the registration's owner, {@code WEIVEL_HOST} or a plugin possibly forgotten since
         * @param registration the registration, already stale as a handle to call with
         */
        public HoldingsReleasedEvent(long sequence, boolean incomplete, long owner, long registration) {
            this.sequence = sequence;
            this.incomplete = incomplete;
            this.owner = owner;
            this.registration = registration;
        }

        /**
         * Answers {@code sequence}.
         *
         * @return the drain sequence number
         */
        public long sequence() {
            return sequence;
        }

        /**
         * Answers {@code incomplete}.
         *
         * @return always false, since the event carries no text
         */
        public boolean incomplete() {
            return incomplete;
        }

        /**
         * Answers {@code owner}.
         *
         * @return the registration's owner, {@code WEIVEL_HOST} or a plugin possibly forgotten since
         */
        public long owner() {
            return owner;
        }

        /**
         * Answers {@code registration}.
         *
         * @return the registration, already stale as a handle to call with
         */
        public long registration() {
            return registration;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HoldingsReleasedEvent)) {
                return false;
            }
            HoldingsReleasedEvent that = (HoldingsReleasedEvent) other;
            return sequence == that.sequence
                    && incomplete == that.incomplete
                    && owner == that.owner
                    && registration == that.registration;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Long.hashCode(sequence);
            hash = 31 * hash + Boolean.hashCode(incomplete);
            hash = 31 * hash + Long.hashCode(owner);
            hash = 31 * hash + Long.hashCode(registration);
            return hash;
        }

        @Override
        public String toString() {
            return "HoldingsReleasedEvent[sequence=" + sequence + ", incomplete=" + incomplete + ", owner=" + owner
                    + ", registration=" + registration + "]";
        }
    }
}
