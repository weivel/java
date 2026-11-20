package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A struct's answer as the map a caller is handed: as a LinkedHashMap of its present members, and unmodifiable. */
class StructValuesTest {
    @Test
    void holdsThePresentMembersInTheDescriptionsOrderAndEqualsTheSameLinkedHashMap() {
        StructValues values = new StructValues(plan("label", "best", "count"));
        values.set(2, 7L);
        values.set(0, null);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("label", null);
        expected.put("count", 7L);
        assertEquals(expected, values);
        assertEquals(values, expected);
        assertEquals(expected.hashCode(), values.hashCode());
        assertEquals(expected.toString(), values.toString());
        assertEquals(Arrays.asList("label", "count"), new ArrayList<>(values.keySet()));
        assertEquals(2, values.size());
        assertTrue(values.containsKey("label"), "a member present as null is present");
        assertNull(values.get("label"));
        assertFalse(values.containsKey("best"), "a member left out is absent");
        assertFalse(values.containsKey("other"));
        assertNull(values.get(42));
    }

    @Test
    void refusesEveryChange() {
        StructValues values = new StructValues(plan("label"));
        values.set(0, "here");

        assertThrows(UnsupportedOperationException.class, () -> values.put("label", "there"));
        assertThrows(UnsupportedOperationException.class, () -> values.remove("label"));
        assertThrows(UnsupportedOperationException.class, values::clear);
        assertThrows(UnsupportedOperationException.class, () -> values.entrySet().iterator().next().setValue("x"));
        Iterator<String> names = values.keySet().iterator();
        names.next();
        assertThrows(UnsupportedOperationException.class, names::remove);
        assertEquals("here", values.get("label"));
    }

    private static StructPlan plan(String... names) {
        StructPlan plan = new StructPlan("struct", 0, false);
        MemberPlan[] members = new MemberPlan[names.length];
        for (int index = 0; index < names.length; index++) {
            members[index] = new MemberPlan(names[index], DescribedKind.U64, 0, index * 8L, MemberPlan.NO_COUNT, 0,
                    null, null);
        }
        plan.visible(members);
        return plan;
    }
}
