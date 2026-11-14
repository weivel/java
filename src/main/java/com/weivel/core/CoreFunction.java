package com.weivel.core;

import static com.weivel.core.NativeType.ADDRESS;
import static com.weivel.core.NativeType.INT;
import static com.weivel.core.NativeType.LONG;

/**
 * Every function the core's header declares, in the header's order, each with its symbol and the signature it is
 * called with: a pointer is an address, a {@code uint32_t} an int, and a handle or a time a long.
 */
enum CoreFunction {
    CORE_CREATE("weivel_core_create", ADDRESS, ADDRESS, ADDRESS),
    CORE_DESTROY("weivel_core_destroy", ADDRESS),
    NEXT_EVENT("weivel_next_event", ADDRESS, ADDRESS, ADDRESS),
    EVENT_RELEASE("weivel_event_release", ADDRESS),
    NEXT_DEADLINE("weivel_next_deadline", ADDRESS, ADDRESS, ADDRESS),
    ADVANCE("weivel_advance", ADDRESS, LONG, ADDRESS),
    SUPPORTS("weivel_supports", ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    SUPPORT_RELEASE("weivel_support_release", ADDRESS),
    REGISTER_MANIFEST("weivel_register_manifest", ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    PLAN_ACTIVATION("weivel_plan_activation", ADDRESS, ADDRESS, INT, INT, ADDRESS, ADDRESS, ADDRESS),
    ACTIVATION_PLAN_RELEASE("weivel_activation_plan_release", ADDRESS),
    OPEN_PLUGIN("weivel_open_plugin", ADDRESS, LONG, ADDRESS, ADDRESS),
    PLUGIN_PAYLOAD("weivel_plugin_payload", ADDRESS, LONG, ADDRESS, ADDRESS),
    RELEASE_PLUGIN("weivel_release_plugin", ADDRESS, LONG, ADDRESS),
    QUARANTINE_PLUGIN("weivel_quarantine_plugin", ADDRESS, LONG, ADDRESS, ADDRESS),
    FORGET_PLUGIN("weivel_forget_plugin", ADDRESS, LONG, ADDRESS),
    REGISTER_SERVICE("weivel_register_service", ADDRESS, LONG, ADDRESS, ADDRESS, ADDRESS),
    UNREGISTER("weivel_unregister", ADDRESS, LONG, ADDRESS),
    HANDLE_STATE("weivel_handle_state", ADDRESS, LONG, ADDRESS, ADDRESS),
    GET_SERVICE("weivel_get_service", ADDRESS, LONG, ADDRESS, ADDRESS, ADDRESS),
    WANT_SERVICE("weivel_want_service", ADDRESS, LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    REPORT_CAPACITY("weivel_report_capacity", ADDRESS, LONG, LONG, ADDRESS, LONG, ADDRESS),
    WATCH_SERVICE("weivel_watch_service", ADDRESS, LONG, ADDRESS, ADDRESS, ADDRESS),
    SERVICE_IDS("weivel_service_ids", ADDRESS, LONG, ADDRESS, ADDRESS),
    UNBOUND_SERVICES("weivel_unbound_services", ADDRESS, LONG, ADDRESS, ADDRESS),
    SERVICE_LIST_RELEASE("weivel_service_list_release", ADDRESS),
    PROVIDE_CAPABILITY("weivel_provide_capability", ADDRESS, LONG, ADDRESS, ADDRESS, ADDRESS),
    VERIFY_ACTIVATION("weivel_verify_activation", ADDRESS, LONG, ADDRESS),
    ALL_CAPABILITIES("weivel_all_capabilities", ADDRESS, LONG, ADDRESS, ADDRESS, ADDRESS),
    CAPABILITY_LIST_RELEASE("weivel_capability_list_release", ADDRESS),
    WATCH_CAPABILITY("weivel_watch_capability", ADDRESS, LONG, ADDRESS, ADDRESS, ADDRESS),
    LEDGER("weivel_ledger", ADDRESS, ADDRESS, ADDRESS),
    LEDGER_SNAPSHOT_RELEASE("weivel_ledger_snapshot_release", ADDRESS),
    LEDGER_ENTRY("weivel_ledger_entry", ADDRESS, LONG, ADDRESS, ADDRESS),
    LEDGER_ROW_RELEASE("weivel_ledger_row_release", ADDRESS);

    private final String symbol;
    private final NativeSignature signature;

    CoreFunction(String symbol, NativeType... parameters) {
        this.symbol = symbol;
        this.signature = NativeSignature.of(INT, parameters);
    }

    String symbol() {
        return symbol;
    }

    NativeSignature signature() {
        return signature;
    }
}
