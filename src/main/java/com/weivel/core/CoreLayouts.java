package com.weivel.core;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;

/** The core's structs the {@code core} package hands in or reads back, each named as the core's header names it. */
final class CoreLayouts {
    static final StructLayout STRINGS = MemoryLayout.structLayout(
            ADDRESS.withName("items"),
            JAVA_INT.withName("count"),
            MemoryLayout.paddingLayout(4)).withName("weivel_strings");

    static final StructLayout CONSTRAINT_ENTRY = MemoryLayout.structLayout(
            JAVA_INT.withName("size"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("constraint_name"),
            ADDRESS.withName("attribute_name"),
            JAVA_INT.withName("bound"),
            MemoryLayout.paddingLayout(4)).withName("weivel_constraint_entry");

    static final StructLayout CONSTRAINT_VOCABULARY = MemoryLayout.structLayout(
            ADDRESS.withName("items"),
            JAVA_INT.withName("count"),
            MemoryLayout.paddingLayout(4)).withName("weivel_constraint_vocabulary");

    static final StructLayout CORE_OPTIONS = MemoryLayout.structLayout(
            JAVA_INT.withName("size"),
            JAVA_INT.withName("initial_event_capacity"),
            JAVA_INT.withName("minimum_log_level"),
            JAVA_INT.withName("api"),
            STRINGS.withName("applications"),
            STRINGS.withName("well_known_services"),
            CONSTRAINT_VOCABULARY.withName("constraint_vocabulary")).withName("weivel_core_options");

    static final StructLayout NUMBER = MemoryLayout.structLayout(
            JAVA_INT.withName("size"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("name"),
            JAVA_DOUBLE.withName("value")).withName("weivel_number");

    static final StructLayout NUMBERS = MemoryLayout.structLayout(
            ADDRESS.withName("items"),
            JAVA_INT.withName("count"),
            MemoryLayout.paddingLayout(4)).withName("weivel_numbers");

    static final StructLayout SERVICE_OFFER = MemoryLayout.structLayout(
            JAVA_INT.withName("size"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("id"),
            NUMBERS.withName("attributes")).withName("weivel_service_offer");

    static final StructLayout SERVICE_OFFERS = MemoryLayout.structLayout(
            ADDRESS.withName("items"),
            JAVA_INT.withName("count"),
            MemoryLayout.paddingLayout(4)).withName("weivel_service_offers");

    static final StructLayout SERVICE_WANT = MemoryLayout.structLayout(
            JAVA_INT.withName("size"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("id"),
            ADDRESS.withName("provider"),
            JAVA_INT.withName("optional"),
            JAVA_INT.withName("degradable"),
            NUMBERS.withName("constraints")).withName("weivel_service_want");

    static final StructLayout SERVICE_WANTS = MemoryLayout.structLayout(
            ADDRESS.withName("items"),
            JAVA_INT.withName("count"),
            MemoryLayout.paddingLayout(4)).withName("weivel_service_wants");

    static final StructLayout CAPABILITY_DECLARATIONS = MemoryLayout.structLayout(
            STRINGS.withName("provides"),
            STRINGS.withName("consumes")).withName("weivel_capability_declarations");

    static final StructLayout SERVICE_DECLARATIONS = MemoryLayout.structLayout(
            SERVICE_OFFERS.withName("provides"),
            SERVICE_WANTS.withName("consumes")).withName("weivel_service_declarations");

    static final StructLayout MANIFEST = MemoryLayout.structLayout(
            JAVA_INT.withName("size"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("import_name"),
            JAVA_INT.withName("api"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("entry"),
            STRINGS.withName("targets"),
            CAPABILITY_DECLARATIONS.withName("capabilities"),
            SERVICE_DECLARATIONS.withName("services"),
            STRINGS.withName("permissions"),
            ADDRESS.withName("identity")).withName("weivel_manifest");

    static final StructLayout WANT_OPTIONS = MemoryLayout.structLayout(
            JAVA_INT.withName("size"),
            JAVA_INT.withName("timed"),
            JAVA_LONG.withName("now"),
            JAVA_LONG.withName("timeout_millis"),
            JAVA_INT.withName("budgeted"),
            MemoryLayout.paddingLayout(4),
            JAVA_LONG.withName("budget_millis"),
            JAVA_LONG.withName("poll_millis")).withName("weivel_want_options");

    static final StructLayout PLUGIN_OPTIONS = MemoryLayout.structLayout(
            JAVA_INT.withName("size"),
            MemoryLayout.paddingLayout(4),
            JAVA_LONG.withName("payload")).withName("weivel_plugin_options");

    static final StructLayout REFUSAL = MemoryLayout.structLayout(
            JAVA_INT.withName("code"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("detail"),
            ADDRESS.withName("fix")).withName("weivel_refusal");

    static final StructLayout LOG_EVENT = MemoryLayout.structLayout(
            JAVA_INT.withName("level"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("text")).withName("weivel_log_event");

    static final StructLayout WANT_EVENT = MemoryLayout.structLayout(
            JAVA_LONG.withName("want"),
            JAVA_LONG.withName("plugin"),
            JAVA_LONG.withName("implementation"),
            REFUSAL.withName("refusal"),
            JAVA_INT.withName("degraded"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("subject"),
            JAVA_INT.withName("has_fix"),
            MemoryLayout.paddingLayout(4)).withName("weivel_want_event");

    static final StructLayout WATCH_EVENT = MemoryLayout.structLayout(
            JAVA_LONG.withName("watch"),
            JAVA_LONG.withName("plugin"),
            JAVA_LONG.withName("implementation"),
            JAVA_INT.withName("change"),
            MemoryLayout.paddingLayout(4)).withName("weivel_watch_event");

    static final StructLayout REVOKED_EVENT = MemoryLayout.structLayout(
            JAVA_LONG.withName("plugin"),
            JAVA_LONG.withName("implementation")).withName("weivel_revoked_event");

    static final StructLayout REACH_REFUSAL = MemoryLayout.structLayout(
            JAVA_INT.withName("kind"),
            MemoryLayout.paddingLayout(4),
            JAVA_LONG.withName("plugin"),
            ADDRESS.withName("id"),
            REFUSAL.withName("refusal"),
            ADDRESS.withName("subject"),
            JAVA_INT.withName("has_fix"),
            MemoryLayout.paddingLayout(4)).withName("weivel_reach_refusal");

    static final StructLayout REFUSAL_EVENT = MemoryLayout.structLayout(
            REFUSAL.withName("refusal"),
            ADDRESS.withName("subject"),
            JAVA_INT.withName("has_fix"),
            MemoryLayout.paddingLayout(4)).withName("weivel_refusal_event");

    static final StructLayout CAPACITY_EVENT = MemoryLayout.structLayout(
            JAVA_LONG.withName("want"),
            JAVA_LONG.withName("plugin"),
            JAVA_LONG.withName("registration"),
            JAVA_LONG.withName("request"),
            JAVA_INT.withName("question"),
            MemoryLayout.paddingLayout(4),
            NUMBERS.withName("constraints")).withName("weivel_capacity_event");

    static final StructLayout CAPABILITY_EVENT = MemoryLayout.structLayout(
            JAVA_LONG.withName("watch"),
            JAVA_LONG.withName("plugin"),
            JAVA_LONG.withName("implementation"),
            JAVA_LONG.withName("provider"),
            JAVA_INT.withName("change"),
            MemoryLayout.paddingLayout(4)).withName("weivel_capability_event");

    static final StructLayout DEADLINE_EVENT = MemoryLayout.structLayout(
            JAVA_LONG.withName("at"),
            JAVA_INT.withName("set"),
            MemoryLayout.paddingLayout(4)).withName("weivel_deadline_event");

    static final UnionLayout EVENT_DATA = MemoryLayout.unionLayout(
            LOG_EVENT.withName("log"),
            REFUSAL.withName("refusal"),
            WANT_EVENT.withName("want"),
            WATCH_EVENT.withName("watch"),
            REVOKED_EVENT.withName("revoked"),
            REACH_REFUSAL.withName("reach"),
            CAPACITY_EVENT.withName("capacity"),
            CAPABILITY_EVENT.withName("capability"),
            REFUSAL_EVENT.withName("refused"),
            DEADLINE_EVENT.withName("deadline"),
            MemoryLayout.sequenceLayout(16, JAVA_LONG).withName("reserved"));

    static final StructLayout EVENT = MemoryLayout.structLayout(
            JAVA_INT.withName("size"),
            JAVA_INT.withName("kind"),
            ADDRESS.withName("storage"),
            EVENT_DATA.withName("data")).withName("weivel_event");

    private CoreLayouts() {
    }

    /** The offset of a field reached by naming each member on its way, as {@link MemoryLayout#byteOffset} reads it. */
    static long offsetOf(MemoryLayout layout, String... names) {
        PathElement[] path = new PathElement[names.length];
        for (int index = 0; index < names.length; index++) {
            path[index] = PathElement.groupElement(names[index]);
        }
        return layout.byteOffset(path);
    }
}
