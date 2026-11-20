package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compares the layouts a package declares by hand with the ones the C compiler printed, by struct and field name. */
public final class LayoutComparison {
    private LayoutComparison() {
    }

    /** Asserts that {@code holder} declares at least one struct layout and that each matches C's. */
    public static void assertEveryDeclaredLayoutMatches(Class<?> holder, CLayouts printed) {
        List<Layout> declared = declaredStructLayouts(holder);
        assertFalse(declared.isEmpty(), holder.getName() + " declares no struct layout");
        for (Layout layout : declared) {
            assertMatches(layout, printed);
        }
    }

    /** The names of the standard's structs the core declares by hand, those of a description and of a value tree. */
    public static Set<String> descriptionStructNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Layout layout : declaredStructLayouts(DescriptionLayouts.class)) {
            names.add(layout.name().orElse(""));
        }
        return names;
    }

    /** Asserts that one declared struct layout has C's fields, offsets, sizes and size. */
    static void assertMatches(Layout declared, CLayouts printed) {
        String name = declared.name().orElseThrow(() -> new AssertionError("a declared struct layout has no name"));
        CLayouts.CStruct struct = printed.structs().get(name);
        if (struct == null) {
            throw new AssertionError(name + " is not a struct the layouts program prints");
        }
        assertEquals(struct.fields(), fieldsOf(declared), name + " fields");
        assertEquals(struct.size(), declared.byteSize(), name + " sizeof");
    }

    private static List<Layout> declaredStructLayouts(Class<?> holder) {
        List<Layout> layouts = new ArrayList<>();
        for (Field field : holder.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Layout.class.isAssignableFrom(field.getType())) {
                Layout layout = (Layout) valueOf(field);
                if (layout.isStruct()) {
                    layouts.add(layout);
                }
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

    private static Map<String, CLayouts.CField> fieldsOf(Layout declared) {
        Map<String, CLayouts.CField> fields = new LinkedHashMap<>();
        long offset = 0;
        for (Layout member : declared.members()) {
            if (!member.isPadding()) {
                addField(fields, "", member, offset);
            }
            offset += member.byteSize();
        }
        return fields;
    }

    private static void addField(Map<String, CLayouts.CField> fields, String prefix, Layout member, long offset) {
        String name = prefix + member.name().orElseThrow(() -> new AssertionError("a declared field has no name"));
        fields.put(name, new CLayouts.CField(offset, member.byteSize()));
        if (member.isUnion()) {
            for (Layout alternative : member.members()) {
                addField(fields, name + ".", alternative, offset);
            }
        }
    }
}
