package com.weivel.core;

import java.util.List;
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
public sealed interface CoreEvent
        permits CoreEvent.LogEvent, CoreEvent.RefusalEvent, CoreEvent.WantSettledEvent, CoreEvent.WatchEvent,
        CoreEvent.RevokedEvent, CoreEvent.ReachRefusedEvent, CoreEvent.CapacityWantedEvent,
        CoreEvent.CapabilityEvent, CoreEvent.DeadlineEvent {

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
     *
     * @param sequence the drain sequence number
     * @param incomplete whether the text could not be copied
     * @param level a log level of the core's header
     * @param text the diagnostic
     */
    record LogEvent(long sequence, boolean incomplete, int level, String text) implements CoreEvent {
    }

    /**
     * A refusal ({@code WEIVEL_EVENT_REFUSAL}).
     *
     * @param sequence the drain sequence number
     * @param incomplete whether the texts could not be copied
     * @param refusal the refusal
     */
    record RefusalEvent(long sequence, boolean incomplete, Refusal refusal) implements CoreEvent {
    }

    /**
     * A want settled, with a service or with a refusal, exactly once ({@code WEIVEL_EVENT_WANT_SETTLED}).
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
    record WantSettledEvent(long sequence, boolean incomplete, long want, long plugin, long implementation,
                            Optional<Refusal> refusal, boolean degraded, Optional<String> unmetConstraint)
            implements CoreEvent {
    }

    /**
     * A watched service was registered or unregistered ({@code WEIVEL_EVENT_WATCH}).
     *
     * @param sequence the drain sequence number
     * @param incomplete always false, since the event carries no text
     * @param watch the watch's handle
     * @param plugin the plugin that watches
     * @param implementation the registration
     * @param change a change of the core's header: registered or unregistered
     */
    record WatchEvent(long sequence, boolean incomplete, long watch, long plugin, long implementation, int change)
            implements CoreEvent {
    }

    /**
     * Something a plugin held was withdrawn ({@code WEIVEL_EVENT_REVOKED}).
     *
     * @param sequence the drain sequence number
     * @param incomplete always false, since the event carries no text
     * @param plugin the plugin that held it, possibly one already forgotten
     * @param implementation the registration or capability implementation that is gone
     */
    record RevokedEvent(long sequence, boolean incomplete, long plugin, long implementation) implements CoreEvent {
    }

    /**
     * A plugin reached for what it may not ({@code WEIVEL_EVENT_REACH_REFUSED}).
     *
     * @param sequence the drain sequence number
     * @param incomplete whether the texts could not be copied
     * @param kind a reach kind of the core's header: a service or a capability
     * @param plugin the plugin that reached
     * @param id the id it reached for
     * @param refusal why it was refused
     */
    record ReachRefusedEvent(long sequence, boolean incomplete, int kind, long plugin, String id, Refusal refusal)
            implements CoreEvent {
    }

    /**
     * A constrained want asks a provider's object a question ({@code WEIVEL_EVENT_CAPACITY_WANTED}). An incomplete
     * one carries no constraints and must be left unanswered.
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
    record CapacityWantedEvent(long sequence, boolean incomplete, long want, long plugin, long registration,
                               long request, int question, List<NamedNumber> constraints) implements CoreEvent {
    }

    /**
     * A watched capability gained or lost a verified implementation ({@code WEIVEL_EVENT_CAPABILITY}).
     *
     * @param sequence the drain sequence number
     * @param incomplete always false, since the event carries no text
     * @param watch the watch's handle
     * @param plugin the plugin that watches
     * @param implementation the implementation that joined or left
     * @param provider the plugin providing it
     * @param change a capability change of the core's header: provided or withdrawn
     */
    record CapabilityEvent(long sequence, boolean incomplete, long watch, long plugin, long implementation,
                           long provider, int change) implements CoreEvent {
    }

    /**
     * The core's earliest deadline changed ({@code WEIVEL_EVENT_DEADLINE}).
     *
     * @param sequence the drain sequence number
     * @param incomplete always false, since the event carries no text
     * @param at the deadline in the caller's milliseconds, 0 when none is set
     * @param set whether a deadline is set
     */
    record DeadlineEvent(long sequence, boolean incomplete, long at, boolean set) implements CoreEvent {
    }
}
