package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    @Test
    void serializesAsAnUnmodifiableLinkedHashMapOfItsMembers() throws Exception {
        StructValues inner = new StructValues(plan("x"));
        inner.set(0, 3L);
        StructValues values = new StructValues(plan("label", "best", "origin"));
        values.set(0, null);
        values.set(2, inner);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(values);
        }

        Object read;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            read = in.readObject();
        }

        Map<?, ?> copy = (Map<?, ?>) read;
        assertEquals(values, copy);
        assertEquals(Arrays.asList("label", "origin"), new ArrayList<>(copy.keySet()));
        assertEquals(Collections.singletonMap("x", 3L), copy.get("origin"));
        assertThrows(UnsupportedOperationException.class, () -> ((Map<Object, Object>) copy).put("x", 1));
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
