package com.weivel.standard;

import static com.weivel.core.Layout.ADDRESS;
import static com.weivel.core.Layout.BYTE;
import static com.weivel.core.Layout.DOUBLE;
import static com.weivel.core.Layout.INT;
import static com.weivel.core.Layout.LONG;

import com.weivel.core.Layout;

/** Every public struct of the standard's headers, each named as its header names it. */
final class StandardLayouts {
    static final Layout JSON_ERROR = Layout.struct(
            INT.withName("size"),
            INT.withName("line"),
            INT.withName("column"),
            Layout.padding(4),
            ADDRESS.withName("message"),
            Layout.sequence(96, BYTE).withName("detail"),
            INT.withName("out_of_memory"),
            Layout.padding(4)).withName("weivel_json_error");

    static final Layout REFUSAL = Layout.struct(
            INT.withName("size"),
            INT.withName("code"),
            ADDRESS.withName("detail"),
            ADDRESS.withName("fix"),
            ADDRESS.withName("path"),
            ADDRESS.withName("storage")).withName("weivel_standard_refusal");

    static final Layout VALIDATION_OPTIONS = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("well_known_services")).withName("weivel_standard_validation_options");

    static final Layout VALIDATION = Layout.struct(
            INT.withName("size"),
            INT.withName("valid"),
            strings().withName("paths"),
            REFUSAL.withName("refusal"),
            ADDRESS.withName("storage")).withName("weivel_standard_validation");

    static final Layout IDENTITY = Layout.struct(
            INT.withName("size"),
            INT.withName("kind"),
            ADDRESS.withName("name"),
            LONG.withName("name_length"),
            ADDRESS.withName("forge"),
            ADDRESS.withName("host"),
            LONG.withName("number"),
            ADDRESS.withName("segments"),
            LONG.withName("segment_count")).withName("weivel_standard_identity");

    static final Layout DISCOVERED_PLUGIN = Layout.struct(
            ADDRESS.withName("manifest"),
            ADDRESS.withName("manifest_path"),
            ADDRESS.withName("identity_path"),
            ADDRESS.withName("identity_id"),
            ADDRESS.withName("repository_name"),
            IDENTITY.withName("identity"),
            ADDRESS.withName("entry_path"),
            INT.withName("entry_present"),
            Layout.padding(4)).withName("weivel_standard_discovered_plugin");

    static final Layout DISCOVERY_FAILURE = Layout.struct(
            ADDRESS.withName("file"),
            REFUSAL.withName("refusal")).withName("weivel_standard_discovery_failure");

    static final Layout DISCOVERY = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("plugins"),
            LONG.withName("plugin_count"),
            ADDRESS.withName("failures"),
            LONG.withName("failure_count"),
            REFUSAL.withName("refusal")).withName("weivel_standard_discovery");

    static final Layout LIBRARY_FUNCTION = Layout.struct(
            ADDRESS.withName("name"),
            ADDRESS.withName("symbol"),
            INT.withName("kind"),
            INT.withName("context")).withName("weivel_standard_library_function");

    static final Layout LIBRARY_SERVICE = Layout.struct(
            ADDRESS.withName("id"),
            ADDRESS.withName("functions"),
            LONG.withName("function_count")).withName("weivel_standard_library_service");

    static final Layout LIBRARY_CONTRACT = Layout.struct(
            ADDRESS.withName("capability"),
            ADDRESS.withName("description")).withName("weivel_standard_library_contract");

    static final Layout LIBRARY = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("import_name"),
            ADDRESS.withName("version"),
            ADDRESS.withName("release"),
            ADDRESS.withName("services"),
            LONG.withName("service_count"),
            ADDRESS.withName("storage"),
            ADDRESS.withName("descriptions"),
            ADDRESS.withName("contracts"),
            LONG.withName("contract_count")).withName("weivel_standard_library");

    static final Layout MIRROR_LIST = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("templates"),
            LONG.withName("count"),
            ADDRESS.withName("storage")).withName("weivel_standard_mirror_list");

    static final Layout SERVICE_REFUSAL = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("detail"),
            ADDRESS.withName("fix"),
            ADDRESS.withName("storage")).withName("weivel_standard_service_refusal");

    static final Layout SETTING_VALUE = Layout.struct(
            INT.withName("kind"),
            INT.withName("boolean"),
            DOUBLE.withName("number"),
            ADDRESS.withName("text"),
            LONG.withName("text_length")).withName("weivel_standard_setting_value");

    static final Layout SETTING = Layout.struct(
            ADDRESS.withName("key"),
            SETTING_VALUE.withName("value")).withName("weivel_standard_setting");

    static final Layout SETTINGS = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("settings"),
            LONG.withName("count"),
            ADDRESS.withName("storage")).withName("weivel_standard_settings");

    static final Layout SETTING_ANSWER = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            SETTING_VALUE.withName("value"),
            ADDRESS.withName("storage")).withName("weivel_standard_setting_answer");

    static final Layout SETTINGS_FILE = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("path"),
            ADDRESS.withName("storage")).withName("weivel_standard_settings_file");

    static final Layout CONFIG = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("context"),
            ADDRESS.withName("all"),
            ADDRESS.withName("get"),
            ADDRESS.withName("set"),
            ADDRESS.withName("release_settings"),
            ADDRESS.withName("release_answer"),
            ADDRESS.withName("release_refusal"),
            ADDRESS.withName("get_tree"),
            ADDRESS.withName("release_tree"),
            ADDRESS.withName("settings_file"),
            ADDRESS.withName("release_settings_file")).withName("weivel_standard_config");

    static final Layout SECRET = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("bytes"),
            LONG.withName("length"),
            ADDRESS.withName("storage")).withName("weivel_standard_secret");

    static final Layout SECRETS = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("context"),
            ADDRESS.withName("remove"),
            ADDRESS.withName("get"),
            ADDRESS.withName("set"),
            ADDRESS.withName("release_secret"),
            ADDRESS.withName("release_refusal")).withName("weivel_standard_secrets");

    static final Layout PATHS = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("context"),
            ADDRESS.withName("cache"),
            ADDRESS.withName("config"),
            ADDRESS.withName("plugin")).withName("weivel_standard_paths");

    static final Layout LOG = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("context"),
            ADDRESS.withName("write"),
            ADDRESS.withName("release_refusal")).withName("weivel_standard_log");

    static final Layout SCOPE = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("config"),
            ADDRESS.withName("secrets"),
            ADDRESS.withName("paths"),
            ADDRESS.withName("log"),
            ADDRESS.withName("storage")).withName("weivel_standard_scope");

    static final Layout SCOPES = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("context"),
            ADDRESS.withName("open"),
            ADDRESS.withName("release_scope"),
            ADDRESS.withName("release_refusal")).withName("weivel_standard_scopes");

    static final Layout HOST_LIBRARY = Layout.struct(
            ADDRESS.withName("binary"),
            ADDRESS.withName("declaration")).withName("weivel_standard_host_library");

    static final Layout HOST_PLUGIN = Layout.struct(
            ADDRESS.withName("directory"),
            ADDRESS.withName("manifest"),
            ADDRESS.withName("entry")).withName("weivel_standard_host_plugin");

    static final Layout HOST_PLAN = Layout.struct(
            INT.withName("size"),
            INT.withName("mode"),
            ADDRESS.withName("libraries"),
            LONG.withName("library_count"),
            ADDRESS.withName("plugins"),
            LONG.withName("plugin_count"),
            ADDRESS.withName("action_plugin"),
            ADDRESS.withName("action_name"),
            value().withName("action_arguments"),
            ADDRESS.withName("storage"),
            ADDRESS.withName("action_service"),
            ADDRESS.withName("action_method")).withName("weivel_standard_host_plan");

    static final Layout HOST_OUTCOME = Layout.struct(
            ADDRESS.withName("manifest"),
            INT.withName("state"),
            Layout.padding(4),
            ADDRESS.withName("detail")).withName("weivel_standard_host_outcome");

    static final Layout HOST_NEXT = Layout.struct(
            INT.withName("size"),
            INT.withName("has_next"),
            LONG.withName("next"),
            ADDRESS.withName("storage")).withName("weivel_standard_host_next");

    static final Layout HOST = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("context"),
            ADDRESS.withName("start"),
            ADDRESS.withName("started"),
            ADDRESS.withName("tick"),
            ADDRESS.withName("finished"),
            ADDRESS.withName("stop"),
            ADDRESS.withName("release_plan"),
            ADDRESS.withName("release_next"),
            ADDRESS.withName("release_refusal")).withName("weivel_standard_host");

    /** The core's {@code weivel_strings}, laid out where the standard embeds or points at one. */
    static final Layout STRINGS_OF_THE_CORE = strings();

    private StandardLayouts() {
    }

    /** {@code weivel/value.h}'s node, laid out where the standard embeds one. */
    private static Layout value() {
        return Layout.struct(
                INT.withName("kind"),
                INT.withName("boolean"),
                DOUBLE.withName("number"),
                ADDRESS.withName("text"),
                LONG.withName("length"),
                ADDRESS.withName("items"),
                ADDRESS.withName("entries"),
                LONG.withName("count")).withName("weivel_standard_value");
    }

    private static Layout strings() {
        return Layout.struct(
                ADDRESS.withName("items"),
                INT.withName("count"),
                Layout.padding(4)).withName("weivel_strings");
    }
}
