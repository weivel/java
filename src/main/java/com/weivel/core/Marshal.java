package com.weivel.core;

import static com.weivel.core.CoreLayouts.offsetOf;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.util.List;
import java.util.Optional;

/**
 * Lays Java values out as the core's structs, in memory of a call's own arena, each struct's {@code size} the
 * {@code byteSize} of its layout.
 */
final class Marshal {
    private static final long LIST_ITEMS = offsetOf(CoreLayouts.STRINGS, "items");
    private static final long LIST_COUNT = offsetOf(CoreLayouts.STRINGS, "count");

    private Marshal() {
    }

    static MemorySegment coreOptions(Arena arena, CoreOptions options) {
        StructLayout layout = CoreLayouts.CORE_OPTIONS;
        MemorySegment struct = sized(arena, layout);
        struct.set(JAVA_INT, offsetOf(layout, "initial_event_capacity"), options.initialEventCapacity());
        struct.set(JAVA_INT, offsetOf(layout, "minimum_log_level"), options.minimumLogLevel());
        struct.set(JAVA_INT, offsetOf(layout, "api"), options.api());
        writeStrings(arena, struct, offsetOf(layout, "applications"), options.applications());
        writeStrings(arena, struct, offsetOf(layout, "well_known_services"), options.wellKnownServices());
        writeVocabulary(arena, struct, offsetOf(layout, "constraint_vocabulary"), options.constraintVocabulary());
        return struct;
    }

    static MemorySegment manifest(Arena arena, Manifest manifest) {
        StructLayout layout = CoreLayouts.MANIFEST;
        MemorySegment struct = sized(arena, layout);
        struct.set(ADDRESS, offsetOf(layout, "import_name"), arena.allocateFrom(manifest.importName()));
        struct.set(JAVA_INT, offsetOf(layout, "api"), manifest.api());
        struct.set(ADDRESS, offsetOf(layout, "entry"), text(arena, manifest.entry()));
        writeStrings(arena, struct, offsetOf(layout, "targets"), manifest.targets());
        writeStrings(arena, struct, offsetOf(layout, "capabilities", "provides"), manifest.capabilitiesProvided());
        writeStrings(arena, struct, offsetOf(layout, "capabilities", "consumes"), manifest.capabilitiesConsumed());
        writeOffers(arena, struct, offsetOf(layout, "services", "provides"), manifest.servicesProvided());
        writeWants(arena, struct, offsetOf(layout, "services", "consumes"), manifest.servicesConsumed());
        writeStrings(arena, struct, offsetOf(layout, "permissions"), manifest.permissions());
        struct.set(ADDRESS, offsetOf(layout, "identity"), text(arena, manifest.identity()));
        return struct;
    }

    static MemorySegment wantOptions(Arena arena, WantOptions options) {
        StructLayout layout = CoreLayouts.WANT_OPTIONS;
        MemorySegment struct = sized(arena, layout);
        struct.set(JAVA_INT, offsetOf(layout, "timed"), options.timed().value());
        struct.set(JAVA_LONG, offsetOf(layout, "now"), options.now());
        struct.set(JAVA_LONG, offsetOf(layout, "timeout_millis"), options.timeoutMillis());
        struct.set(JAVA_INT, offsetOf(layout, "budgeted"), options.budgeted().value());
        struct.set(JAVA_LONG, offsetOf(layout, "budget_millis"), options.budgetMillis());
        struct.set(JAVA_LONG, offsetOf(layout, "poll_millis"), options.pollMillis());
        return struct;
    }

    static MemorySegment quarantineReason(Arena arena, Optional<QuarantineReason> reason) {
        if (reason.isEmpty()) {
            return MemorySegment.NULL;
        }
        StructLayout layout = CoreLayouts.QUARANTINE_REASON;
        MemorySegment struct = sized(arena, layout);
        struct.set(JAVA_INT, offsetOf(layout, "code"), reason.get().code());
        struct.set(ADDRESS, offsetOf(layout, "detail"), arena.allocateFrom(reason.get().detail()));
        struct.set(ADDRESS, offsetOf(layout, "fix"), arena.allocateFrom(reason.get().fix()));
        return struct;
    }

    static MemorySegment capacity(Arena arena, CapacityAnswer answer) {
        StructLayout layout = CoreLayouts.CAPACITY;
        MemorySegment struct = sized(arena, layout);
        struct.set(JAVA_LONG, offsetOf(layout, "request"), answer.request());
        writeNumbers(arena, struct, offsetOf(layout, "attributes"), answer.attributes());
        struct.set(JAVA_INT, offsetOf(layout, "serves"), answer.serves().value());
        return struct;
    }

    /** An empty answer struct of {@code layout}, its {@code size} set, for the core to fill. */
    static MemorySegment emptyAnswer(Arena arena, StructLayout layout) {
        return sized(arena, layout);
    }

    static MemorySegment pluginOptions(Arena arena, long payload) {
        StructLayout layout = CoreLayouts.PLUGIN_OPTIONS;
        MemorySegment struct = sized(arena, layout);
        struct.set(JAVA_LONG, offsetOf(layout, "payload"), payload);
        return struct;
    }

    private static MemorySegment sized(Arena arena, StructLayout layout) {
        MemorySegment struct = arena.allocate(layout);
        struct.set(JAVA_INT, offsetOf(layout, "size"), (int) layout.byteSize());
        return struct;
    }

    private static MemorySegment text(Arena arena, Optional<String> text) {
        return text.map(arena::allocateFrom).orElse(MemorySegment.NULL);
    }

    private static void writeList(MemorySegment struct, long offset, MemorySegment items, int count) {
        struct.set(ADDRESS, offset + LIST_ITEMS, count == 0 ? MemorySegment.NULL : items);
        struct.set(JAVA_INT, offset + LIST_COUNT, count);
    }

    private static void writeStrings(Arena arena, MemorySegment struct, long offset, List<String> strings) {
        MemorySegment items = arena.allocate(ADDRESS, Math.max(1, strings.size()));
        for (int index = 0; index < strings.size(); index++) {
            items.setAtIndex(ADDRESS, index, arena.allocateFrom(strings.get(index)));
        }
        writeList(struct, offset, items, strings.size());
    }

    private static void writeNumbers(Arena arena, MemorySegment struct, long offset, List<NamedNumber> numbers) {
        StructLayout layout = CoreLayouts.NUMBER;
        MemorySegment items = arena.allocate(layout, Math.max(1, numbers.size()));
        for (int index = 0; index < numbers.size(); index++) {
            MemorySegment element = items.asSlice(index * layout.byteSize(), layout);
            element.set(JAVA_INT, offsetOf(layout, "size"), (int) layout.byteSize());
            element.set(ADDRESS, offsetOf(layout, "name"), arena.allocateFrom(numbers.get(index).name()));
            element.set(JAVA_DOUBLE, offsetOf(layout, "value"), numbers.get(index).value());
        }
        writeList(struct, offset, items, numbers.size());
    }

    private static void writeVocabulary(Arena arena, MemorySegment struct, long offset,
                                        List<CoreOptions.ConstraintEntry> entries) {
        StructLayout layout = CoreLayouts.CONSTRAINT_ENTRY;
        MemorySegment items = arena.allocate(layout, Math.max(1, entries.size()));
        for (int index = 0; index < entries.size(); index++) {
            MemorySegment element = items.asSlice(index * layout.byteSize(), layout);
            CoreOptions.ConstraintEntry entry = entries.get(index);
            element.set(JAVA_INT, offsetOf(layout, "size"), (int) layout.byteSize());
            element.set(ADDRESS, offsetOf(layout, "constraint_name"), arena.allocateFrom(entry.constraintName()));
            element.set(ADDRESS, offsetOf(layout, "attribute_name"), arena.allocateFrom(entry.attributeName()));
            element.set(JAVA_INT, offsetOf(layout, "bound"), entry.bound());
        }
        writeList(struct, offset, items, entries.size());
    }

    private static void writeOffers(Arena arena, MemorySegment struct, long offset,
                                    List<Manifest.ServiceOffer> offers) {
        StructLayout layout = CoreLayouts.SERVICE_OFFER;
        MemorySegment items = arena.allocate(layout, Math.max(1, offers.size()));
        for (int index = 0; index < offers.size(); index++) {
            MemorySegment element = items.asSlice(index * layout.byteSize(), layout);
            element.set(JAVA_INT, offsetOf(layout, "size"), (int) layout.byteSize());
            element.set(ADDRESS, offsetOf(layout, "id"), arena.allocateFrom(offers.get(index).id()));
            writeNumbers(arena, element, offsetOf(layout, "attributes"), offers.get(index).attributes());
        }
        writeList(struct, offset, items, offers.size());
    }

    private static void writeWants(Arena arena, MemorySegment struct, long offset,
                                   List<Manifest.ServiceWant> wants) {
        StructLayout layout = CoreLayouts.SERVICE_WANT;
        MemorySegment items = arena.allocate(layout, Math.max(1, wants.size()));
        for (int index = 0; index < wants.size(); index++) {
            MemorySegment element = items.asSlice(index * layout.byteSize(), layout);
            Manifest.ServiceWant want = wants.get(index);
            element.set(JAVA_INT, offsetOf(layout, "size"), (int) layout.byteSize());
            element.set(ADDRESS, offsetOf(layout, "id"), arena.allocateFrom(want.id()));
            element.set(ADDRESS, offsetOf(layout, "provider"), text(arena, want.provider()));
            element.set(JAVA_INT, offsetOf(layout, "optional"), want.optional().value());
            element.set(JAVA_INT, offsetOf(layout, "degradable"), want.degradable().value());
            writeNumbers(arena, element, offsetOf(layout, "constraints"), want.constraints());
        }
        writeList(struct, offset, items, wants.size());
    }
}
