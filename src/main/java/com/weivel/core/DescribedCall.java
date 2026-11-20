package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes and reads the structs of one described call, by the plans its definition was bound with, in the arena the
 * call took: every input written straight into native memory as plain stores, and every answer copied out into Java
 * values. A member a caller leaves out stays at its zero. Structs nest no deeper than a value tree may, so a cycle of
 * pointers, in a caller's maps or a provider's answer, is refused rather than followed for ever.
 */
final class DescribedCall {
    private static final long WORD = Layout.ADDRESS.byteSize();

    private final NativeAccess access;
    private final NativeArena arena;
    private final DescribedHandles holdings;
    private final List<DescribedHandle> made = new ArrayList<>();
    private final List<DescribedHandle> used = new ArrayList<>();
    /** How many elements the last {@link #reached} wrote, for the count beside them. */
    long counted;

    DescribedCall(NativeAccess access, NativeArena arena, DescribedHandles holdings) {
        this.access = access;
        this.arena = arena;
        this.holdings = holdings;
    }

    long allocate(long size) {
        return arena.allocateAddress(size);
    }

    /** Lets go of every handle this call handed its provider, once the native call has returned. */
    void leaveUsedHandles() {
        for (DescribedHandle handle : used) {
            handle.leave();
        }
        used.clear();
    }

    /** Releases every handle this call wrapped, for an answer that failed to be copied out whole. */
    void releaseMadeHandles() {
        for (DescribedHandle handle : made) {
            handle.release();
        }
    }

    /**
     * Writes {@code value} as the struct {@code plan} at {@code base}, its leading size set.
     *
     * @throws IllegalArgumentException for anything but a {@code Map}, a member the struct does not let a caller
     *     write, a value its member's kind does not hold, or structs nested deeper than a value tree may be
     */
    void writeStruct(StructPlan plan, long base, Object value, String where) {
        writeStruct(plan, base, value, where, 0);
    }

    private void writeStruct(StructPlan plan, long base, Object value, String where, int depth) {
        if (depth > ValueTrees.DEPTH_LIMIT) {
            throw new IllegalArgumentException(where + " nests structs deeper than " + ValueTrees.DEPTH_LIMIT
                    + " levels, which no call hands on");
        }
        if (plan.leadingSize) {
            access.putInt(base, 0, (int) plan.size);
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(where + " is a " + plan.name + ", written as a Map keyed by member "
                    + "name, not " + Scalars.described(value));
        }
        Map<?, ?> members = (Map<?, ?>) value;
        refuseUnknownNames(members, plan, where);
        for (MemberPlan member : plan.visible) {
            if (members.containsKey(member.name)) {
                writeMember(member, base, members.get(member.name), where + "." + member.name, depth);
            }
        }
    }

    private static void refuseUnknownNames(Map<?, ?> members, StructPlan plan, String where) {
        if (plan.visibleNames.containsAll(members.keySet())) {
            return;
        }
        for (Object name : members.keySet()) {
            if (!plan.visibleNames.contains(name)) {
                throw new IllegalArgumentException(where + " has no " + name + " a caller writes; it takes "
                        + (plan.visibleNames.isEmpty() ? "nothing" : String.join(", ", plan.visibleNames)));
            }
        }
    }

    private void writeMember(MemberPlan member, long base, Object value, String where, int depth) {
        long at = base + member.offset;
        if (DescribedLayouts.isScalar(member.kind)) {
            Scalars.write(access, member.kind, at, Scalars.bits(member.kind, value, where));
        } else if (member.kind == DescribedKind.STRUCT) {
            writeStruct(member.target, at, value, where, depth + 1);
        } else if (member.kind == DescribedKind.VALUE) {
            ValueTrees.write(access, arena, at, value, where, 0);
        } else {
            access.putLong(at, 0, reached(member, value, where, depth + 1));
            if (member.isCounted()) {
                access.putLong(base + member.countOffset, 0, counted);
            }
        }
    }

    /**
     * The address of what a pointer-sized member or parameter reaches, written into the arena; NULL for a text,
     * pointer or handle handed as null, and for an empty array, texts or bytes, whose count {@link #counted} then holds
     * as 0. A handle handed in is in use until {@link #leaveUsedHandles}.
     */
    long reached(MemberPlan plan, Object value, String where) {
        return reached(plan, value, where, 0);
    }

    private long reached(MemberPlan plan, Object value, String where, int depth) {
        counted = 0;
        switch (plan.kind) {
            case DescribedKind.TEXT:
                return text(value, where);
            case DescribedKind.TEXTS:
                return texts(value, where);
            case DescribedKind.BYTES:
                return bytes(value, where);
            case DescribedKind.POINTER:
                return pointed(plan, value, where, depth);
            case DescribedKind.ARRAY:
                return array(plan, value, where, depth);
            case DescribedKind.HANDLE:
                return value == null ? 0 : used(holdings.use(value, plan.handleType, where));
            default:
                throw new IllegalArgumentException(where + " is the provider's own, and a caller writes nothing there");
        }
    }

    private long used(DescribedHandle handle) {
        used.add(handle);
        return handle.pointer();
    }

    private long text(Object value, String where) {
        if (value == null) {
            return 0;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(where + " is a text, written as a String or null, not "
                    + Scalars.described(value));
        }
        byte[] encoded = Scalars.utf8((String) value, where);
        long address = arena.allocateAddress(encoded.length + 1L);
        access.writeBytes(address, 0, encoded);
        return address;
    }

    private long texts(Object value, String where) {
        Object[] texts = snapshot(value, where);
        if (texts.length == 0) {
            return 0;
        }
        long items = arena.allocateAddress(texts.length * WORD);
        for (int index = 0; index < texts.length; index++) {
            access.putLong(items, index * WORD, text(texts[index], where + "[" + index + "]"));
        }
        counted = texts.length;
        return items;
    }

    private long bytes(Object value, String where) {
        if (!(value instanceof byte[])) {
            throw new IllegalArgumentException(where + " is bytes, written as a byte[], not "
                    + Scalars.described(value));
        }
        byte[] bytes = (byte[]) value;
        if (bytes.length == 0) {
            return 0;
        }
        long address = arena.allocateAddress(bytes.length);
        access.writeBytes(address, 0, bytes);
        counted = bytes.length;
        return address;
    }

    private long pointed(MemberPlan plan, Object value, String where, int depth) {
        if (value == null && plan.element == DescribedKind.STRUCT) {
            return 0;
        }
        long address = arena.allocateAddress(plan.stride);
        writeElement(plan, address, value, where, depth);
        return address;
    }

    private long array(MemberPlan plan, Object value, String where, int depth) {
        Object[] items = snapshot(value, where);
        if (items.length == 0) {
            return 0;
        }
        long address = arena.allocateAddress(items.length * plan.stride);
        for (int index = 0; index < items.length; index++) {
            writeElement(plan, address + index * plan.stride, items[index], where + "[" + index + "]", depth);
        }
        counted = items.length;
        return address;
    }

    private void writeElement(MemberPlan plan, long address, Object value, String where, int depth) {
        if (plan.element == DescribedKind.STRUCT) {
            writeStruct(plan.target, address, value, where, depth);
        } else if (plan.element == DescribedKind.VALUE) {
            ValueTrees.write(access, arena, address, value, where, 0);
        } else {
            Scalars.write(access, plan.element, address, Scalars.bits(plan.element, value, where));
        }
    }

    /**
     * A counted input's elements, taken once, so the block sized for them and the walk writing them agree however the
     * list changes meanwhile.
     */
    private static Object[] snapshot(Object value, String where) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(where + " is counted, written as a List, not "
                    + Scalars.described(value));
        }
        return ((List<?>) value).toArray();
    }

    /**
     * Reads the struct {@code plan} at {@code base} as a {@code Map} in member order, leaving out a NULL pointer, a
     * NULL handle and a value of kind NONE; a handle it holds is wrapped and held.
     *
     * @throws IllegalStateException for structs nested deeper than a value tree may be, as a cycle of pointers is
     */
    Map<String, Object> readStruct(StructPlan plan, long base) {
        return readStruct(plan, base, 0);
    }

    private Map<String, Object> readStruct(StructPlan plan, long base, int depth) {
        if (depth > ValueTrees.DEPTH_LIMIT) {
            throw new IllegalStateException("the provider answered structs nested deeper than "
                    + ValueTrees.DEPTH_LIMIT + " levels");
        }
        Map<String, Object> members = new LinkedHashMap<>();
        for (MemberPlan member : plan.visible) {
            readMember(members, member, base, depth);
        }
        return Collections.unmodifiableMap(members);
    }

    private void readMember(Map<String, Object> members, MemberPlan member, long base, int depth) {
        long at = base + member.offset;
        if (DescribedLayouts.isScalar(member.kind)) {
            members.put(member.name, Scalars.read(access, member.kind, at));
        } else if (member.kind == DescribedKind.STRUCT) {
            members.put(member.name, readStruct(member.target, at, depth + 1));
        } else if (member.kind == DescribedKind.VALUE) {
            if (ValueTrees.kindAt(access, at) != ValueTrees.NONE) {
                members.put(member.name, ValueTrees.read(access, at, 0));
            }
        } else {
            long count = member.isCounted() ? access.getLong(base + member.countOffset, 0) : 0;
            Object read = readReached(member, access.getLong(at, 0), count, depth + 1);
            if (read != null || member.kind == DescribedKind.TEXT) {
                members.put(member.name, read);
            }
        }
    }

    /**
     * What the pointer {@code pointer} reaches, as {@code plan} says: null for a NULL text, pointer or handle, and an
     * empty list or array for a NULL or empty counted one.
     */
    Object readReached(MemberPlan plan, long pointer, long count) {
        return readReached(plan, pointer, count, 0);
    }

    private Object readReached(MemberPlan plan, long pointer, long count, int depth) {
        switch (plan.kind) {
            case DescribedKind.TEXT:
                return pointer == 0 ? null : access.readText(pointer);
            case DescribedKind.TEXTS:
                return readTexts(pointer, count);
            case DescribedKind.BYTES:
                return pointer == 0 || count == 0 ? new byte[0] : access.readBytes(pointer, 0, Scalars.count(count));
            case DescribedKind.POINTER:
                return pointer == 0 ? null : readElement(plan, pointer, depth);
            case DescribedKind.ARRAY:
                return readArray(plan, pointer, count, depth);
            case DescribedKind.HANDLE:
                return pointer == 0 ? null : hold(plan, pointer);
            default:
                return null;
        }
    }

    DescribedHandle hold(MemberPlan plan, long pointer) {
        DescribedHandle handle = holdings.hold(pointer, plan.handleType, plan.release);
        made.add(handle);
        return handle;
    }

    private List<Object> readTexts(long items, long count) {
        if (items == 0 || count == 0) {
            return Collections.emptyList();
        }
        int length = Scalars.count(count);
        List<Object> texts = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            long text = access.getLong(items, index * WORD);
            texts.add(text == 0 ? null : access.readText(text));
        }
        return Collections.unmodifiableList(texts);
    }

    private List<Object> readArray(MemberPlan plan, long items, long count, int depth) {
        if (items == 0 || count == 0) {
            return Collections.emptyList();
        }
        int length = Scalars.count(count);
        List<Object> elements = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            elements.add(readElement(plan, items + index * plan.stride, depth));
        }
        return Collections.unmodifiableList(elements);
    }

    private Object readElement(MemberPlan plan, long address, int depth) {
        if (plan.element == DescribedKind.STRUCT) {
            return readStruct(plan.target, address, depth);
        }
        if (plan.element == DescribedKind.VALUE) {
            return ValueTrees.read(access, address, 0);
        }
        return Scalars.read(access, plan.element, address);
    }
}
