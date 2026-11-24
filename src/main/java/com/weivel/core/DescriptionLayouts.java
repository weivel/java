package com.weivel.core;

import static com.weivel.core.Layout.ADDRESS;
import static com.weivel.core.Layout.DOUBLE;
import static com.weivel.core.Layout.INT;
import static com.weivel.core.Layout.LONG;

/**
 * The standard's {@code weivel/description.h} structs, which a binding reads a description through, and
 * {@code weivel/value.h}'s, which every described value is laid out as; each named as its header names it. These are
 * the only structs of a described library laid out by hand: every other one is laid out from its description.
 */
final class DescriptionLayouts {
    static final Layout MEMBER = Layout.struct(
            ADDRESS.withName("name"),
            LONG.withName("offset"),
            LONG.withName("size"),
            INT.withName("kind"),
            INT.withName("element"),
            ADDRESS.withName("type"),
            ADDRESS.withName("count"),
            ADDRESS.withName("release")).withName("weivel_standard_described_member");

    static final Layout TYPE = Layout.struct(
            ADDRESS.withName("name"),
            LONG.withName("size"),
            LONG.withName("first_shape"),
            ADDRESS.withName("members"),
            LONG.withName("member_count")).withName("weivel_standard_described_type");

    static final Layout ENUM_ENTRY = Layout.struct(
            ADDRESS.withName("name"),
            INT.withName("number"),
            Layout.padding(4)).withName("weivel_standard_described_enum_entry");

    static final Layout ENUM = Layout.struct(
            ADDRESS.withName("name"),
            ADDRESS.withName("entries"),
            LONG.withName("entry_count")).withName("weivel_standard_described_enum");

    static final Layout PARAMETER = Layout.struct(
            ADDRESS.withName("name"),
            INT.withName("kind"),
            INT.withName("role"),
            INT.withName("element"),
            Layout.padding(4),
            ADDRESS.withName("type"),
            ADDRESS.withName("count"),
            ADDRESS.withName("release")).withName("weivel_standard_described_parameter");

    static final Layout OPERATION = Layout.struct(
            ADDRESS.withName("name"),
            LONG.withName("offset"),
            INT.withName("returns"),
            Layout.padding(4),
            ADDRESS.withName("parameters"),
            LONG.withName("parameter_count")).withName("weivel_standard_described_operation");

    static final Layout DESCRIPTION = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            ADDRESS.withName("definition"),
            ADDRESS.withName("types"),
            LONG.withName("type_count"),
            ADDRESS.withName("enums"),
            LONG.withName("enum_count"),
            ADDRESS.withName("operations"),
            LONG.withName("operation_count"),
            ADDRESS.withName("blocking"),
            LONG.withName("blocking_count"),
            ADDRESS.withName("definitions"),
            LONG.withName("definition_count"),
            ADDRESS.withName("answered_by")).withName("weivel_standard_description");

    static final Layout VALUE = Layout.struct(
            INT.withName("kind"),
            INT.withName("boolean"),
            DOUBLE.withName("number"),
            ADDRESS.withName("text"),
            LONG.withName("length"),
            ADDRESS.withName("items"),
            ADDRESS.withName("entries"),
            LONG.withName("count")).withName("weivel_standard_value");

    static final Layout ENTRY = Layout.struct(
            ADDRESS.withName("key"),
            LONG.withName("key_length"),
            VALUE.withName("value")).withName("weivel_standard_entry");

    static final Layout VALUE_TREE = Layout.struct(
            INT.withName("size"),
            Layout.padding(4),
            VALUE.withName("root"),
            ADDRESS.withName("storage")).withName("weivel_standard_value_tree");

    private DescriptionLayouts() {
    }
}
