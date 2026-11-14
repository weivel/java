package com.weivel.core;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

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

    private CoreLayouts() {
    }
}
