package com.weivel.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every struct a description names, laid out once by the native backend's own rules from the description's members
 * and held against it: the struct's size, and every member's offset and size, must be what the backend computes, so a
 * description that disagrees with the platform is refused before anything reads or writes through it. Each struct
 * then gets the {@link StructPlan} a call follows.
 */
final class DescribedLayouts {
    private static final FfiStruct VALUE_NODE = new FfiStruct("weivel_standard_value", fields(
            FfiStruct.Field.of("kind", FfiStruct.Shape.U32),
            FfiStruct.Field.of("boolean", FfiStruct.Shape.U32),
            FfiStruct.Field.of("number", FfiStruct.Shape.F64),
            FfiStruct.Field.of("text", FfiStruct.Shape.POINTER),
            FfiStruct.Field.of("length", FfiStruct.Shape.SIZE),
            FfiStruct.Field.of("items", FfiStruct.Shape.POINTER),
            FfiStruct.Field.of("entries", FfiStruct.Shape.POINTER),
            FfiStruct.Field.of("count", FfiStruct.Shape.SIZE)));

    private final NativeAccess access;
    private final Description description;
    private final Map<String, Description.Type> byName = new LinkedHashMap<>();
    private final Map<String, FfiStruct> built = new HashMap<>();
    private final Set<String> building = new HashSet<>();
    private final Map<String, StructPlan> plans = new LinkedHashMap<>();

    /**
     * @throws DescriptionRefused for a struct whose layout disagrees with the backend's, a kind this binding does not
     *     know, or an element it cannot reach
     */
    DescribedLayouts(NativeAccess access, Description description) {
        this.access = access;
        this.description = description;
        for (Description.Type type : description.types()) {
            byName.put(type.name(), type);
        }
        for (Description.Type type : description.types()) {
            check(type, build(type.name()));
        }
        for (Description.Type type : description.types()) {
            plans.put(type.name(), new StructPlan(type.name(), type.size(), hasLeadingSize(type)));
        }
        for (Description.Type type : description.types()) {
            for (Description.Member member : type.members()) {
                refuseUnreachableElement(member.kind(), member.element(), member.type(), "its member " + type.name()
                        + "." + member.name());
            }
        }
        for (Description.Type type : description.types()) {
            plans.get(type.name()).visible(visiblePlans(type));
        }
    }

    Description description() {
        return description;
    }

    /** @throws DescriptionRefused for a name the description holds no struct for */
    StructPlan plan(Optional<String> name, String where) {
        StructPlan plan = name.isPresent() ? plans.get(name.get()) : null;
        if (plan == null) {
            throw refused(where + " names " + name.orElse("no struct") + " as a struct and the description describes "
                    + "none by that name");
        }
        return plan;
    }

    Description.Type type(String name) {
        return byName.get(name);
    }

    /** Every plan of every struct, each of its visible members once. */
    List<MemberPlan> everyMemberPlan() {
        List<MemberPlan> every = new ArrayList<>();
        for (StructPlan plan : plans.values()) {
            for (MemberPlan member : plan.visible) {
                every.add(member);
            }
        }
        return every;
    }

    DescriptionRefused refused(String detail) {
        return new DescriptionRefused(description.definition(), detail);
    }

    /** A U32 called {@code size} at offset 0: the struct's own size, set by the binding and never answered. */
    static boolean isLeadingSize(Description.Member member) {
        return member.kind() == DescribedKind.U32 && member.name().equals("size") && member.offset() == 0;
    }

    static boolean isScalar(int kind) {
        return kind >= DescribedKind.U32 && kind <= DescribedKind.SIZE;
    }

    private static boolean hasLeadingSize(Description.Type type) {
        for (Description.Member member : type.members()) {
            if (isLeadingSize(member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves what a POINTER or an ARRAY reaches, so a description whose element this binding cannot marshal is
     * refused whole at the bind rather than at the first call reaching it.
     *
     * @throws DescriptionRefused for an element of a kind this binding does not know or cannot hold there, or a struct
     *     element the description does not describe
     */
    void refuseUnreachableElement(int kind, int element, Optional<String> type, String where) {
        if (kind != DescribedKind.POINTER && kind != DescribedKind.ARRAY) {
            return;
        }
        if (element < DescribedKind.U32 || element > DescribedKind.LAST) {
            throw refused(where + " reaches elements of kind " + element + ", which this binding does not know");
        }
        if (kind == DescribedKind.ARRAY) {
            elementSize(element, type, where);
        } else if (element == DescribedKind.STRUCT) {
            plan(type, where);
        } else if (element != DescribedKind.VALUE) {
            throw refused(where + " points at an element of kind " + element + ", where a pointer points at a struct "
                    + "or a value");
        }
    }

    /** How many bytes one element of an array of {@code element} takes, {@code type} naming a struct element. */
    long elementSize(int element, Optional<String> type, String where) {
        if (element == DescribedKind.STRUCT) {
            return plan(type, where).size;
        }
        if (element == DescribedKind.VALUE) {
            return DescriptionLayouts.VALUE.byteSize();
        }
        if (element == DescribedKind.U32) {
            return Layout.INT.byteSize();
        }
        if (isScalar(element)) {
            return Layout.LONG.byteSize();
        }
        throw refused(where + " holds elements of kind " + element + ", which this binding cannot lay out");
    }

    /** The plan of what a member or a parameter reaches, its target resolved. */
    MemberPlan reachOf(String name, int kind, int element, Optional<String> type, Optional<String> release,
                       long offset, long countOffset, String where) {
        boolean reaches = kind == DescribedKind.POINTER || kind == DescribedKind.ARRAY;
        MemberPlan plan = new MemberPlan(name, kind, element, offset, countOffset,
                reaches ? elementSize(element, type, where) : 0,
                kind == DescribedKind.HANDLE ? type.orElse("") : null, release.orElse(null));
        if (kind == DescribedKind.STRUCT || (reaches && element == DescribedKind.STRUCT)) {
            plan.target = plan(type, where);
        }
        return plan;
    }

    private MemberPlan[] visiblePlans(Description.Type type) {
        Set<String> counted = new HashSet<>();
        Map<String, Long> offsets = new HashMap<>();
        for (Description.Member member : type.members()) {
            if (member.count().isPresent()) {
                counted.add(member.count().get());
            }
            offsets.put(member.name(), member.offset());
        }
        List<MemberPlan> visible = new ArrayList<>();
        for (Description.Member member : type.members()) {
            if (isLeadingSize(member) || counted.contains(member.name()) || member.kind() == DescribedKind.CONTEXT
                    || member.kind() == DescribedKind.STORAGE) {
                continue;
            }
            long countOffset = member.count().isPresent() ? offsets.get(member.count().get()) : MemberPlan.NO_COUNT;
            visible.add(reachOf(member.name(), member.kind(), member.element(), member.type(), member.release(),
                    member.offset(), countOffset, "its member " + type.name() + "." + member.name()));
        }
        return visible.toArray(new MemberPlan[0]);
    }

    private FfiStruct build(String name) {
        FfiStruct done = built.get(name);
        if (done != null) {
            return done;
        }
        Description.Type type = byName.get(name);
        if (type == null) {
            throw refused("it embeds " + name + ", which it does not describe");
        }
        if (!building.add(name)) {
            throw refused(name + " holds itself by value");
        }
        List<FfiStruct.Field> fields = new ArrayList<>();
        for (Described field : fieldsOf(type)) {
            fields.add(field.ffi);
        }
        building.remove(name);
        FfiStruct struct = new FfiStruct(name, fields);
        built.put(name, struct);
        return struct;
    }

    /** Every field of the struct in declaration order: a definition's operations, which it lists apart, among them. */
    private List<Described> fieldsOf(Description.Type type) {
        List<Described> fields = new ArrayList<>();
        for (Description.Member member : type.members()) {
            fields.add(new Described(member.name(), member.offset(), member.size(), fieldOf(type, member)));
        }
        if (type.name().equals(description.definition())) {
            for (Description.Operation operation : description.operations()) {
                fields.add(new Described(operation.name(), operation.offset(), access.addressSize(),
                        FfiStruct.Field.of(operation.name(), FfiStruct.Shape.POINTER)));
            }
            fields.sort(Comparator.comparingLong(field -> field.offset));
        }
        return fields;
    }

    private FfiStruct.Field fieldOf(Description.Type type, Description.Member member) {
        int kind = member.kind();
        if (kind < DescribedKind.U32 || kind > DescribedKind.LAST) {
            throw refused("its member " + type.name() + "." + member.name() + " is of kind " + kind
                    + ", which this binding does not know");
        }
        switch (kind) {
            case DescribedKind.U32:
                return FfiStruct.Field.of(member.name(), FfiStruct.Shape.U32);
            case DescribedKind.U64:
                return FfiStruct.Field.of(member.name(), FfiStruct.Shape.U64);
            case DescribedKind.I64:
                return FfiStruct.Field.of(member.name(), FfiStruct.Shape.I64);
            case DescribedKind.F64:
                return FfiStruct.Field.of(member.name(), FfiStruct.Shape.F64);
            case DescribedKind.SIZE:
                return FfiStruct.Field.of(member.name(), FfiStruct.Shape.SIZE);
            case DescribedKind.STRUCT:
                if (!member.type().isPresent()) {
                    throw refused("its member " + type.name() + "." + member.name() + " holds a struct and names "
                            + "none");
                }
                return FfiStruct.Field.embedding(member.name(), build(member.type().get()));
            case DescribedKind.VALUE:
                return FfiStruct.Field.embedding(member.name(), VALUE_NODE);
            default:
                return FfiStruct.Field.of(member.name(), FfiStruct.Shape.POINTER);
        }
    }

    private void check(Description.Type type, FfiStruct struct) {
        FfiStruct.Placement placement = access.layOut(struct);
        List<Described> fields = fieldsOf(type);
        for (int index = 0; index < fields.size(); index++) {
            Described field = fields.get(index);
            long offset = placement.offset(index);
            long size = placement.size(index);
            if (offset != field.offset || size != field.size) {
                throw refused(type.name() + "." + field.name + " is described at offset " + field.offset
                        + " and size " + field.size + ", where this platform lays it out at offset " + offset
                        + " and size " + size);
            }
        }
        if (placement.size() != type.size()) {
            throw refused(type.name() + " is described as " + type.size() + " bytes, where this platform lays it out "
                    + "in " + placement.size());
        }
    }

    private static List<FfiStruct.Field> fields(FfiStruct.Field... fields) {
        List<FfiStruct.Field> list = new ArrayList<>();
        for (FfiStruct.Field field : fields) {
            list.add(field);
        }
        return list;
    }

    /** One field as the description states it, beside the field the backend is asked to lay out. */
    private static final class Described {
        final String name;
        final long offset;
        final long size;
        final FfiStruct.Field ffi;

        Described(String name, long offset, long size, FfiStruct.Field ffi) {
            this.name = name;
            this.offset = offset;
            this.size = size;
            this.ffi = ffi;
        }
    }
}
