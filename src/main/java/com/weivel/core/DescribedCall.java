package com.weivel.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Writes and reads the structs of one described call, by the plans its definition was bound with, in the arena the
 * call took: every input written straight into native memory as plain stores, and every answer copied out into Java
 * values. A member a caller leaves out stays at its zero. Structs nest no deeper than a value tree may, so a cycle of
 * pointers, in a caller's maps or a provider's answer, is refused rather than followed for ever. A definition an answer
 * carries is bound as it is read, and the call collects each one's scope for the answer that will keep it.
 *
 * @implNote Both walks keep their own stack of structs still to write or read rather than recursing, so the depth
 *     limit is reached on any thread's stack, however small: a recursive walk overflowed a Linux runner's default
 *     stack before it reached the limit.
 */
final class DescribedCall {
    private static final long WORD = Layout.ADDRESS.byteSize();

    private final NativeAccess access;
    private final NativeArena arena;
    private final DescribedHandles holdings;
    private final Carrier carrier;
    private final boolean answersApart;
    private final List<DescribedHandle> made = new ArrayList<>();
    private final List<KeptAnswer.Carried> carried = new ArrayList<>();
    private NativeArena answers;
    private final List<DescribedHandle> used = new ArrayList<>();
    private final Deque<StructWrite> writes = new ArrayDeque<>();
    private final Deque<StructRead> reads = new ArrayDeque<>();
    /** How many elements the last {@link #reached} wrote, for the count beside them. */
    long counted;

    /**
     * @param carrier binds a definition one of the call's answers carries
     * @param answersApart whether the call's answers are written in memory of their own, apart from {@code arena},
     *     since an answer carrying a definition outlives the call and so the arena's reuse
     */
    DescribedCall(NativeAccess access, NativeArena arena, DescribedHandles holdings, Carrier carrier,
                  boolean answersApart) {
        this.access = access;
        this.arena = arena;
        this.holdings = holdings;
        this.carrier = carrier;
        this.answersApart = answersApart;
    }

    long allocate(long size) {
        return arena.allocateAddress(size);
    }

    /** Zeroed memory for an empty answer the provider fills: in the arena, or apart from it for answers kept. */
    long allocateAnswer(long size) {
        if (!answersApart) {
            return arena.allocateAddress(size);
        }
        if (answers == null) {
            answers = new NativeArena(access);
        }
        return answers.allocateAddress(size);
    }

    /** Hands the memory the answers were written in apart to the answer that keeps them; null when there is none. */
    NativeArena takeAnswers() {
        NativeArena taken = answers;
        answers = null;
        return taken;
    }

    /** Frees the memory the answers were written in apart, for answers released before the call answers. */
    void closeAnswers() {
        NativeArena taken = takeAnswers();
        if (taken != null) {
            taken.close();
        }
    }

    /** The scope of every definition the call's answers carried, in the order they were read. */
    List<KeptAnswer.Carried> carried() {
        return carried;
    }

    /** Ends every definition the call's answers carried, for an answer that failed to be copied out whole. */
    void endCarried() {
        for (KeptAnswer.Carried definition : carried) {
            definition.scope.close(definition.holdings::releaseAll);
        }
    }

    /**
     * Lets go of every handle this call handed its provider, once the native call has returned: each one's count is
     * dropped even when freeing another throws, and the first such failure is thrown once all are dropped.
     */
    void leaveUsedHandles() {
        RuntimeException firstFailure = null;
        Error firstError = null;
        try {
            for (DescribedHandle handle : used) {
                try {
                    handle.leave();
                } catch (RuntimeException failed) {
                    firstFailure = firstFailure == null ? failed : firstFailure;
                } catch (Error failed) {
                    firstError = firstError == null ? failed : firstError;
                }
            }
        } finally {
            used.clear();
        }
        if (firstError != null) {
            throw firstError;
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
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
        writes.push(new StructWrite(plan, base, value, CallPath.of(where), 0));
        drainWrites();
    }

    /**
     * The address of what a pointer-sized member or parameter reaches, written into the arena; NULL for a text,
     * pointer or handle handed as null, and for an empty array, texts or bytes, whose count {@link #counted} then holds
     * as 0. A handle handed in is in use until {@link #leaveUsedHandles}.
     */
    long reached(MemberPlan plan, Object value, String where) {
        return reached(plan, value, CallPath.of(where));
    }

    /** As {@link #reached(MemberPlan, Object, String)}, for an input whose place its step named at the bind. */
    long reached(MemberPlan plan, Object value, CallPath where) {
        long address = reached(plan, value, where, 0);
        long count = counted;
        drainWrites();
        counted = count;
        return address;
    }

    private void drainWrites() {
        try {
            while (!writes.isEmpty()) {
                writeOne(writes.pop());
            }
        } finally {
            writes.clear();
        }
    }

    private void writeOne(StructWrite next) {
        if (next.depth > ValueTrees.DEPTH_LIMIT) {
            throw new IllegalArgumentException(next.where + " nests structs deeper than " + ValueTrees.DEPTH_LIMIT
                    + " levels, which no call hands on");
        }
        StructPlan plan = next.plan;
        if (plan.leadingSize) {
            access.putInt(next.base, 0, (int) plan.size);
        }
        if (!(next.value instanceof Map)) {
            throw new IllegalArgumentException(next.where + " is a " + plan.name + ", written as a Map keyed by "
                    + "member name, not " + Scalars.described(next.value));
        }
        Map<?, ?> members = (Map<?, ?>) next.value;
        refuseUnknownNames(members, plan, next.where);
        for (MemberPlan member : plan.visible) {
            if (members.containsKey(member.name)) {
                writeMember(member, next.base, members.get(member.name), next.where.member(member.name),
                        next.depth);
            }
        }
    }

    private static void refuseUnknownNames(Map<?, ?> members, StructPlan plan, CallPath where) {
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

    private void writeMember(MemberPlan member, long base, Object value, CallPath where, int depth) {
        long at = base + member.offset;
        if (DescribedLayouts.isScalar(member.kind)) {
            Scalars.write(access, member.kind, at, Scalars.bits(member.kind, value, where));
        } else if (member.kind == DescribedKind.STRUCT) {
            writes.push(new StructWrite(member.target, at, value, where, depth + 1));
        } else if (member.kind == DescribedKind.VALUE) {
            ValueTrees.write(access, arena, at, value, where);
        } else {
            access.putLong(at, 0, reached(member, value, where, depth + 1));
            if (member.isCounted()) {
                access.putLong(base + member.countOffset, 0, counted);
            }
        }
    }

    private long reached(MemberPlan plan, Object value, CallPath where, int depth) {
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

    private long text(Object value, CallPath where) {
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

    private long texts(Object value, CallPath where) {
        Object[] texts = snapshot(value, where);
        if (texts.length == 0) {
            return 0;
        }
        long items = arena.allocateAddress(texts.length * WORD);
        for (int index = 0; index < texts.length; index++) {
            access.putLong(items, index * WORD, text(texts[index], where.element(index)));
        }
        counted = texts.length;
        return items;
    }

    private long bytes(Object value, CallPath where) {
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

    private long pointed(MemberPlan plan, Object value, CallPath where, int depth) {
        if (value == null && plan.element == DescribedKind.STRUCT) {
            return 0;
        }
        long address = arena.allocateAddress(plan.stride);
        writeElement(plan, address, value, where, depth);
        return address;
    }

    private long array(MemberPlan plan, Object value, CallPath where, int depth) {
        Object[] items = snapshot(value, where);
        if (items.length == 0) {
            return 0;
        }
        long address = arena.allocateAddress(items.length * plan.stride);
        for (int index = 0; index < items.length; index++) {
            writeElement(plan, address + index * plan.stride, items[index], where.element(index), depth);
        }
        counted = items.length;
        return address;
    }

    private void writeElement(MemberPlan plan, long address, Object value, CallPath where, int depth) {
        if (plan.element == DescribedKind.STRUCT) {
            writes.push(new StructWrite(plan.target, address, value, where, depth));
        } else if (plan.element == DescribedKind.VALUE) {
            ValueTrees.write(access, arena, address, value, where);
        } else {
            Scalars.write(access, plan.element, address, Scalars.bits(plan.element, value, where));
        }
    }

    /**
     * A counted input's elements, taken once, so the block sized for them and the walk writing them agree however the
     * list changes meanwhile.
     */
    private static Object[] snapshot(Object value, CallPath where) {
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
        Map<String, Object> struct = startStruct(plan, base, 0);
        drainReads();
        return struct;
    }

    /**
     * What the pointer {@code pointer} reaches, as {@code plan} says: null for a NULL text, pointer or handle, and an
     * empty list or array for a NULL or empty counted one.
     */
    Object readReached(MemberPlan plan, long pointer, long count) {
        Object read = readReached(plan, pointer, count, 0);
        drainReads();
        return read;
    }

    private void drainReads() {
        try {
            while (!reads.isEmpty()) {
                StructRead next = reads.pop();
                readMembers(next.plan, next.base, next.into, next.depth);
            }
        } finally {
            reads.clear();
        }
    }

    /** A struct's map, its members left to be read into it, which it shows as they arrive. */
    private Map<String, Object> startStruct(StructPlan plan, long base, int depth) {
        if (depth > ValueTrees.DEPTH_LIMIT) {
            throw new IllegalStateException("the provider answered structs nested deeper than "
                    + ValueTrees.DEPTH_LIMIT + " levels");
        }
        StructValues members = new StructValues(plan);
        reads.push(new StructRead(plan, base, members, depth));
        return members;
    }

    private void readMembers(StructPlan plan, long base, StructValues members, int depth) {
        MemberPlan[] visible = plan.visible;
        for (int position = 0; position < visible.length; position++) {
            MemberPlan member = visible[position];
            long at = base + member.offset;
            if (DescribedLayouts.isScalar(member.kind)) {
                members.set(position, Scalars.read(access, member.kind, at));
            } else if (member.kind == DescribedKind.STRUCT) {
                members.set(position, startStruct(member.target, at, depth + 1));
            } else if (member.kind == DescribedKind.VALUE) {
                if (ValueTrees.kindAt(access, at) != ValueTrees.NONE) {
                    members.set(position, ValueTrees.read(access, at));
                }
            } else {
                long count = member.isCounted() ? access.getLong(base + member.countOffset, 0) : 0;
                Object read = readReached(member, access.getLong(at, 0), count, depth + 1);
                if (read != null || member.kind == DescribedKind.TEXT) {
                    members.set(position, read);
                }
            }
        }
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
            case DescribedKind.DEFINITION:
                return pointer == 0 ? null : carry(plan, pointer);
            default:
                return null;
        }
    }

    /** Binds the definition at {@code pointer}, which one of the call's answers carries. */
    BoundDefinition carry(MemberPlan plan, long pointer) {
        return carrier.carry(plan.definition, pointer, carried);
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
            return startStruct(plan.target, address, depth);
        }
        if (plan.element == DescribedKind.VALUE) {
            return ValueTrees.read(access, address);
        }
        return Scalars.read(access, plan.element, address);
    }

    /** Binds a definition an answer carries, by the nested description its member or parameter names. */
    interface Carrier {
        /** A carrier for a definition whose description nests none, which no answer of it can carry. */
        Carrier NONE = (definition, pointer, carried) -> {
            throw new IllegalStateException("an answer carried the definition " + definition
                    + ", which its description does not nest");
        };

        /**
         * Binds the definition at {@code pointer}, its function pointers, context and releases read from it now.
         *
         * @param definition the {@code definition} of the nested description describing it
         * @param carried where its scope is added, for the answer that will keep it
         * @throws IllegalStateException for a definition its description nests none of, or whose struct this binding
         *     cannot bind
         */
        BoundDefinition carry(String definition, long pointer, List<KeptAnswer.Carried> carried);
    }

    /** A struct still to write, at its depth. */
    private static final class StructWrite {
        final StructPlan plan;
        final long base;
        final Object value;
        final CallPath where;
        final int depth;

        StructWrite(StructPlan plan, long base, Object value, CallPath where, int depth) {
            this.plan = plan;
            this.base = base;
            this.value = value;
            this.where = where;
            this.depth = depth;
        }
    }

    /** A struct still to read, into the map already standing for it. */
    private static final class StructRead {
        final StructPlan plan;
        final long base;
        final StructValues into;
        final int depth;

        StructRead(StructPlan plan, long base, StructValues into, int depth) {
            this.plan = plan;
            this.base = base;
            this.into = into;
            this.depth = depth;
        }
    }
}
