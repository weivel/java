package com.weivel.core;

import static com.weivel.core.CoreLayouts.offsetOf;

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

    static NativeRegion coreOptions(NativeArena arena, CoreOptions options) {
        Layout layout = CoreLayouts.CORE_OPTIONS;
        NativeRegion struct = sized(arena, layout);
        struct.putInt(offsetOf(layout, "initial_event_capacity"), options.initialEventCapacity());
        struct.putInt(offsetOf(layout, "minimum_log_level"), options.minimumLogLevel());
        struct.putInt(offsetOf(layout, "api"), options.api());
        writeStrings(arena, struct, offsetOf(layout, "applications"), options.applications());
        writeStrings(arena, struct, offsetOf(layout, "well_known_services"), options.wellKnownServices());
        writeVocabulary(arena, struct, offsetOf(layout, "constraint_vocabulary"), options.constraintVocabulary());
        return struct;
    }

    static NativeRegion manifest(NativeArena arena, Manifest manifest) {
        Layout layout = CoreLayouts.MANIFEST;
        NativeRegion struct = sized(arena, layout);
        struct.putAddress(offsetOf(layout, "import_name"), arena.text(manifest.importName()).address());
        struct.putInt(offsetOf(layout, "api"), manifest.api());
        struct.putAddress(offsetOf(layout, "entry"), text(arena, manifest.entry()));
        writeStrings(arena, struct, offsetOf(layout, "targets"), manifest.targets());
        writeStrings(arena, struct, offsetOf(layout, "capabilities", "provides"), manifest.capabilitiesProvided());
        writeStrings(arena, struct, offsetOf(layout, "capabilities", "consumes"), manifest.capabilitiesConsumed());
        writeOffers(arena, struct, offsetOf(layout, "services", "provides"), manifest.servicesProvided());
        writeWants(arena, struct, offsetOf(layout, "services", "consumes"), manifest.servicesConsumed());
        writeStrings(arena, struct, offsetOf(layout, "permissions"), manifest.permissions());
        struct.putAddress(offsetOf(layout, "identity"), text(arena, manifest.identity()));
        return struct;
    }

    static NativeRegion wantOptions(NativeArena arena, WantOptions options) {
        Layout layout = CoreLayouts.WANT_OPTIONS;
        NativeRegion struct = sized(arena, layout);
        struct.putInt(offsetOf(layout, "timed"), options.timed().value());
        struct.putLong(offsetOf(layout, "now"), options.now());
        struct.putLong(offsetOf(layout, "timeout_millis"), options.timeoutMillis());
        struct.putInt(offsetOf(layout, "budgeted"), options.budgeted().value());
        struct.putLong(offsetOf(layout, "budget_millis"), options.budgetMillis());
        struct.putLong(offsetOf(layout, "poll_millis"), options.pollMillis());
        return struct;
    }

    /** The reason's struct, as its address; 0, the core's {@code NULL}, for none. */
    static long quarantineReason(NativeArena arena, Optional<QuarantineReason> reason) {
        if (!reason.isPresent()) {
            return 0;
        }
        Layout layout = CoreLayouts.QUARANTINE_REASON;
        NativeRegion struct = sized(arena, layout);
        struct.putInt(offsetOf(layout, "code"), reason.get().code());
        struct.putAddress(offsetOf(layout, "detail"), arena.text(reason.get().detail()).address());
        struct.putAddress(offsetOf(layout, "fix"), arena.text(reason.get().fix()).address());
        return struct.address();
    }

    static NativeRegion capacity(NativeArena arena, CapacityAnswer answer) {
        Layout layout = CoreLayouts.CAPACITY;
        NativeRegion struct = sized(arena, layout);
        struct.putLong(offsetOf(layout, "request"), answer.request());
        writeNumbers(arena, struct, offsetOf(layout, "attributes"), answer.attributes());
        struct.putInt(offsetOf(layout, "serves"), answer.serves().value());
        return struct;
    }

    /** An empty answer struct of {@code layout}, its {@code size} set, for the core to fill. */
    static NativeRegion emptyAnswer(NativeArena arena, Layout layout) {
        return sized(arena, layout);
    }

    static NativeRegion pluginOptions(NativeArena arena, long payload) {
        Layout layout = CoreLayouts.PLUGIN_OPTIONS;
        NativeRegion struct = sized(arena, layout);
        struct.putLong(offsetOf(layout, "payload"), payload);
        return struct;
    }

    private static NativeRegion sized(NativeArena arena, Layout layout) {
        NativeRegion struct = arena.allocate(layout);
        struct.putInt(offsetOf(layout, "size"), (int) layout.byteSize());
        return struct;
    }

    private static long text(NativeArena arena, Optional<String> text) {
        return text.isPresent() ? arena.text(text.get()).address() : 0;
    }

    private static void writeList(NativeRegion struct, long offset, NativeRegion items, int count) {
        struct.putAddress(offset + LIST_ITEMS, count == 0 ? 0 : items.address());
        struct.putInt(offset + LIST_COUNT, count);
    }

    private static void writeStrings(NativeArena arena, NativeRegion struct, long offset, List<String> strings) {
        NativeRegion items = arena.allocate(Layout.ADDRESS, strings.size());
        for (int index = 0; index < strings.size(); index++) {
            items.putAddress(index * Layout.ADDRESS.byteSize(), arena.text(strings.get(index)).address());
        }
        writeList(struct, offset, items, strings.size());
    }

    private static void writeNumbers(NativeArena arena, NativeRegion struct, long offset, List<NamedNumber> numbers) {
        Layout layout = CoreLayouts.NUMBER;
        NativeRegion items = arena.allocate(layout, numbers.size());
        for (int index = 0; index < numbers.size(); index++) {
            NativeRegion element = items.slice(index * layout.byteSize(), layout.byteSize());
            element.putInt(offsetOf(layout, "size"), (int) layout.byteSize());
            element.putAddress(offsetOf(layout, "name"), arena.text(numbers.get(index).name()).address());
            element.putDouble(offsetOf(layout, "value"), numbers.get(index).value());
        }
        writeList(struct, offset, items, numbers.size());
    }

    private static void writeVocabulary(NativeArena arena, NativeRegion struct, long offset,
                                        List<CoreOptions.ConstraintEntry> entries) {
        Layout layout = CoreLayouts.CONSTRAINT_ENTRY;
        NativeRegion items = arena.allocate(layout, entries.size());
        for (int index = 0; index < entries.size(); index++) {
            NativeRegion element = items.slice(index * layout.byteSize(), layout.byteSize());
            CoreOptions.ConstraintEntry entry = entries.get(index);
            element.putInt(offsetOf(layout, "size"), (int) layout.byteSize());
            element.putAddress(offsetOf(layout, "constraint_name"), arena.text(entry.constraintName()).address());
            element.putAddress(offsetOf(layout, "attribute_name"), arena.text(entry.attributeName()).address());
            element.putInt(offsetOf(layout, "bound"), entry.bound());
        }
        writeList(struct, offset, items, entries.size());
    }

    private static void writeOffers(NativeArena arena, NativeRegion struct, long offset,
                                    List<Manifest.ServiceOffer> offers) {
        Layout layout = CoreLayouts.SERVICE_OFFER;
        NativeRegion items = arena.allocate(layout, offers.size());
        for (int index = 0; index < offers.size(); index++) {
            NativeRegion element = items.slice(index * layout.byteSize(), layout.byteSize());
            element.putInt(offsetOf(layout, "size"), (int) layout.byteSize());
            element.putAddress(offsetOf(layout, "id"), arena.text(offers.get(index).id()).address());
            writeNumbers(arena, element, offsetOf(layout, "attributes"), offers.get(index).attributes());
        }
        writeList(struct, offset, items, offers.size());
    }

    private static void writeWants(NativeArena arena, NativeRegion struct, long offset,
                                   List<Manifest.ServiceWant> wants) {
        Layout layout = CoreLayouts.SERVICE_WANT;
        NativeRegion items = arena.allocate(layout, wants.size());
        for (int index = 0; index < wants.size(); index++) {
            NativeRegion element = items.slice(index * layout.byteSize(), layout.byteSize());
            Manifest.ServiceWant want = wants.get(index);
            element.putInt(offsetOf(layout, "size"), (int) layout.byteSize());
            element.putAddress(offsetOf(layout, "id"), arena.text(want.id()).address());
            element.putAddress(offsetOf(layout, "provider"), text(arena, want.provider()));
            element.putInt(offsetOf(layout, "optional"), want.optional().value());
            element.putInt(offsetOf(layout, "degradable"), want.degradable().value());
            writeNumbers(arena, element, offsetOf(layout, "constraints"), want.constraints());
        }
        writeList(struct, offset, items, wants.size());
    }
}
