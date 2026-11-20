package com.weivel.core;

import java.util.Collections;
import java.util.LinkedHashSet;
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

    StructPlan(String name, long size, boolean leadingSize) {
        this.name = name;
        this.size = size;
        this.leadingSize = leadingSize;
    }

    void visible(MemberPlan[] members) {
        Set<String> names = new LinkedHashSet<>();
        for (MemberPlan member : members) {
            names.add(member.name);
        }
        visible = members;
        visibleNames = Collections.unmodifiableSet(names);
    }
}
