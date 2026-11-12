package io.github.intisy.ai.js.surface;

/**
 * The two things that can happen to a watched capability.
 *
 * @implNote Lower-case constant names for the reason {@link ServiceEvent} gives.
 */
public enum CapabilityEvent {
    /** A verified implementation joined the watched capability. */
    provide,
    /** An implementation left the watched capability, because its plugin was quarantined or released. */
    withdraw
}
