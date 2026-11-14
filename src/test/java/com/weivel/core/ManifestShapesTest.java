package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A manifest another library laid out is read up to the size it declares, as core.h reads one: a field past an older
 * producer's size is absent and takes its default, a field past this layer's shape from a newer producer is ignored,
 * and a growing list is stepped at its first element's size.
 */
class ManifestShapesTest {
    private static final byte NEWER_FIELD = (byte) 0xFF;
    private static final long NEWER_BYTES = 16;

    private final NativeAccess access = NativeBackends.current();
    private final NativeArena arena = new NativeArena(access);
    private final ForeignMemory memory = new ForeignMemory(new NativeArena(access));

    private static final Manifest FULL = new Manifest("shaped", 3, Optional.of("entry.jar"),
            Collections.singletonList("app"), Collections.singletonList("screens"), Collections.<String>emptyList(),
            Arrays.asList(new Manifest.ServiceOffer("shaped:one",
                            Collections.singletonList(new NamedNumber("latency", 2.5))),
                    new Manifest.ServiceOffer("shaped:two", Collections.<NamedNumber>emptyList())),
            Arrays.asList(new Manifest.ServiceWant("clock", Optional.of("local:clock"), Choice.YES, Choice.NO,
                            Collections.singletonList(new NamedNumber("latency", 10))),
                    new Manifest.ServiceWant("store", Optional.<String>empty(), Choice.NO, Choice.YES,
                            Collections.<NamedNumber>emptyList())),
            Collections.singletonList("network"), Optional.of("local:shaped"));

    @AfterEach
    void free() {
        memory.close();
        arena.close();
    }

    @Test
    void aNewerProducersManifestAndElementsAreReadUpToWhatThisLayerKnows() {
        NativeRegion laidOut = Marshal.manifest(arena, FULL);
        restride(laidOut, offset("services", "provides"), CoreLayouts.SERVICE_OFFER,
                CoreLayouts.SERVICE_OFFER.byteSize() + NEWER_BYTES);
        restride(laidOut, offset("services", "consumes"), CoreLayouts.SERVICE_WANT,
                CoreLayouts.SERVICE_WANT.byteSize() + NEWER_BYTES);
        NativeRegion newer = resized(laidOut, CoreLayouts.MANIFEST.byteSize() + NEWER_BYTES);

        assertEquals(FULL, memory.manifest(newer.address()));
    }

    @Test
    void anOlderProducersManifestReadsTheFieldsPastItsSizeAsAbsent() {
        NativeRegion laidOut = Marshal.manifest(arena, FULL);
        restride(laidOut, offset("services", "provides"), CoreLayouts.SERVICE_OFFER,
                CoreLayouts.SERVICE_OFFER.offsetOf("attributes"));
        restride(laidOut, offset("services", "consumes"), CoreLayouts.SERVICE_WANT,
                CoreLayouts.SERVICE_WANT.offsetOf("constraints"));
        laidOut.putInt(0, (int) offset("permissions"));

        Manifest read = memory.manifest(laidOut.address());

        assertEquals(Collections.<String>emptyList(), read.permissions());
        assertEquals(Optional.<String>empty(), read.identity());
        assertEquals(Arrays.asList(new Manifest.ServiceOffer("shaped:one", Collections.<NamedNumber>emptyList()),
                new Manifest.ServiceOffer("shaped:two", Collections.<NamedNumber>emptyList())),
                read.servicesProvided());
        assertEquals(Arrays.asList(new Manifest.ServiceWant("clock", Optional.of("local:clock"), Choice.YES,
                        Choice.NO, Collections.<NamedNumber>emptyList()),
                new Manifest.ServiceWant("store", Optional.<String>empty(), Choice.NO, Choice.YES,
                        Collections.<NamedNumber>emptyList())), read.servicesConsumed());
        assertEquals(FULL.targets(), read.targets());
    }

    @Test
    void aManifestOrElementBelowItsFirstShapeIsRefusedNamingIt() {
        NativeRegion manifest = Marshal.manifest(arena, FULL);
        manifest.putInt(0, (int) offset("services"));

        IllegalArgumentException small = assertThrows(IllegalArgumentException.class,
                () -> memory.manifest(manifest.address()));
        assertTrue(small.getMessage().contains("weivel_manifest"), small.getMessage());

        NativeRegion elements = Marshal.manifest(arena, FULL);
        restride(elements, offset("services", "provides"), CoreLayouts.SERVICE_OFFER, 8);
        IllegalArgumentException element = assertThrows(IllegalArgumentException.class,
                () -> memory.manifest(elements.address()));
        assertTrue(element.getMessage().contains("weivel_service_offer"), element.getMessage());
    }

    private static long offset(String... names) {
        return CoreLayouts.MANIFEST.offsetOf(names);
    }

    /**
     * Copies a list's elements into slots of {@code stride} bytes, each declaring that size: the known bytes kept up
     * to the stride, any beyond them filled as a newer field would be.
     */
    private void restride(NativeRegion manifest, long list, Layout element, long stride) {
        long items = manifest.getAddress(list);
        int count = manifest.getInt(list + CoreLayouts.STRINGS.offsetOf("count"));
        NativeRegion slots = arena.allocate(stride * count);
        for (int index = 0; index < count; index++) {
            long slot = slots.address() + index * stride;
            long kept = Math.min(stride, element.byteSize());
            access.writeBytes(slot, 0, access.readBytes(items + index * element.byteSize(), 0, (int) kept));
            for (long newer = kept; newer < stride; newer++) {
                access.writeBytes(slot, newer, new byte[] {NEWER_FIELD});
            }
            access.putInt(slot, 0, (int) stride);
        }
        manifest.putAddress(list, slots.address());
    }

    private NativeRegion resized(NativeRegion manifest, long size) {
        NativeRegion copy = arena.allocate(size);
        access.writeBytes(copy.address(), 0, access.readBytes(manifest.address(), 0,
                (int) CoreLayouts.MANIFEST.byteSize()));
        for (long newer = CoreLayouts.MANIFEST.byteSize(); newer < size; newer++) {
            access.writeBytes(copy.address(), newer, new byte[] {NEWER_FIELD});
        }
        copy.putInt(0, (int) size);
        return copy;
    }
}
