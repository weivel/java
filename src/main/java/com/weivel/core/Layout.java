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
 *
 * @implNote Public so that a package of the layer that reaches a C library other than the core, as {@code standard}
 * does, declares that library's structs in its own package, as every struct is declared in the package that owns the
 * call. A layout is arithmetic over sizes and reaches no native code.
 */
public final class Layout {
    /** One byte, as a {@code char} of a C array field. */
    public static final Layout BYTE = value(1);
    /** A 32-bit integer. */
    public static final Layout INT = value(4);
    /** A 64-bit integer, a {@code size_t} included. */
    public static final Layout LONG = value(8);
    /** A 64-bit IEEE 754 double. */
    public static final Layout DOUBLE = value(8);
    /** A pointer, 8 bytes on the 64-bit JVMs the layer runs on. */
    public static final Layout ADDRESS = value(8);

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

    /**
     * Bytes the C compiler leaves unused, stated so that every member's offset follows from the ones before it.
     *
     * @param size how many bytes
     * @return the padding
     */
    public static Layout padding(long size) {
        return new Layout(Kind.PADDING, Optional.<String>empty(), size, 1, Collections.<Layout>emptyList(),
                Collections.<Long>emptyList());
    }

    /**
     * Members laid out one after another, each at an offset its alignment allows.
     *
     * @param members the members, padding included, in order
     * @return the struct
     * @throws IllegalArgumentException when a member falls at an offset its alignment forbids, or the struct's size is
     *     not a multiple of its alignment, so padding the C compiler would add is missing
     */
    public static Layout struct(Layout... members) {
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

    /**
     * Members that all start at the union's start; the union is as large and as aligned as its largest.
     *
     * @param members the members
     * @return the union
     */
    public static Layout union(Layout... members) {
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

    /**
     * {@code count} elements of {@code element} in a row, as a C array field.
     *
     * @param count how many elements
     * @param element each element's layout
     * @return the sequence
     */
    public static Layout sequence(long count, Layout element) {
        return new Layout(Kind.SEQUENCE, Optional.<String>empty(), element.size * count, element.alignment,
                Collections.<Layout>emptyList(), Collections.<Long>emptyList());
    }

    /**
     * This layout under a name: a struct's as its C type, a member's as its field.
     *
     * @param newName the name
     * @return the named copy
     */
    public Layout withName(String newName) {
        return new Layout(kind, Optional.of(newName), size, alignment, members, offsets);
    }

    /**
     * Answers the name.
     *
     * @return the name given, or empty
     */
    public Optional<String> name() {
        return name;
    }

    /**
     * Answers the size.
     *
     * @return the size in bytes, the C compiler's {@code sizeof}
     */
    public long byteSize() {
        return size;
    }

    /**
     * Whether this is a struct.
     *
     * @return whether it is
     */
    public boolean isStruct() {
        return kind == Kind.STRUCT;
    }

    /**
     * Whether this is a union.
     *
     * @return whether it is
     */
    public boolean isUnion() {
        return kind == Kind.UNION;
    }

    /**
     * Whether this is padding.
     *
     * @return whether it is
     */
    public boolean isPadding() {
        return kind == Kind.PADDING;
    }

    /**
     * The members of a struct or union, padding included, in order.
     *
     * @return the members
     */
    public List<Layout> members() {
        return members;
    }

    /**
     * The offset of a field reached by naming each member on its way.
     *
     * @param names the member names, outermost first
     * @return the offset in bytes from this layout's start
     * @throws IllegalArgumentException when a name on the way is no member's
     */
    public long offsetOf(String... names) {
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
