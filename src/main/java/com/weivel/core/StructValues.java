package com.weivel.core;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A struct an answer carries, as the unmodifiable {@code Map} a caller is handed: the member names its plan holds
 * once for every answer of the struct, and one array of this answer's values beside them, in the description's
 * order. A member the provider left out, a NULL pointer, a NULL handle or a value of kind NONE, is absent. It
 * serializes as an unmodifiable {@code LinkedHashMap} of the same members.
 */
final class StructValues extends AbstractMap<String, Object> implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Stands for a member present with the value null, as a NULL text is, since an empty slot is an absent one. */
    private static final Object NULL = new Object();

    private final transient StructPlan plan;
    private final transient Object[] values;
    private transient int size;

    StructValues(StructPlan plan) {
        this.plan = plan;
        this.values = new Object[plan.visible.length];
    }

    /** Puts the value of the member at {@code position} in the plan's visible members, while the answer is read. */
    void set(int position, Object value) {
        if (values[position] == null) {
            size++;
        }
        values[position] = value == null ? NULL : value;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean containsKey(Object key) {
        Integer position = plan.positionOf(key);
        return position != null && values[position] != null;
    }

    @Override
    public Object get(Object key) {
        Integer position = plan.positionOf(key);
        return position == null ? null : unwrapped(values[position]);
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return new AbstractSet<Map.Entry<String, Object>>() {
            @Override
            public Iterator<Map.Entry<String, Object>> iterator() {
                return new Entries();
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    private Object writeReplace() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this));
    }

    private static Object unwrapped(Object stored) {
        return stored == NULL ? null : stored;
    }

    /** The present members in the description's order, each an entry no caller can change. */
    private final class Entries implements Iterator<Map.Entry<String, Object>> {
        private int next = advancedFrom(0);

        private int advancedFrom(int position) {
            int at = position;
            while (at < values.length && values[at] == null) {
                at++;
            }
            return at;
        }

        @Override
        public boolean hasNext() {
            return next < values.length;
        }

        @Override
        public Map.Entry<String, Object> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Map.Entry<String, Object> entry = new AbstractMap.SimpleImmutableEntry<>(plan.visible[next].name,
                    unwrapped(values[next]));
            next = advancedFrom(next + 1);
            return entry;
        }
    }
}
