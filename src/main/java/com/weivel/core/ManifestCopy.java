package com.weivel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Copies a {@code weivel_manifest} another C library laid out into a {@link Manifest} value, every array of growing
 * elements stepped at the stride its first element's {@code size} declares.
 */
final class ManifestCopy {
    private static final Layout MANIFEST = CoreLayouts.MANIFEST;
    private static final long LIST_ITEMS = CoreLayouts.offsetOf(CoreLayouts.STRINGS, "items");
    private static final long LIST_COUNT = CoreLayouts.offsetOf(CoreLayouts.STRINGS, "count");
    private static final long ELEMENT_SIZE = 0;

    private ManifestCopy() {
    }

    static Manifest read(NativeRegion struct) {
        return new Manifest(text(struct, struct.getAddress(offset("import_name"))),
                struct.getInt(offset("api")),
                optionalText(struct, struct.getAddress(offset("entry"))),
                strings(struct, offset("targets")),
                strings(struct, offset("capabilities", "provides")),
                strings(struct, offset("capabilities", "consumes")),
                offers(struct, offset("services", "provides")),
                wants(struct, offset("services", "consumes")),
                strings(struct, offset("permissions")),
                optionalText(struct, struct.getAddress(offset("identity"))));
    }

    private static long offset(String... names) {
        return MANIFEST.offsetOf(names);
    }

    private static String text(NativeRegion struct, long pointer) {
        if (pointer == 0) {
            throw new IllegalArgumentException("the manifest has no import name, which the core requires");
        }
        return struct.textAt(pointer);
    }

    private static Optional<String> optionalText(NativeRegion struct, long pointer) {
        return pointer == 0 ? Optional.<String>empty() : Optional.of(struct.textAt(pointer));
    }

    private static int count(NativeRegion list, long offset) {
        return list.getInt(offset + LIST_COUNT);
    }

    private static List<String> strings(NativeRegion struct, long offset) {
        int count = count(struct, offset);
        NativeRegion items = struct.at(struct.getAddress(offset + LIST_ITEMS), count * Layout.ADDRESS.byteSize());
        List<String> strings = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            strings.add(struct.textAt(items.getAddress(index * Layout.ADDRESS.byteSize())));
        }
        return strings;
    }

    /** The elements of a list whose elements each begin with their {@code size}, stepped at the first one's. */
    private static List<NativeRegion> elements(NativeRegion struct, long offset, Layout element) {
        int count = count(struct, offset);
        List<NativeRegion> elements = new ArrayList<>(count);
        if (count == 0) {
            return elements;
        }
        long first = struct.getAddress(offset + LIST_ITEMS);
        long stride = struct.at(first, Layout.INT.byteSize()).getInt(ELEMENT_SIZE);
        if (stride < element.byteSize()) {
            throw new IllegalArgumentException(element.name().orElse("an element") + " of " + stride
                    + " bytes is smaller than its first shape");
        }
        for (int index = 0; index < count; index++) {
            elements.add(struct.at(first + index * stride, element.byteSize()));
        }
        return elements;
    }

    private static List<NamedNumber> numbers(NativeRegion struct, long offset) {
        Layout layout = CoreLayouts.NUMBER;
        List<NamedNumber> numbers = new ArrayList<>();
        for (NativeRegion element : elements(struct, offset, layout)) {
            numbers.add(new NamedNumber(element.textAt(element.getAddress(layout.offsetOf("name"))),
                    element.getDouble(layout.offsetOf("value"))));
        }
        return numbers;
    }

    private static List<Manifest.ServiceOffer> offers(NativeRegion struct, long offset) {
        Layout layout = CoreLayouts.SERVICE_OFFER;
        List<Manifest.ServiceOffer> offers = new ArrayList<>();
        for (NativeRegion element : elements(struct, offset, layout)) {
            offers.add(new Manifest.ServiceOffer(element.textAt(element.getAddress(layout.offsetOf("id"))),
                    numbers(element, layout.offsetOf("attributes"))));
        }
        return offers;
    }

    private static List<Manifest.ServiceWant> wants(NativeRegion struct, long offset) {
        Layout layout = CoreLayouts.SERVICE_WANT;
        List<Manifest.ServiceWant> wants = new ArrayList<>();
        for (NativeRegion element : elements(struct, offset, layout)) {
            wants.add(new Manifest.ServiceWant(element.textAt(element.getAddress(layout.offsetOf("id"))),
                    optionalText(element, element.getAddress(layout.offsetOf("provider"))),
                    Choice.of(element.getInt(layout.offsetOf("optional"))),
                    Choice.of(element.getInt(layout.offsetOf("degradable"))),
                    numbers(element, layout.offsetOf("constraints"))));
        }
        return wants;
    }
}
