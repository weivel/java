package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares the layouts a package declares by hand with the ones the C compiler printed, by struct and field name. */
public final class LayoutComparison {
    private LayoutComparison() {
    }

    /** Asserts that {@code holder} declares at least one struct layout and that each matches C's. */
    public static void assertEveryDeclaredLayoutMatches(Class<?> holder, CLayouts printed) {
        List<StructLayout> declared = declaredStructLayouts(holder);
        assertFalse(declared.isEmpty(), holder.getName() + " declares no struct layout");
        for (StructLayout layout : declared) {
            assertMatches(layout, printed);
        }
    }

    /** Asserts that one declared struct layout has C's fields, offsets, sizes and size. */
    public static void assertMatches(StructLayout declared, CLayouts printed) {
        String name = declared.name().orElseThrow(() -> new AssertionError("a declared struct layout has no name"));
        CLayouts.CStruct struct = printed.structs().get(name);
        if (struct == null) {
            throw new AssertionError(name + " is not a struct the layouts program prints");
        }
        assertEquals(struct.fields(), fieldsOf(declared), name + " fields");
        assertEquals(struct.size(), declared.byteSize(), name + " sizeof");
    }

    private static List<StructLayout> declaredStructLayouts(Class<?> holder) {
        List<StructLayout> layouts = new ArrayList<>();
        for (Field field : holder.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && StructLayout.class.isAssignableFrom(field.getType())) {
                layouts.add((StructLayout) valueOf(field));
            }
        }
        return layouts;
    }

    private static Object valueOf(Field field) {
        try {
            field.setAccessible(true);
            return field.get(null);
        } catch (IllegalAccessException inaccessible) {
            throw new AssertionError("cannot read " + field, inaccessible);
        }
    }

    private static Map<String, CLayouts.CField> fieldsOf(StructLayout declared) {
        Map<String, CLayouts.CField> fields = new LinkedHashMap<>();
        long offset = 0;
        for (MemoryLayout member : declared.memberLayouts()) {
            if (!(member instanceof PaddingLayout)) {
                addField(fields, "", member, offset);
            }
            offset += member.byteSize();
        }
        return fields;
    }

    private static void addField(Map<String, CLayouts.CField> fields, String prefix, MemoryLayout member, long offset) {
        String name = prefix + member.name().orElseThrow(() -> new AssertionError("a declared field has no name"));
        fields.put(name, new CLayouts.CField(offset, member.byteSize()));
        if (member instanceof UnionLayout union) {
            for (MemoryLayout alternative : union.memberLayouts()) {
                addField(fields, name + ".", alternative, offset);
            }
        }
    }
}
