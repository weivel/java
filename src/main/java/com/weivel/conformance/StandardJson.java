package com.weivel.conformance;

import com.weivel.core.Answer;
import com.weivel.core.ForeignFunction;
import com.weivel.core.ForeignLibrary;
import com.weivel.core.ForeignMemory;
import com.weivel.core.NativeType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The standard's JSON reader, bound from the standard's binary, reading a document into Java values: an object as a
 * {@link Map} in member order, an array as a {@link List}, a string, a {@link Boolean}, an integral number as a
 * {@link Long} and any other as a {@link Double}, and {@code null} as {@link #NULL}.
 */
final class StandardJson implements AutoCloseable {
    /** JSON's {@code null}, so a member that is null is told apart from one that is absent. */
    static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private static final int KIND_NULL = 0;
    private static final int KIND_BOOL = 1;
    private static final int KIND_NUMBER = 2;
    private static final int KIND_STRING = 3;
    private static final int KIND_ARRAY = 4;
    private static final int KIND_OBJECT = 5;
    private static final long OUT_BYTES = 8;

    private final ForeignLibrary library;
    private final ForeignFunction parse;
    private final ForeignFunction free;
    private final ForeignFunction kindOf;
    private final ForeignFunction objectSize;
    private final ForeignFunction keyAt;
    private final ForeignFunction valueAt;
    private final ForeignFunction arrayLength;
    private final ForeignFunction arrayAt;
    private final ForeignFunction asString;
    private final ForeignFunction stringLength;
    private final ForeignFunction asBool;
    private final ForeignFunction asInt64;
    private final ForeignFunction asDouble;

    private StandardJson(ForeignLibrary library) {
        this.library = library;
        this.parse = library.function("weivel_json_parse", NativeType.ADDRESS, NativeType.ADDRESS, NativeType.LONG,
                NativeType.ADDRESS);
        this.free = library.function("weivel_json_free", NativeType.VOID, NativeType.ADDRESS);
        this.kindOf = library.function("weivel_json_kind_of", NativeType.INT, NativeType.ADDRESS);
        this.objectSize = library.function("weivel_json_object_size", NativeType.LONG, NativeType.ADDRESS);
        this.keyAt = library.function("weivel_json_object_key_at", NativeType.ADDRESS, NativeType.ADDRESS,
                NativeType.LONG);
        this.valueAt = library.function("weivel_json_object_value_at", NativeType.ADDRESS, NativeType.ADDRESS,
                NativeType.LONG);
        this.arrayLength = library.function("weivel_json_array_length", NativeType.LONG, NativeType.ADDRESS);
        this.arrayAt = library.function("weivel_json_array_at", NativeType.ADDRESS, NativeType.ADDRESS,
                NativeType.LONG);
        this.asString = library.function("weivel_json_as_string", NativeType.ADDRESS, NativeType.ADDRESS);
        this.stringLength = library.function("weivel_json_string_length", NativeType.LONG, NativeType.ADDRESS);
        this.asBool = library.function("weivel_json_as_bool", NativeType.INT, NativeType.ADDRESS, NativeType.ADDRESS);
        this.asInt64 = library.function("weivel_json_as_int64", NativeType.INT, NativeType.ADDRESS,
                NativeType.ADDRESS);
        this.asDouble = library.function("weivel_json_as_double", NativeType.INT, NativeType.ADDRESS,
                NativeType.ADDRESS);
    }

    static Answer<StandardJson> load(Path standardBinary) {
        Answer<ForeignLibrary> opened = ForeignLibrary.open(Objects.requireNonNull(standardBinary, "standardBinary"),
                Arrays.asList("weivel_json_parse", "weivel_json_free", "weivel_json_kind_of",
                        "weivel_json_object_size", "weivel_json_object_key_at", "weivel_json_object_value_at",
                        "weivel_json_array_length", "weivel_json_array_at", "weivel_json_as_string",
                        "weivel_json_string_length", "weivel_json_as_bool", "weivel_json_as_int64",
                        "weivel_json_as_double"));
        if (!(opened instanceof Answer.Ok)) {
            return Answer.retyped(opened);
        }
        return new Answer.Ok<>(new StandardJson(((Answer.Ok<ForeignLibrary>) opened).value()));
    }

    /**
     * Reads one document.
     *
     * @throws IllegalArgumentException naming {@code where} when the standard's reader refuses the document
     */
    Object read(byte[] document, String where) {
        try (ForeignMemory memory = library.memory()) {
            long root = parse.call(memory.bytes(document), document.length, 0L);
            if (root == 0) {
                throw new IllegalArgumentException("the standard's JSON reader refused " + where);
            }
            try {
                return value(memory, root);
            } finally {
                free.call(root);
            }
        }
    }

    private Object value(ForeignMemory memory, long value) {
        int kind = (int) kindOf.call(value);
        switch (kind) {
            case KIND_NULL:
                return NULL;
            case KIND_BOOL:
                return Boolean.valueOf(memory.getInt(answered(memory, asBool, value), 0) != 0);
            case KIND_NUMBER:
                return number(memory, value);
            case KIND_STRING:
                return text(memory, value);
            case KIND_ARRAY:
                return array(memory, value);
            case KIND_OBJECT:
                return object(memory, value);
            default:
                throw new IllegalStateException("the standard's JSON reader answered a kind " + kind
                        + " this driver does not know");
        }
    }

    private Object number(ForeignMemory memory, long value) {
        long out = memory.allocate(OUT_BYTES);
        if (asInt64.call(value, out) != 0) {
            return Long.valueOf(memory.getLong(out, 0));
        }
        return Double.valueOf(Double.longBitsToDouble(memory.getLong(answered(memory, asDouble, value), 0)));
    }

    private long answered(ForeignMemory memory, ForeignFunction accessor, long value) {
        long out = memory.allocate(OUT_BYTES);
        if (accessor.call(value, out) == 0) {
            throw new IllegalStateException("the standard's JSON reader refused a value of the kind it answered");
        }
        return out;
    }

    private String text(ForeignMemory memory, long value) {
        long length = stringLength.call(value);
        long bytes = asString.call(value);
        return new String(memory.readBytes(bytes, 0, Math.toIntExact(length)), StandardCharsets.UTF_8);
    }

    private List<Object> array(ForeignMemory memory, long value) {
        long length = arrayLength.call(value);
        List<Object> items = new ArrayList<>();
        for (long index = 0; index < length; index++) {
            items.add(value(memory, arrayAt.call(value, index)));
        }
        return Collections.unmodifiableList(items);
    }

    private Map<String, Object> object(ForeignMemory memory, long value) {
        long size = objectSize.call(value);
        Map<String, Object> members = new LinkedHashMap<>();
        for (long index = 0; index < size; index++) {
            members.put(memory.readText(keyAt.call(value, index)), value(memory, valueAt.call(value, index)));
        }
        return Collections.unmodifiableMap(members);
    }

    @Override
    public void close() {
        library.close();
    }
}
