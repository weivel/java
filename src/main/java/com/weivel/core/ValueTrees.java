package com.weivel.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
     */
    static void write(NativeAccess access, NativeArena arena, long node, Object value, String where, int depth) {
        if (depth > DEPTH_LIMIT) {
            throw new IllegalArgumentException(where + " nests deeper than " + DEPTH_LIMIT + " levels, which no value "
                    + "tree holds");
        }
        if (value == null) {
            access.putInt(node, KIND, NULL);
        } else if (value instanceof Boolean) {
            access.putInt(node, KIND, BOOLEAN);
            access.putInt(node, BOOLEAN_FIELD, ((Boolean) value) ? 1 : 0);
        } else if (value instanceof Number) {
            access.putInt(node, KIND, NUMBER);
            access.putDouble(node, NUMBER_FIELD, number((Number) value, where));
        } else if (value instanceof String) {
            access.putInt(node, KIND, TEXT);
            byte[] text = Scalars.utf8((String) value, where);
            access.putLong(node, TEXT_FIELD, placed(access, arena, text));
            access.putLong(node, LENGTH, text.length);
        } else if (value instanceof List) {
            writeList(access, arena, node, (List<?>) value, where, depth);
        } else if (value instanceof Map) {
            writeRecord(access, arena, node, (Map<?, ?>) value, where, depth);
        } else {
            throw new IllegalArgumentException(where + " is a value, written as null, a Boolean, a Number, a String, "
                    + "a List or a Map keyed by String, not a " + value.getClass().getName());
        }
    }

    private static void writeList(NativeAccess access, NativeArena arena, long node, List<?> list, String where,
                                  int depth) {
        access.putInt(node, KIND, LIST);
        if (list.isEmpty()) {
            return;
        }
        long items = arena.allocateAddress(list.size() * NODE_SIZE);
        int index = 0;
        for (Object item : list) {
            write(access, arena, items + index * NODE_SIZE, item, where + "[" + index + "]", depth + 1);
            index++;
        }
        access.putLong(node, ITEMS, items);
        access.putLong(node, COUNT, list.size());
    }

    private static void writeRecord(NativeAccess access, NativeArena arena, long node, Map<?, ?> record,
                                    String where, int depth) {
        access.putInt(node, KIND, RECORD);
        if (record.isEmpty()) {
            return;
        }
        long entries = arena.allocateAddress(record.size() * ENTRY_SIZE);
        int index = 0;
        for (Map.Entry<?, ?> member : record.entrySet()) {
            if (!(member.getKey() instanceof String)) {
                throw new IllegalArgumentException(where + " is a record, keyed by String, and holds the key "
                        + member.getKey());
            }
            String key = (String) member.getKey();
            long entry = entries + index * ENTRY_SIZE;
            byte[] encoded = Scalars.utf8(key, where + "'s key " + key);
            access.putLong(entry, KEY, placed(access, arena, encoded));
            access.putLong(entry, KEY_LENGTH, encoded.length);
            write(access, arena, entry + ENTRY_VALUE, member.getValue(), where + "." + key, depth + 1);
            index++;
        }
        access.putLong(node, ENTRIES, entries);
        access.putLong(node, COUNT, record.size());
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
     */
    static Object read(NativeAccess access, long node, int depth) {
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
                return readList(access, node, depth);
            case RECORD:
                return readRecord(access, node, depth);
            default:
                throw new IllegalStateException("the provider answered a value of kind " + kind
                        + (kind == NONE ? " below its root, where NONE may not stand" : ", which value.h does not "
                        + "publish"));
        }
    }

    private static List<Object> readList(NativeAccess access, long node, int depth) {
        long items = access.getLong(node, ITEMS);
        int count = Scalars.count(access.getLong(node, COUNT));
        if (items == 0 || count == 0) {
            return Collections.emptyList();
        }
        List<Object> list = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            list.add(read(access, items + index * NODE_SIZE, depth + 1));
        }
        return Collections.unmodifiableList(list);
    }

    private static Map<String, Object> readRecord(NativeAccess access, long node, int depth) {
        long entries = access.getLong(node, ENTRIES);
        int count = Scalars.count(access.getLong(node, COUNT));
        if (entries == 0 || count == 0) {
            return Collections.emptyMap();
        }
        Map<String, Object> record = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            long entry = entries + index * ENTRY_SIZE;
            record.put(text(access, access.getLong(entry, KEY), access.getLong(entry, KEY_LENGTH)),
                    read(access, entry + ENTRY_VALUE, depth + 1));
        }
        return Collections.unmodifiableMap(record);
    }

    private static String text(NativeAccess access, long text, long length) {
        if (text == 0) {
            return "";
        }
        return new String(access.readBytes(text, 0, Scalars.count(length)), StandardCharsets.UTF_8);
    }
}
