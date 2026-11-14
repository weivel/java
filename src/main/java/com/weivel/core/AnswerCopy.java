package com.weivel.core;

import static com.weivel.core.CoreLayouts.offsetOf;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Copies an answer the core filled into a caller's struct out into Java values, before the struct's release. */
final class AnswerCopy {
    private AnswerCopy() {
    }

    static List<String> serviceIds(MemorySegment list) {
        long ids = offsetOf(CoreLayouts.SERVICE_LIST, "ids");
        int count = list.get(JAVA_INT, ids + offsetOf(CoreLayouts.STRINGS, "count"));
        if (count == 0) {
            return List.of();
        }
        MemorySegment items = list.get(ADDRESS, ids + offsetOf(CoreLayouts.STRINGS, "items"))
                .reinterpret(ADDRESS.byteSize() * count);
        List<String> copied = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            copied.add(items.getAtIndex(ADDRESS, index).reinterpret(Long.MAX_VALUE).getString(0,
                    StandardCharsets.UTF_8));
        }
        return List.copyOf(copied);
    }

    /** @implNote Stepped at the first element's {@code size}, the core's, reading only the fields both know. */
    static List<CapabilityImplementation> capabilities(MemorySegment list) {
        StructLayout element = CoreLayouts.CAPABILITY_IMPLEMENTATION;
        int count = list.get(JAVA_INT, offsetOf(CoreLayouts.CAPABILITY_LIST, "count"));
        if (count == 0) {
            return List.of();
        }
        MemorySegment first = list.get(ADDRESS, offsetOf(CoreLayouts.CAPABILITY_LIST, "items"))
                .reinterpret(element.byteSize());
        long stride = Integer.toUnsignedLong(first.get(JAVA_INT, offsetOf(element, "size")));
        if (stride < element.byteSize()) {
            throw new IllegalStateException("a capability element smaller than the fields the layer reads");
        }
        MemorySegment items = first.reinterpret(stride * count);
        List<CapabilityImplementation> copied = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long base = index * stride;
            copied.add(new CapabilityImplementation(items.get(JAVA_LONG, base + offsetOf(element, "implementation")),
                    items.get(JAVA_LONG, base + offsetOf(element, "provider"))));
        }
        return List.copyOf(copied);
    }
}
