package com.weivel.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The standard's value tree ({@code weivel/value.h}) as the natural Java value: JSON's null as {@code null}, a
 * {@link Boolean}, a {@link Double}, a {@link String}, a {@code List} and a {@code Map} keyed by text, in the
 * provider's order. A node of kind NONE has no Java value; whoever holds one leaves it out.
 */
final class ValueTrees {
    static final int NONE = 0;
    static final int NULL = 1;
    static final int BOOLEAN = 2;
    static final int NUMBER = 3;
    static final int TEXT = 4;
    static final int LIST = 5;
    static final int RECORD = 6;
    static final int DEPTH_LIMIT = 1000;

    static final long NODE_SIZE = DescriptionLayouts.VALUE.byteSize();
    static final long ENTRY_SIZE = DescriptionLayouts.ENTRY.byteSize();
    static final long TREE_SIZE = DescriptionLayouts.VALUE_TREE.byteSize();
    static final long TREE_ROOT = DescriptionLayouts.VALUE_TREE.offsetOf("root");

    private static final long KIND = DescriptionLayouts.VALUE.offsetOf("kind");
    private static final long BOOLEAN_FIELD = DescriptionLayouts.VALUE.offsetOf("boolean");
    private static final long NUMBER_FIELD = DescriptionLayouts.VALUE.offsetOf("number");
    private static final long TEXT_FIELD = DescriptionLayouts.VALUE.offsetOf("text");
    private static final long LENGTH = DescriptionLayouts.VALUE.offsetOf("length");
    private static final long ITEMS = DescriptionLayouts.VALUE.offsetOf("items");
    private static final long ENTRIES = DescriptionLayouts.VALUE.offsetOf("entries");
    private static final long COUNT = DescriptionLayouts.VALUE.offsetOf("count");
    private static final long KEY = DescriptionLayouts.ENTRY.offsetOf("key");
    private static final long KEY_LENGTH = DescriptionLayouts.ENTRY.offsetOf("key_length");
    private static final long ENTRY_VALUE = DescriptionLayouts.ENTRY.offsetOf("value");
    private static final long LONGEST_EXACT = 1L << 53;

    private ValueTrees() {
    }

    static int kindAt(NativeAccess access, long node) {
        return access.getInt(node, KIND);
    }

    /**
     * Writes {@code value} as a node at {@code node}, every text and child array it holds placed in {@code arena}; the
     * node is zeroed already.
     *
     * @throws IllegalArgumentException for a value that is none of the natural kinds, a number that is not finite or
     *     an integer a double does not hold exactly, a text or key holding a NUL, a key that is not a text, or a tree
     *     nested deeper than the standard's limit
     * @implNote The walk keeps its own stack of nodes still to write rather than recursing, so the depth limit is
     *     reached on any thread's stack, however small. A list or record is taken once as an array, so the block sized
     *     for its elements and the walk writing them agree even while another thread changes it.
     */
    static void write(NativeAccess access, NativeArena arena, long node, Object value, String where) {
        Deque<Pending> pending = new ArrayDeque<>();
        pending.push(new Pending(node, value, where, 0));
        while (!pending.isEmpty()) {
            writeOne(access, arena, pending.pop(), pending);
        }
    }

    private static void writeOne(NativeAccess access, NativeArena arena, Pending next, Deque<Pending> pending) {
        if (next.depth > DEPTH_LIMIT) {
            throw new IllegalArgumentException(next.where + " nests deeper than " + DEPTH_LIMIT + " levels, which no "
                    + "value tree holds");
        }
        long node = next.node;
        Object value = next.value;
        if (value == null) {
            access.putInt(node, KIND, NULL);
        } else if (value instanceof Boolean) {
            access.putInt(node, KIND, BOOLEAN);
            access.putInt(node, BOOLEAN_FIELD, ((Boolean) value) ? 1 : 0);
        } else if (value instanceof Number) {
            access.putInt(node, KIND, NUMBER);
            access.putDouble(node, NUMBER_FIELD, number((Number) value, next.where));
        } else if (value instanceof String) {
            access.putInt(node, KIND, TEXT);
            byte[] text = Scalars.utf8((String) value, next.where);
            access.putLong(node, TEXT_FIELD, placed(access, arena, text));
            access.putLong(node, LENGTH, text.length);
        } else if (value instanceof List) {
            writeList(access, arena, next, (List<?>) value, pending);
        } else if (value instanceof Map) {
            writeRecord(access, arena, next, (Map<?, ?>) value, pending);
        } else {
            throw new IllegalArgumentException(next.where + " is a value, written as null, a Boolean, a Number, a "
                    + "String, a List or a Map keyed by String, not a " + value.getClass().getName());
        }
    }

    private static void writeList(NativeAccess access, NativeArena arena, Pending list, List<?> items,
                                  Deque<Pending> pending) {
        access.putInt(list.node, KIND, LIST);
        Object[] snapshot = items.toArray();
        if (snapshot.length == 0) {
            return;
        }
        long nodes = arena.allocateAddress(snapshot.length * NODE_SIZE);
        for (int index = 0; index < snapshot.length; index++) {
            pending.push(new Pending(nodes + index * NODE_SIZE, snapshot[index], list.where + "[" + index + "]",
                    list.depth + 1));
        }
        access.putLong(list.node, ITEMS, nodes);
        access.putLong(list.node, COUNT, snapshot.length);
    }

    private static void writeRecord(NativeAccess access, NativeArena arena, Pending record, Map<?, ?> members,
                                    Deque<Pending> pending) {
        access.putInt(record.node, KIND, RECORD);
        Object[] snapshot = members.entrySet().toArray();
        if (snapshot.length == 0) {
            return;
        }
        long entries = arena.allocateAddress(snapshot.length * ENTRY_SIZE);
        for (int index = 0; index < snapshot.length; index++) {
            Map.Entry<?, ?> member = (Map.Entry<?, ?>) snapshot[index];
            if (!(member.getKey() instanceof String)) {
                throw new IllegalArgumentException(record.where + " is a record, keyed by String, and holds the key "
                        + member.getKey());
            }
            String key = (String) member.getKey();
            long entry = entries + index * ENTRY_SIZE;
            byte[] encoded = Scalars.utf8(key, record.where + "'s key " + key);
            access.putLong(entry, KEY, placed(access, arena, encoded));
            access.putLong(entry, KEY_LENGTH, encoded.length);
            pending.push(new Pending(entry + ENTRY_VALUE, member.getValue(), record.where + "." + key,
                    record.depth + 1));
        }
        access.putLong(record.node, ENTRIES, entries);
        access.putLong(record.node, COUNT, snapshot.length);
    }

    private static double number(Number number, String where) {
        if (number instanceof Long || number instanceof Integer || number instanceof Short
                || number instanceof Byte) {
            long integer = number.longValue();
            if (integer > LONGEST_EXACT || integer < -LONGEST_EXACT) {
                throw new IllegalArgumentException(where + " is a value's number, a double, which holds " + integer
                        + " only rounded");
            }
            return integer;
        }
        double real = number.doubleValue();
        if (Double.isNaN(real) || Double.isInfinite(real)) {
            throw new IllegalArgumentException(where + " is a value's number, which is always finite, not " + real);
        }
        return real;
    }

    private static long placed(NativeAccess access, NativeArena arena, byte[] text) {
        long address = arena.allocateAddress(text.length + 1L);
        access.writeBytes(address, 0, text);
        return address;
    }

    /**
     * The Java value of the node at {@code node}.
     *
     * @throws IllegalStateException for a node of a kind the standard does not publish, NONE below a root, or a tree
     *     nested deeper than the standard's limit
     * @implNote Read with a stack of its own, as {@link #write} writes, so the limit is reached on any thread's stack:
     *     a list or record is made at once, holding a place for each child, and the place is filled when its child is
     *     read.
     */
    static Object read(NativeAccess access, long node) {
        Deque<Place> pending = new ArrayDeque<>();
        Object root = start(access, node, 0, pending);
        while (!pending.isEmpty()) {
            Place place = pending.pop();
            place.fill(start(access, place.node, place.depth, pending));
        }
        return root;
    }

    /** The node's value, a list or record made with its places held and its children left to {@code pending}. */
    private static Object start(NativeAccess access, long node, int depth, Deque<Place> pending) {
        if (depth > DEPTH_LIMIT) {
            throw new IllegalStateException("the provider answered a value nested deeper than " + DEPTH_LIMIT
                    + " levels");
        }
        int kind = access.getInt(node, KIND);
        switch (kind) {
            case NULL:
                return null;
            case BOOLEAN:
                return access.getInt(node, BOOLEAN_FIELD) != 0;
            case NUMBER:
                return access.getDouble(node, NUMBER_FIELD);
            case TEXT:
                return text(access, access.getLong(node, TEXT_FIELD), access.getLong(node, LENGTH));
            case LIST:
                return startList(access, node, depth, pending);
            case RECORD:
                return startRecord(access, node, depth, pending);
            default:
                throw new IllegalStateException("the provider answered a value of kind " + kind
                        + (kind == NONE ? " below its root, where NONE may not stand" : ", which value.h does not "
                        + "publish"));
        }
    }

    private static List<Object> startList(NativeAccess access, long node, int depth, Deque<Place> pending) {
        long items = access.getLong(node, ITEMS);
        int count = Scalars.count(access.getLong(node, COUNT));
        if (items == 0 || count == 0) {
            return Collections.emptyList();
        }
        List<Object> list = new ArrayList<>(Collections.nCopies(count, null));
        for (int index = 0; index < count; index++) {
            pending.push(new Place(items + index * NODE_SIZE, depth + 1, list, index, null));
        }
        return Collections.unmodifiableList(list);
    }

    private static Map<String, Object> startRecord(NativeAccess access, long node, int depth, Deque<Place> pending) {
        long entries = access.getLong(node, ENTRIES);
        int count = Scalars.count(access.getLong(node, COUNT));
        if (entries == 0 || count == 0) {
            return Collections.emptyMap();
        }
        Map<String, Object> record = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            long entry = entries + index * ENTRY_SIZE;
            String key = text(access, access.getLong(entry, KEY), access.getLong(entry, KEY_LENGTH));
            record.put(key, null);
            pending.push(new Place(entry + ENTRY_VALUE, depth + 1, null, index, new Keyed(record, key)));
        }
        return Collections.unmodifiableMap(record);
    }

    /** A node still to write, at its depth. */
    private static final class Pending {
        final long node;
        final Object value;
        final String where;
        final int depth;

        Pending(long node, Object value, String where, int depth) {
            this.node = node;
            this.value = value;
            this.where = where;
            this.depth = depth;
        }
    }

    /** A record's member whose value is still to read. */
    private static final class Keyed {
        final Map<String, Object> record;
        final String key;

        Keyed(Map<String, Object> record, String key) {
            this.record = record;
            this.key = key;
        }
    }

    /** A node still to read, and the place in a list or record its value fills. */
    private static final class Place {
        final long node;
        final int depth;
        final List<Object> list;
        final int index;
        final Keyed keyed;

        Place(long node, int depth, List<Object> list, int index, Keyed keyed) {
            this.node = node;
            this.depth = depth;
            this.list = list;
            this.index = index;
            this.keyed = keyed;
        }

        void fill(Object value) {
            if (list != null) {
                list.set(index, value);
            } else {
                keyed.record.put(keyed.key, value);
            }
        }
    }

    private static String text(NativeAccess access, long text, long length) {
        if (text == 0) {
            return "";
        }
        return new String(access.readBytes(text, 0, Scalars.count(length)), StandardCharsets.UTF_8);
    }
}
