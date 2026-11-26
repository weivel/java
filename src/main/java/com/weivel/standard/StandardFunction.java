package com.weivel.standard;

import static com.weivel.core.NativeType.ADDRESS;
import static com.weivel.core.NativeType.INT;
import static com.weivel.core.NativeType.LONG;
import static com.weivel.core.NativeType.VOID;

import com.weivel.core.NativeType;

/**
 * Every function of the standard this package calls, each with its symbol and the signature it is called with: a
 * pointer is an address, a {@code size_t} a long, and a {@code weivel_status} or an {@code int} an int.
 */
enum StandardFunction {
    REFUSAL_RELEASE("weivel_standard_refusal_release", VOID, ADDRESS),
    MANIFEST_VIEW("weivel_standard_manifest_view", ADDRESS, ADDRESS),
    MANIFEST_LANGUAGE("weivel_standard_manifest_language", ADDRESS, ADDRESS),
    MANIFEST_RELEASE("weivel_standard_manifest_release", VOID, ADDRESS),
    VALIDATION_RELEASE("weivel_standard_validation_release", VOID, ADDRESS),
    READ_MANIFEST("weivel_standard_read_manifest", INT, ADDRESS, LONG, ADDRESS, ADDRESS),
    VALIDATE("weivel_standard_validate", INT, ADDRESS, LONG, ADDRESS, ADDRESS),
    LOAD_MANIFEST("weivel_standard_load_manifest", INT, ADDRESS, LONG, ADDRESS, ADDRESS, ADDRESS),
    IDENTITY_PARSE("weivel_standard_identity_parse", INT, ADDRESS, LONG, ADDRESS, ADDRESS),
    IDENTITY_RELEASE("weivel_standard_identity_release", VOID, ADDRESS),
    IDENTITY_COMPARE("weivel_standard_identity_compare", INT, ADDRESS, ADDRESS),
    DISCOVER("weivel_standard_discover", INT, ADDRESS, ADDRESS, ADDRESS),
    DISCOVERY_RELEASE("weivel_standard_discovery_release", VOID, ADDRESS),
    READ_LIBRARY("weivel_standard_read_library", INT, ADDRESS, LONG, ADDRESS, ADDRESS),
    LIBRARY_RELEASE("weivel_standard_library_release", VOID, ADDRESS),
    READ_MIRRORS("weivel_standard_read_mirrors", INT, ADDRESS, LONG, ADDRESS, ADDRESS),
    LOAD_MIRRORS("weivel_standard_load_mirrors", INT, ADDRESS, ADDRESS, ADDRESS),
    MIRROR_LIST_RELEASE("weivel_standard_mirror_list_release", VOID, ADDRESS),
    MIRROR_EXPAND("weivel_standard_mirror_expand", INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    TEXT_RELEASE("weivel_standard_text_release", VOID, ADDRESS),
    SHA256("weivel_standard_sha256", VOID, ADDRESS, LONG, ADDRESS),
    SHA256_HEX("weivel_standard_sha256_hex", VOID, ADDRESS, LONG, ADDRESS),
    IS_SHA256_HEX("weivel_standard_is_sha256_hex", INT, ADDRESS),
    VERIFY_DOWNLOAD("weivel_standard_verify_download", INT, ADDRESS, LONG, ADDRESS, ADDRESS),
    SERVICE_CONFIG("weivel_standard_service_config", ADDRESS),
    SERVICE_SECRETS("weivel_standard_service_secrets", ADDRESS),
    SERVICE_PATHS("weivel_standard_service_paths", ADDRESS),
    DESCRIPTION_CHECK("weivel_standard_description_check", INT, ADDRESS, ADDRESS),
    DESCRIPTION_ANSWERS("weivel_standard_description_answers", INT, ADDRESS, ADDRESS, ADDRESS);

    private final String symbol;
    private final NativeType returned;
    private final NativeType[] parameters;

    StandardFunction(String symbol, NativeType returned, NativeType... parameters) {
        this.symbol = symbol;
        this.returned = returned;
        this.parameters = parameters;
    }

    String symbol() {
        return symbol;
    }

    NativeType returned() {
        return returned;
    }

    NativeType[] parameters() {
        return parameters.clone();
    }
}
