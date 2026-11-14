package com.weivel.core;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;

/**
 * Every function the core's header declares, in the header's order, each with its symbol and the descriptor its
 * downcall is linked with: a pointer is an address, a {@code uint32_t} an int, and a handle or a time a long.
 */
enum CoreFunction {
    CORE_CREATE("weivel_core_create", ADDRESS, ADDRESS, ADDRESS),
    CORE_DESTROY("weivel_core_destroy", ADDRESS),
    NEXT_EVENT("weivel_next_event", ADDRESS, ADDRESS, ADDRESS),
    EVENT_RELEASE("weivel_event_release", ADDRESS),
    NEXT_DEADLINE("weivel_next_deadline", ADDRESS, ADDRESS, ADDRESS),
    ADVANCE("weivel_advance", ADDRESS, JAVA_LONG, ADDRESS),
    SUPPORTS("weivel_supports", ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    SUPPORT_RELEASE("weivel_support_release", ADDRESS),
    REGISTER_MANIFEST("weivel_register_manifest", ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    PLAN_ACTIVATION("weivel_plan_activation", ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
    ACTIVATION_PLAN_RELEASE("weivel_activation_plan_release", ADDRESS),
    OPEN_PLUGIN("weivel_open_plugin", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS),
    PLUGIN_PAYLOAD("weivel_plugin_payload", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS),
    RELEASE_PLUGIN("weivel_release_plugin", ADDRESS, JAVA_LONG, ADDRESS),
    QUARANTINE_PLUGIN("weivel_quarantine_plugin", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS),
    FORGET_PLUGIN("weivel_forget_plugin", ADDRESS, JAVA_LONG, ADDRESS),
    REGISTER_SERVICE("weivel_register_service", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS),
    UNREGISTER("weivel_unregister", ADDRESS, JAVA_LONG, ADDRESS),
    HANDLE_STATE("weivel_handle_state", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS),
    GET_SERVICE("weivel_get_service", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS),
    WANT_SERVICE("weivel_want_service", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    REPORT_CAPACITY("weivel_report_capacity", ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS),
    WATCH_SERVICE("weivel_watch_service", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS),
    SERVICE_IDS("weivel_service_ids", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS),
    UNBOUND_SERVICES("weivel_unbound_services", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS),
    SERVICE_LIST_RELEASE("weivel_service_list_release", ADDRESS),
    PROVIDE_CAPABILITY("weivel_provide_capability", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS),
    VERIFY_ACTIVATION("weivel_verify_activation", ADDRESS, JAVA_LONG, ADDRESS),
    ALL_CAPABILITIES("weivel_all_capabilities", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS),
    CAPABILITY_LIST_RELEASE("weivel_capability_list_release", ADDRESS),
    WATCH_CAPABILITY("weivel_watch_capability", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS),
    LEDGER("weivel_ledger", ADDRESS, ADDRESS, ADDRESS),
    LEDGER_SNAPSHOT_RELEASE("weivel_ledger_snapshot_release", ADDRESS),
    LEDGER_ENTRY("weivel_ledger_entry", ADDRESS, JAVA_LONG, ADDRESS, ADDRESS),
    LEDGER_ROW_RELEASE("weivel_ledger_row_release", ADDRESS);

    private final String symbol;
    private final FunctionDescriptor descriptor;

    CoreFunction(String symbol, MemoryLayout... parameters) {
        this.symbol = symbol;
        this.descriptor = FunctionDescriptor.of(JAVA_INT, parameters);
    }

    String symbol() {
        return symbol;
    }

    FunctionDescriptor descriptor() {
        return descriptor;
    }
}
