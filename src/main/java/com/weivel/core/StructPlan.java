package com.weivel.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One described struct as a call writes and reads it: its size, whether it opens with a size the binding sets, and
 * the plan of every member a caller writes and an answer carries, which is every member but that leading size, a
 * count, the context and storage.
 */
final class StructPlan {
    final String name;
    final long size;
    final boolean leadingSize;
    MemberPlan[] visible = new MemberPlan[0];
    Set<String> visibleNames = Collections.emptySet();
    private Map<String, Integer> positions = Collections.emptyMap();

    StructPlan(String name, long size, boolean leadingSize) {
        this.name = name;
        this.size = size;
        this.leadingSize = leadingSize;
    }

    void visible(MemberPlan[] members) {
        Set<String> names = new LinkedHashSet<>();
        Map<String, Integer> byName = new HashMap<>();
        for (int position = 0; position < members.length; position++) {
            names.add(members[position].name);
            byName.put(members[position].name, position);
        }
        visible = members;
        visibleNames = Collections.unmodifiableSet(names);
        positions = byName;
    }

    /** Where the member named {@code name} stands among the visible ones; null for a name the struct has not. */
    Integer positionOf(Object name) {
        return positions.get(name);
    }
}
