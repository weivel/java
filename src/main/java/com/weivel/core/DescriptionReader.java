package com.weivel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Copies a {@code weivel_standard_description} into a {@link Description}, through the hand layout of its header.
 * It reads a description the standard's check has passed, so every array is as long as its count and every name it
 * requires is set.
 */
final class DescriptionReader {
    private final NativeAccess access;

    DescriptionReader(NativeAccess access) {
        this.access = access;
    }

    Description read(long description) {
        Layout layout = DescriptionLayouts.DESCRIPTION;
        List<Description.Type> types = new ArrayList<>();
        each(description, layout, "types", "type_count", DescriptionLayouts.TYPE, type -> types.add(type(type)));
        List<Description.Enumeration> enums = new ArrayList<>();
        each(description, layout, "enums", "enum_count", DescriptionLayouts.ENUM, described -> enums.add(
                enumeration(described)));
        List<Description.Operation> operations = new ArrayList<>();
        each(description, layout, "operations", "operation_count", DescriptionLayouts.OPERATION, operation ->
                operations.add(operation(operation)));
        return new Description(text(description, layout, "definition"), types, enums, operations);
    }

    private Description.Type type(long type) {
        Layout layout = DescriptionLayouts.TYPE;
        List<Description.Member> members = new ArrayList<>();
        each(type, layout, "members", "member_count", DescriptionLayouts.MEMBER, member -> members.add(member(member)));
        return new Description.Type(text(type, layout, "name"), size(type, layout, "size"),
                size(type, layout, "first_shape"), members);
    }

    private Description.Member member(long member) {
        Layout layout = DescriptionLayouts.MEMBER;
        return new Description.Member(text(member, layout, "name"), size(member, layout, "offset"),
                size(member, layout, "size"), number(member, layout, "kind"), number(member, layout, "element"),
                optionalText(member, layout, "type"), optionalText(member, layout, "count"),
                optionalText(member, layout, "release"));
    }

    private Description.Enumeration enumeration(long described) {
        Layout layout = DescriptionLayouts.ENUM;
        List<Description.Entry> entries = new ArrayList<>();
        each(described, layout, "entries", "entry_count", DescriptionLayouts.ENUM_ENTRY, entry -> entries.add(
                new Description.Entry(text(entry, DescriptionLayouts.ENUM_ENTRY, "name"),
                        Integer.toUnsignedLong(number(entry, DescriptionLayouts.ENUM_ENTRY, "number")))));
        return new Description.Enumeration(text(described, layout, "name"), entries);
    }

    private Description.Operation operation(long operation) {
        Layout layout = DescriptionLayouts.OPERATION;
        List<Description.Parameter> parameters = new ArrayList<>();
        each(operation, layout, "parameters", "parameter_count", DescriptionLayouts.PARAMETER, parameter ->
                parameters.add(parameter(parameter)));
        return new Description.Operation(text(operation, layout, "name"), size(operation, layout, "offset"),
                number(operation, layout, "returns"), parameters);
    }

    private Description.Parameter parameter(long parameter) {
        Layout layout = DescriptionLayouts.PARAMETER;
        return new Description.Parameter(text(parameter, layout, "name"), number(parameter, layout, "kind"),
                number(parameter, layout, "role"), number(parameter, layout, "element"),
                optionalText(parameter, layout, "type"), optionalText(parameter, layout, "count"),
                optionalText(parameter, layout, "release"));
    }

    private void each(long holder, Layout layout, String items, String count, Layout element, Element read) {
        long first = access.getLong(holder, layout.offsetOf(items));
        long total = access.getLong(holder, layout.offsetOf(count));
        for (long index = 0; first != 0 && index < total; index++) {
            read.at(first + index * element.byteSize());
        }
    }

    private String text(long holder, Layout layout, String member) {
        return access.readText(access.getLong(holder, layout.offsetOf(member)));
    }

    private Optional<String> optionalText(long holder, Layout layout, String member) {
        long text = access.getLong(holder, layout.offsetOf(member));
        return text == 0 ? Optional.<String>empty() : Optional.of(access.readText(text));
    }

    private long size(long holder, Layout layout, String member) {
        return access.getLong(holder, layout.offsetOf(member));
    }

    private int number(long holder, Layout layout, String member) {
        return access.getInt(holder, layout.offsetOf(member));
    }

    /** Reads one element of an array at its address. */
    private interface Element {
        void at(long address);
    }
}
