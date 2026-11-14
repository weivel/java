package com.weivel.handles;

/** What a handle of an entry stands for. */
public enum HandleKind {
    /** A plugin, as {@code weivel_register_manifest} answered it. */
    PLUGIN,
    /** A service's registration, as {@code weivel_register_service} answered it. */
    SERVICE_REGISTRATION,
    /** An implementation of a capability, as {@code weivel_provide_capability} answered it. */
    CAPABILITY_IMPLEMENTATION,
    /** A watch of a service or of a capability. */
    WATCH,
    /** A want. */
    WANT
}
