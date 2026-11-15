package com.weivel.core;

import static com.weivel.core.Layout.ADDRESS;
import static com.weivel.core.Layout.DOUBLE;
import static com.weivel.core.Layout.INT;
import static com.weivel.core.Layout.LONG;

/** The core's structs the {@code core} package hands in or reads back, each named as the core's header names it. */
final class CoreLayouts {
    static final Layout STRINGS = Layout.struct(
            ADDRESS.withName("items"),
            INT.withName("count"),
            Layout.padding(4)).withName("weivel_strings");

    static final Layout CONSTRAINT_ENTRY = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("constraint_name"),
            ADDRESS.withName("attribute_name"),
            INT.withName("bound"),
            Layout.padding(4)).withName("weivel_constraint_entry");

    static final Layout CONSTRAINT_VOCABULARY = Layout.struct(
            ADDRESS.withName("items"),
            INT.withName("count"),
            Layout.padding(4)).withName("weivel_constraint_vocabulary");

    static final Layout CORE_OPTIONS = Layout.struct(
            INT.withName("size"),
            INT.withName("initial_event_capacity"),
            INT.withName("minimum_log_level"),
            INT.withName("api"),
            STRINGS.withName("applications"),
            STRINGS.withName("well_known_services"),
            CONSTRAINT_VOCABULARY.withName("constraint_vocabulary")).withName("weivel_core_options");

    static final Layout NUMBER = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("name"),
            DOUBLE.withName("value")).withName("weivel_number");

    static final Layout NUMBERS = Layout.struct(
            ADDRESS.withName("items"),
            INT.withName("count"),
            Layout.padding(4)).withName("weivel_numbers");

    static final Layout SERVICE_OFFER = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("id"),
            NUMBERS.withName("attributes")).withName("weivel_service_offer");

    static final Layout SERVICE_OFFERS = Layout.struct(
            ADDRESS.withName("items"),
            INT.withName("count"),
            Layout.padding(4)).withName("weivel_service_offers");

    static final Layout SERVICE_WANT = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("id"),
            ADDRESS.withName("provider"),
            INT.withName("optional"),
            INT.withName("degradable"),
            NUMBERS.withName("constraints")).withName("weivel_service_want");

    static final Layout SERVICE_WANTS = Layout.struct(
            ADDRESS.withName("items"),
            INT.withName("count"),
            Layout.padding(4)).withName("weivel_service_wants");

    static final Layout CAPABILITY_DECLARATIONS = Layout.struct(
            STRINGS.withName("provides"),
            STRINGS.withName("consumes")).withName("weivel_capability_declarations");

    static final Layout SERVICE_DECLARATIONS = Layout.struct(
            SERVICE_OFFERS.withName("provides"),
            SERVICE_WANTS.withName("consumes")).withName("weivel_service_declarations");

    static final Layout MANIFEST = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("import_name"),
            INT.withName("api"),
            Layout.padding(4),
            ADDRESS.withName("entry"),
            STRINGS.withName("targets"),
            CAPABILITY_DECLARATIONS.withName("capabilities"),
            SERVICE_DECLARATIONS.withName("services"),
            STRINGS.withName("permissions"),
            ADDRESS.withName("identity")).withName("weivel_manifest");

    static final Layout WANT_OPTIONS = Layout.struct(
            INT.withName("size"),
            INT.withName("timed"),
            LONG.withName("now"),
            LONG.withName("timeout_millis"),
            INT.withName("budgeted"),
            Layout.padding(4),
            LONG.withName("budget_millis"),
            LONG.withName("poll_millis")).withName("weivel_want_options");

    static final Layout PLUGIN_OPTIONS = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            LONG.withName("payload")).withName("weivel_plugin_options");

    static final Layout QUARANTINE_REASON = Layout.struct(
            INT.withName("size"),
            INT.withName("code"),
            ADDRESS.withName("detail"),
            ADDRESS.withName("fix")).withName("weivel_quarantine_reason");

    static final Layout CAPACITY = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            LONG.withName("request"),
            NUMBERS.withName("attributes"),
            INT.withName("serves"),
            Layout.padding(4)).withName("weivel_capacity");

    static final Layout SERVICE_LIST = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            STRINGS.withName("ids"),
            ADDRESS.withName("storage")).withName("weivel_service_list");

    static final Layout CAPABILITY_IMPLEMENTATION = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            LONG.withName("implementation"),
            LONG.withName("provider")).withName("weivel_capability_implementation");

    static final Layout CAPABILITY_LIST = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("items"),
            INT.withName("count"),
            Layout.padding(4),
            ADDRESS.withName("storage")).withName("weivel_capability_list");

    static final Layout REFUSAL = Layout.struct(
            INT.withName("code"),
            Layout.padding(4),
            ADDRESS.withName("detail"),
            ADDRESS.withName("fix")).withName("weivel_refusal");

    static final Layout EDGE = Layout.struct(
            LONG.withName("provider"),
            LONG.withName("consumer")).withName("weivel_edge");

    static final Layout CYCLE = Layout.struct(
            ADDRESS.withName("members"),
            INT.withName("count"),
            Layout.padding(4)).withName("weivel_cycle");

    static final Layout PLUGIN_REFUSAL = Layout.struct(
            LONG.withName("plugin"),
            REFUSAL.withName("refusal")).withName("weivel_plugin_refusal");

    static final Layout REFUSAL_SUBJECT = Layout.struct(
            ADDRESS.withName("subject"),
            INT.withName("has_fix"),
            Layout.padding(4)).withName("weivel_refusal_subject");

    static final Layout ACTIVATION_PLAN = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("order"),
            INT.withName("order_count"),
            Layout.padding(4),
            ADDRESS.withName("edges"),
            INT.withName("edge_count"),
            Layout.padding(4),
            ADDRESS.withName("cycles"),
            INT.withName("cycle_count"),
            Layout.padding(4),
            ADDRESS.withName("refusals"),
            INT.withName("refusal_count"),
            Layout.padding(4),
            ADDRESS.withName("storage"),
            ADDRESS.withName("refusal_subjects")).withName("weivel_activation_plan");

    static final Layout LEDGER_ROW = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            LONG.withName("plugin"),
            ADDRESS.withName("import_name"),
            ADDRESS.withName("identity"),
            INT.withName("status"),
            INT.withName("verified"),
            REFUSAL.withName("refusal"),
            STRINGS.withName("capabilities_declared"),
            STRINGS.withName("permissions"),
            STRINGS.withName("services_provided"),
            STRINGS.withName("capabilities_provided"),
            STRINGS.withName("services_consumed"),
            ADDRESS.withName("storage"),
            STRINGS.withName("capabilities_consumed"),
            ADDRESS.withName("subject"),
            INT.withName("has_fix"),
            Layout.padding(4)).withName("weivel_ledger_row");

    static final Layout LEDGER_SNAPSHOT = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("rows"),
            INT.withName("row_count"),
            Layout.padding(4),
            ADDRESS.withName("refusals"),
            INT.withName("refusal_count"),
            Layout.padding(4),
            ADDRESS.withName("storage")).withName("weivel_ledger_snapshot");

    static final Layout LOG_EVENT = Layout.struct(
            INT.withName("level"),
            Layout.padding(4),
            ADDRESS.withName("text")).withName("weivel_log_event");

    static final Layout WANT_EVENT = Layout.struct(
            LONG.withName("want"),
            LONG.withName("plugin"),
            LONG.withName("implementation"),
            REFUSAL.withName("refusal"),
            INT.withName("degraded"),
            Layout.padding(4),
            ADDRESS.withName("subject"),
            INT.withName("has_fix"),
            Layout.padding(4)).withName("weivel_want_event");

    static final Layout WATCH_EVENT = Layout.struct(
            LONG.withName("watch"),
            LONG.withName("plugin"),
            LONG.withName("implementation"),
            INT.withName("change"),
            Layout.padding(4)).withName("weivel_watch_event");

    static final Layout REVOKED_EVENT = Layout.struct(
            LONG.withName("plugin"),
            LONG.withName("implementation")).withName("weivel_revoked_event");

    static final Layout REACH_REFUSAL = Layout.struct(
            INT.withName("kind"),
            Layout.padding(4),
            LONG.withName("plugin"),
            ADDRESS.withName("id"),
            REFUSAL.withName("refusal"),
            ADDRESS.withName("subject"),
            INT.withName("has_fix"),
            Layout.padding(4)).withName("weivel_reach_refusal");

    static final Layout REFUSAL_EVENT = Layout.struct(
            REFUSAL.withName("refusal"),
            ADDRESS.withName("subject"),
            INT.withName("has_fix"),
            Layout.padding(4)).withName("weivel_refusal_event");

    static final Layout CAPACITY_EVENT = Layout.struct(
            LONG.withName("want"),
            LONG.withName("plugin"),
            LONG.withName("registration"),
            LONG.withName("request"),
            INT.withName("question"),
            Layout.padding(4),
            NUMBERS.withName("constraints")).withName("weivel_capacity_event");

    static final Layout CAPABILITY_EVENT = Layout.struct(
            LONG.withName("watch"),
            LONG.withName("plugin"),
            LONG.withName("implementation"),
            LONG.withName("provider"),
            INT.withName("change"),
            Layout.padding(4)).withName("weivel_capability_event");

    static final Layout DEADLINE_EVENT = Layout.struct(
            LONG.withName("at"),
            INT.withName("set"),
            Layout.padding(4)).withName("weivel_deadline_event");

    static final Layout HOLDINGS_RELEASED_EVENT = Layout.struct(
            LONG.withName("owner"),
            LONG.withName("registration")).withName("weivel_holdings_released_event");

    static final Layout EVENT_DATA = Layout.union(
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
            HOLDINGS_RELEASED_EVENT.withName("released"),
            Layout.sequence(16, LONG).withName("reserved"));

    static final Layout EVENT = Layout.struct(
            INT.withName("size"),
            INT.withName("kind"),
            ADDRESS.withName("storage"),
            EVENT_DATA.withName("data")).withName("weivel_event");

    private CoreLayouts() {
    }

    /** The offset of a field reached by naming each member on its way. */
    static long offsetOf(Layout layout, String... names) {
        return layout.offsetOf(names);
    }
}
