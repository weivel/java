package com.weivel.core;

import static com.weivel.core.CoreLayouts.offsetOf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Copies an answer the core filled into a caller's struct out into Java values, before the struct's release. */
final class AnswerCopy {
    private AnswerCopy() {
    }

    static List<String> serviceIds(NativeRegion list) {
        long ids = offsetOf(CoreLayouts.SERVICE_LIST, "ids");
        int count = list.getInt(ids + offsetOf(CoreLayouts.STRINGS, "count"));
        if (count == 0) {
            return Collections.emptyList();
        }
        long addressSize = Layout.ADDRESS.byteSize();
        NativeRegion items = list.at(list.getAddress(ids + offsetOf(CoreLayouts.STRINGS, "items")),
                addressSize * count);
        List<String> copied = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            copied.add(items.textAt(items.getAddress(index * addressSize)));
        }
        return Collections.unmodifiableList(copied);
    }

    /** @implNote Stepped at the first element's {@code size}, the core's, reading only the fields both know. */
    static List<CapabilityImplementation> capabilities(NativeRegion list) {
        Layout element = CoreLayouts.CAPABILITY_IMPLEMENTATION;
        int count = list.getInt(offsetOf(CoreLayouts.CAPABILITY_LIST, "count"));
        if (count == 0) {
            return Collections.emptyList();
        }
        long itemsAddress = list.getAddress(offsetOf(CoreLayouts.CAPABILITY_LIST, "items"));
        NativeRegion first = list.at(itemsAddress, element.byteSize());
        long stride = Integer.toUnsignedLong(first.getInt(offsetOf(element, "size")));
        if (stride < element.byteSize()) {
            throw new IllegalStateException("a capability element smaller than the fields the layer reads");
        }
        NativeRegion items = list.at(itemsAddress, stride * count);
        List<CapabilityImplementation> copied = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long base = index * stride;
            copied.add(new CapabilityImplementation(items.getLong(base + offsetOf(element, "implementation")),
                    items.getLong(base + offsetOf(element, "provider"))));
        }
        return Collections.unmodifiableList(copied);
    }
}
