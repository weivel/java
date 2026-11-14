package com.weivel.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The layout of a C value, declared by hand: a primitive, padding, a struct whose members follow each other with
 * their padding stated, a union whose members share its start, or a sequence of one element. Both native backends
 * read every struct through these offsets, which the tests compare with what the C compiler laid out.
 */
final class Layout {
    static final Layout INT = value(4);
    static final Layout LONG = value(8);
    static final Layout DOUBLE = value(8);
    static final Layout ADDRESS = value(8);

    private enum Kind {
        VALUE, PADDING, STRUCT, UNION, SEQUENCE
    }

    private final Kind kind;
    private final Optional<String> name;
    private final long size;
    private final long alignment;
    private final List<Layout> members;
    private final List<Long> offsets;

    private Layout(Kind kind, Optional<String> name, long size, long alignment, List<Layout> members,
                   List<Long> offsets) {
        this.kind = kind;
        this.name = name;
        this.size = size;
        this.alignment = alignment;
        this.members = members;
        this.offsets = offsets;
    }

    private static Layout value(long size) {
        return new Layout(Kind.VALUE, Optional.<String>empty(), size, size, Collections.<Layout>emptyList(),
                Collections.<Long>emptyList());
    }

    /**
     * Answers {@code size} when it is the 8 bytes every pointer of this table is laid out as.
     *
     * @throws IllegalStateException when the JVM's addresses are any other size
     */
    static long requireAddressSize(long size) {
        if (size != ADDRESS.byteSize()) {
            throw new IllegalStateException("weivel/java needs a 64-bit JVM: this one's addresses are " + size
                    + " bytes, and the core's structs are laid out for 8");
        }
        return size;
    }

    static Layout padding(long size) {
        return new Layout(Kind.PADDING, Optional.<String>empty(), size, 1, Collections.<Layout>emptyList(),
                Collections.<Long>emptyList());
    }

    /**
     * Members laid out one after another, each at an offset its alignment allows.
     *
     * @throws IllegalArgumentException when a member falls at an offset its alignment forbids, or the struct's size is
     *     not a multiple of its alignment, so padding the C compiler would add is missing
     */
    static Layout struct(Layout... members) {
        List<Long> offsets = new ArrayList<>();
        long offset = 0;
        long alignment = 1;
        for (Layout member : members) {
            if (offset % member.alignment != 0) {
                throw new IllegalArgumentException("a member at offset " + offset + " needs alignment "
                        + member.alignment);
            }
            offsets.add(offset);
            offset += member.size;
            alignment = Math.max(alignment, member.alignment);
        }
        if (offset % alignment != 0) {
            throw new IllegalArgumentException("a struct of " + offset + " bytes needs trailing padding");
        }
        return new Layout(Kind.STRUCT, Optional.<String>empty(), offset, alignment, listOf(members),
                Collections.unmodifiableList(offsets));
    }

    /** Members that all start at the union's start; the union is as large and as aligned as its largest. */
    static Layout union(Layout... members) {
        long size = 0;
        long alignment = 1;
        List<Long> offsets = new ArrayList<>();
        for (Layout member : members) {
            size = Math.max(size, member.size);
            alignment = Math.max(alignment, member.alignment);
            offsets.add(0L);
        }
        return new Layout(Kind.UNION, Optional.<String>empty(), size, alignment, listOf(members),
                Collections.unmodifiableList(offsets));
    }

    static Layout sequence(long count, Layout element) {
        return new Layout(Kind.SEQUENCE, Optional.<String>empty(), element.size * count, element.alignment,
                Collections.<Layout>emptyList(), Collections.<Long>emptyList());
    }

    Layout withName(String newName) {
        return new Layout(kind, Optional.of(newName), size, alignment, members, offsets);
    }

    Optional<String> name() {
        return name;
    }

    long byteSize() {
        return size;
    }

    boolean isStruct() {
        return kind == Kind.STRUCT;
    }

    boolean isUnion() {
        return kind == Kind.UNION;
    }

    boolean isPadding() {
        return kind == Kind.PADDING;
    }

    /** The members of a struct or union, padding included, in order. */
    List<Layout> members() {
        return members;
    }

    /**
     * The offset of a field reached by naming each member on its way.
     *
     * @throws IllegalArgumentException when a name on the way is no member's
     */
    long offsetOf(String... names) {
        long offset = 0;
        Layout current = this;
        for (String member : names) {
            int index = current.indexOf(member);
            offset += current.offsets.get(index);
            current = current.members.get(index);
        }
        return offset;
    }

    private int indexOf(String member) {
        for (int index = 0; index < members.size(); index++) {
            if (members.get(index).name.equals(Optional.of(member))) {
                return index;
            }
        }
        throw new IllegalArgumentException("no member named " + member + " in " + name.orElse("a nameless layout"));
    }

    private static List<Layout> listOf(Layout[] members) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(members)));
    }
}
