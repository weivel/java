package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A C struct a native backend is asked to lay out by its own rules, as a description names it: its fields in order,
 * each a C scalar, a pointer or an embedded struct. It carries no offset, so the backend's answer is its own and can
 * be held against what the description says.
 */
final class FfiStruct {
    private final String name;
    private final List<Field> fields;

    FfiStruct(String name, List<Field> fields) {
        this.name = Objects.requireNonNull(name, "name");
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
    }

    String name() {
        return name;
    }

    List<Field> fields() {
        return fields;
    }

    /** What a field holds, as far as laying it out goes. */
    enum Shape {
        U32, U64, I64, F64, SIZE, POINTER, STRUCT
    }

    /** One field: its name, its shape and, for {@link Shape#STRUCT}, the struct it embeds. */
    static final class Field {
        private final String name;
        private final Shape shape;
        private final FfiStruct embedded;

        private Field(String name, Shape shape, FfiStruct embedded) {
            this.name = Objects.requireNonNull(name, "name");
            this.shape = Objects.requireNonNull(shape, "shape");
            this.embedded = embedded;
        }

        static Field of(String name, Shape shape) {
            if (shape == Shape.STRUCT) {
                throw new IllegalArgumentException("an embedded struct names the struct it embeds");
            }
            return new Field(name, shape, null);
        }

        static Field embedding(String name, FfiStruct embedded) {
            return new Field(name, Shape.STRUCT, Objects.requireNonNull(embedded, "embedded"));
        }

        String name() {
            return name;
        }

        Shape shape() {
            return shape;
        }

        FfiStruct embedded() {
            return embedded;
        }
    }

    /** Where a backend placed a struct's fields, in the struct's order, and how large it made the struct. */
    static final class Placement {
        private final long size;
        private final long[] offsets;
        private final long[] sizes;

        Placement(long size, long[] offsets, long[] sizes) {
            this.size = size;
            this.offsets = offsets.clone();
            this.sizes = sizes.clone();
        }

        long size() {
            return size;
        }

        long offset(int field) {
            return offsets[field];
        }

        long size(int field) {
            return sizes[field];
        }
    }
}
