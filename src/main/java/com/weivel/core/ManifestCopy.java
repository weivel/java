package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Copies a {@code weivel_manifest} another C library laid out into a {@link Manifest} value, as the core reads one:
 * each struct up to the {@code size} it declares, a field past it absent and taking the default core.h states (no
 * permissions, no identity, no attributes, no constraints), and every array of growing elements stepped at the stride
 * its first element's {@code size} declares.
 */
final class ManifestCopy {
    private static final Layout MANIFEST = CoreLayouts.MANIFEST;
    private static final Layout OFFER = CoreLayouts.SERVICE_OFFER;
    private static final Layout WANT = CoreLayouts.SERVICE_WANT;
    private static final Layout NUMBER = CoreLayouts.NUMBER;
    private static final long MANIFEST_FIRST_SHAPE = MANIFEST.endOf("services");
    private static final long OFFER_FIRST_SHAPE = OFFER.endOf("id");
    private static final long WANT_FIRST_SHAPE = WANT.endOf("degradable");
    private static final long NUMBER_FIRST_SHAPE = NUMBER.byteSize();
    private static final long LIST_ITEMS = CoreLayouts.offsetOf(CoreLayouts.STRINGS, "items");
    private static final long LIST_COUNT = CoreLayouts.offsetOf(CoreLayouts.STRINGS, "count");
    private static final long ELEMENT_SIZE = 0;

    private ManifestCopy() {
    }

    /** @throws IllegalArgumentException for a struct below its first shape, naming it, or no import name */
    static Manifest read(NativeRegion struct) {
        requireFirstShape(MANIFEST, struct.byteSize(), MANIFEST_FIRST_SHAPE);
        return new Manifest(importName(struct),
                struct.getInt(MANIFEST.offsetOf("api")),
                optionalText(struct, struct.getAddress(MANIFEST.offsetOf("entry"))),
                strings(struct, MANIFEST.offsetOf("targets")),
                strings(struct, MANIFEST.offsetOf("capabilities", "provides")),
                strings(struct, MANIFEST.offsetOf("capabilities", "consumes")),
                offers(struct, MANIFEST.offsetOf("services", "provides")),
                wants(struct, MANIFEST.offsetOf("services", "consumes")),
                covers(struct, MANIFEST, "permissions") ? strings(struct, MANIFEST.offsetOf("permissions"))
                        : Collections.<String>emptyList(),
                covers(struct, MANIFEST, "identity")
                        ? optionalText(struct, struct.getAddress(MANIFEST.offsetOf("identity")))
                        : Optional.<String>empty());
    }

    private static void requireFirstShape(Layout layout, long declared, long firstShape) {
        if (declared < firstShape) {
            throw new IllegalArgumentException(layout.name().orElse("a struct") + " of " + declared
                    + " bytes is smaller than its first shape of " + firstShape);
        }
    }

    private static boolean covers(NativeRegion struct, Layout layout, String field) {
        return layout.endOf(field) <= struct.byteSize();
    }

    private static String importName(NativeRegion struct) {
        long pointer = struct.getAddress(MANIFEST.offsetOf("import_name"));
        if (pointer == 0) {
            throw new IllegalArgumentException("the manifest has no importName, which the core requires");
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

    /** The elements of a list whose elements each begin with their {@code size}, each read at the first one's. */
    private static List<NativeRegion> elements(NativeRegion struct, long offset, Layout element, long firstShape) {
        int count = count(struct, offset);
        List<NativeRegion> elements = new ArrayList<>(count);
        if (count == 0) {
            return elements;
        }
        long first = struct.getAddress(offset + LIST_ITEMS);
        long stride = Integer.toUnsignedLong(struct.at(first, Layout.INT.byteSize()).getInt(ELEMENT_SIZE));
        requireFirstShape(element, stride, firstShape);
        for (int index = 0; index < count; index++) {
            elements.add(struct.at(first + index * stride, stride));
        }
        return elements;
    }

    private static List<NamedNumber> numbers(NativeRegion struct, long offset) {
        List<NamedNumber> numbers = new ArrayList<>();
        for (NativeRegion element : elements(struct, offset, NUMBER, NUMBER_FIRST_SHAPE)) {
            numbers.add(new NamedNumber(element.textAt(element.getAddress(NUMBER.offsetOf("name"))),
                    element.getDouble(NUMBER.offsetOf("value"))));
        }
        return numbers;
    }

    private static List<Manifest.ServiceOffer> offers(NativeRegion struct, long offset) {
        List<Manifest.ServiceOffer> offers = new ArrayList<>();
        for (NativeRegion element : elements(struct, offset, OFFER, OFFER_FIRST_SHAPE)) {
            offers.add(new Manifest.ServiceOffer(element.textAt(element.getAddress(OFFER.offsetOf("id"))),
                    covers(element, OFFER, "attributes") ? numbers(element, OFFER.offsetOf("attributes"))
                            : Collections.<NamedNumber>emptyList()));
        }
        return offers;
    }

    private static List<Manifest.ServiceWant> wants(NativeRegion struct, long offset) {
        List<Manifest.ServiceWant> wants = new ArrayList<>();
        for (NativeRegion element : elements(struct, offset, WANT, WANT_FIRST_SHAPE)) {
            wants.add(new Manifest.ServiceWant(element.textAt(element.getAddress(WANT.offsetOf("id"))),
                    optionalText(element, element.getAddress(WANT.offsetOf("provider"))),
                    Choice.of(element.getInt(WANT.offsetOf("optional"))),
                    Choice.of(element.getInt(WANT.offsetOf("degradable"))),
                    covers(element, WANT, "constraints") ? numbers(element, WANT.offsetOf("constraints"))
                            : Collections.<NamedNumber>emptyList()));
        }
        return wants;
    }
}
