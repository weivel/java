package com.weivel.host;

import static com.weivel.host.HostFixture.consuming;
import static com.weivel.host.HostFixture.value;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.context.PluginEvent;
import com.weivel.core.Answer;
import com.weivel.core.BoundDefinition;
import com.weivel.core.BoundOperation;
import com.weivel.core.CoreConstants;
import com.weivel.core.DescribedAnswer;
import com.weivel.core.DescribedFixture;
import com.weivel.core.DescribedHandle;
import com.weivel.core.DescribedKind;
import com.weivel.core.Description;
import com.weivel.core.ForeignMemory;
import com.weivel.core.Refusal;
import com.weivel.core.RefusalStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Described definitions through the host, against the real core, the standard's check and its described library,
 * beyond what the kit's {@code description} vectors witness: what a plugin is handed, the Java values each kind
 * arrives as, every refusal made before a call, and the life of a handle. Declarations are the standard's own files.
 */
class DescribedLibrariesTest {
    private static final String DECLARATION = "library.json";

    @Test
    void aPluginIsHandedTheBoundDefinitionStandingForTheProvidersOwnStruct() throws Exception {
        try (HostFixture fixture = checking()) {
            LoadedLibrary library = value(fixture.host.loadLibrary(DescribedFixture.binary(), declaration()));
            TestEntry consumer = activated(fixture, mirrorId(), walkerId());

            Object served = value(consumer.context.get(mirrorId()));
            BoundDefinition mirror = assertInstanceOf(BoundDefinition.class, served);
            assertSame(library.definitions().get(mirrorId()), mirror);
            assertEquals(mirror.address(), ForeignMemory.addressOf(mirror.definition()));
            assertEquals(Arrays.asList("reflect", "tree", "find", "measure", "negate", "halve", "invert",
                    "swap_shade", "grow", "weigh", "count_releases", "outline"), mirror.operationNames());
            assertEquals(Collections.singletonList("bytes"), mirror.operation("measure").inputNames());
            assertEquals("the first letter", answered(mirror.call("find", inputs("key", "alpha"))));
        }
    }

    @Test
    void aHostGivenNoDescriptionCheckRefusesADescribedLibraryAsTheCallersMistake() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            Answer<LoadedLibrary> loaded = fixture.host.loadLibrary(DescribedFixture.binary(), declaration());

            assertInstanceOf(Answer.Mistake.class, loaded);
            assertTrue(((Answer.Mistake<?>) loaded).detail().contains("no description check"), loaded.toString());
        }
    }

    @Test
    void aDescriptionOfAnotherDefinitionOfTheSameStructFailsTheLoadNamingBothSymbols() throws Exception {
        LibraryDeclaration swapped = DescribedFixture.declaration(DECLARATION, json -> json.replaceFirst(
                "\"described_mirror_description\"", "\"described_mirror_without_halve_description\""));
        try (HostFixture fixture = checking()) {
            Answer<LoadedLibrary> loaded = fixture.host.loadLibrary(DescribedFixture.binary(), swapped);

            String detail = assertInstanceOf(Answer.Mistake.class, loaded).detail();
            assertTrue(detail.contains("described_mirror_without_halve_description"), detail);
            assertTrue(detail.contains("described_mirror_definition"), detail);
            assertTrue(detail.contains("at answered_by"), detail);
        }
    }

    @Test
    void aCheckRunningOutOfMemoryFailsTheLoadAsOutOfMemoryNeverAsTheCallersMistake() throws Exception {
        try (HostFixture fixture = new HostFixture(
                (description, definition) -> new Answer.Refused<>(RefusalStatus.OUT_OF_MEMORY,
                        Optional.<Refusal>empty()))) {
            Answer<LoadedLibrary> loaded = fixture.host.loadLibrary(DescribedFixture.binary(), declaration());

            Answer.Refused<?> refused = assertInstanceOf(Answer.Refused.class, loaded);
            assertEquals(RefusalStatus.OUT_OF_MEMORY, refused.status());
            assertFalse(fixture.host.loadLibrary(DescribedFixture.binary(), declaration()) instanceof Answer.Mistake);
        }
    }

    @Test
    void sixtyFourBitIntegersCrossWholeBeyondTwoToTheFiftyThree() throws Exception {
        try (HostFixture fixture = checking()) {
            BoundDefinition mirror = loaded(fixture).get(mirrorId());

            assertEquals((1L << 53) + 2, answered(mirror.call("grow", inputs("amount", (1L << 53) + 1))));
            assertEquals(Long.MAX_VALUE, answered(mirror.call("negate", inputs("number", Long.MIN_VALUE + 1))));
            assertEquals(-1L, answered(mirror.call("grow", inputs("amount", -2L))),
                    "a size of 2^64 - 2 is handed as the long -2 and grows to 2^64 - 1, the long -1");
            assertEquals(4294967295L, answered(mirror.call("invert", inputs("flags", 0))));
        }
    }

    @Test
    void aValueItsKindDoesNotHoldIsRefusedBeforeAnythingIsCalled() throws Exception {
        try (HostFixture fixture = checking()) {
            BoundDefinition mirror = loaded(fixture).get(mirrorId());
            List<Long> before = releaseCounts(mirror, null);

            assertRefused(() -> mirror.call("invert", inputs("flags", 1L << 32)), "lies outside");
            assertRefused(() -> mirror.call("invert", inputs("flags", -1)), "lies outside");
            assertRefused(() -> mirror.call("invert", inputs("flags", "seven")), "takes an integer");
            assertRefused(() -> mirror.call("halve", inputs("number", (1L << 53) + 1)), "only rounded");
            assertRefused(() -> mirror.call("find", inputs("colour", "red")), "has no colour a caller writes");
            assertRefused(() -> mirror.call("measure", inputs("length", 3L)), "has no length a caller writes");
            assertRefused(() -> mirror.call("find", inputs("key", "al\0pha")), "NUL");
            assertRefused(() -> mirror.call("weigh", inputs("value", Double.NaN)), "finite");
            assertRefused(() -> mirror.call("reflect", inputs("sample", inputs("size", 8L))),
                    "has no size a caller writes");
            assertEquals(Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L), releaseCounts(mirror, before),
                    "a refused input reached no provider, so nothing it answers was released");
        }
    }

    @Test
    void aCountIsSetFromWhatItCountsAndNullBytesAreNone() throws Exception {
        try (HostFixture fixture = checking()) {
            BoundDefinition mirror = loaded(fixture).get(mirrorId());

            assertEquals(258L, answered(mirror.call("measure", inputs("bytes", new byte[] {1, 2, (byte) 255}))));
            assertEquals(0L, answered(mirror.operation("measure").call()));
        }
    }

    @Test
    void aStructAnswersAsAnUnmodifiableMapInItsMembersOrder() throws Exception {
        try (HostFixture fixture = checking()) {
            BoundDefinition mirror = loaded(fixture).get(mirrorId());
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("label", "here");
            sample.put("tags", Arrays.asList("a", null));
            sample.put("bytes", new byte[] {0, (byte) 255});

            Map<?, ?> reflection = (Map<?, ?>) answered(mirror.call("reflect", inputs("sample", sample)));
            Map<?, ?> reflected = (Map<?, ?>) reflection.get("sample");

            assertEquals("here", reflected.get("label"));
            assertEquals(Arrays.asList("a", null), reflected.get("tags"));
            assertArrayEquals(new byte[] {0, (byte) 255}, (byte[]) reflected.get("bytes"));
            assertFalse(reflected.containsKey("best"), "a NULL pointer is left out");
            assertTrue(reflected.containsKey("origin"), "a struct held by value is always answered");
            assertEquals(new ArrayList<>(reflected.keySet()), keysInOrderTheyAreDescribed(mirror, reflected));
            assertThrows(UnsupportedOperationException.class, () -> ((Map<Object, Object>) reflected).put("x", 1));
        }
    }

    @Test
    void aValueTreeAnswersAsTheNaturalJavaValues() throws Exception {
        try (HostFixture fixture = checking()) {
            BoundDefinition mirror = loaded(fixture).get(mirrorId());
            Map<String, Object> held = new LinkedHashMap<>();
            held.put("list", Arrays.<Object>asList(1L, "two", true, null));
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("ratio", 0.5);
            sample.put("held", held);

            Map<?, ?> tree = (Map<?, ?>) answered(mirror.call("tree", inputs("sample", sample)));

            assertEquals(0.5, tree.get("ratio"));
            assertEquals(Collections.singletonMap("list", Arrays.<Object>asList(1.0, "two", true, null)),
                    tree.get("held"));
            assertEquals(null, tree.get("label"));
            assertTrue(tree.containsKey("label"), "a record's null is a key holding null, not a key left out");
        }
    }

    @Test
    void aNoneAndARefusalAreToldApartFromAnAnswer() throws Exception {
        try (HostFixture fixture = checking()) {
            BoundDefinition mirror = loaded(fixture).get(mirrorId());

            assertInstanceOf(DescribedAnswer.None.class, mirror.call("find", inputs("key", "beta")));
            DescribedAnswer.Refused refused = assertInstanceOf(DescribedAnswer.Refused.class,
                    mirror.call("find", inputs("key", "refuse")));
            assertTrue(refused.detail().isPresent() && refused.fix().isPresent(), refused.toString());
            DescribedAnswer.Failed failed = assertInstanceOf(DescribedAnswer.Failed.class, mirror.call("find",
                    Collections.<String, Object>emptyMap()));
            assertEquals(CoreConstants.STATUS_MALFORMED, failed.status());
        }
    }

    @Test
    void aHandleIsReleasedOnceByItsHolderAndRefusedAsAnInputAfterwards() throws Exception {
        try (HostFixture fixture = checking()) {
            Map<String, BoundDefinition> bound = loaded(fixture);
            BoundDefinition walker = bound.get(walkerId());
            List<Long> before = releaseCounts(bound.get(mirrorId()), null);

            DescribedHandle cursor = (DescribedHandle) answered(walker.call("open", openWalk(5L)));
            assertEquals(5L, answered(walker.call("use", inputs("cursor", cursor))));
            cursor.release();
            cursor.release();

            assertTrue(cursor.isReleased());
            assertEquals(Arrays.asList(0L, 0L, 0L, 1L, 0L, 0L), releaseCounts(bound.get(mirrorId()), before));
            assertRefused(() -> walker.call("use", inputs("cursor", cursor)), "released already");
            assertRefused(() -> walker.call("use", inputs("cursor", "a cursor")), "a handle of");
        }
    }

    @Test
    void aHandleStillHeldIsReleasedAtTheUnloadAndNotAgainAfterwards() throws Exception {
        try (HostFixture fixture = checking()) {
            LoadedLibrary library = value(fixture.host.loadLibrary(DescribedFixture.binary(), declaration()));
            TestEntry consumer = activated(fixture, mirrorId(), walkerId());
            BoundDefinition mirror = (BoundDefinition) value(consumer.context.get(mirrorId()));
            BoundDefinition walker = (BoundDefinition) value(consumer.context.get(walkerId()));
            List<Long> before = releaseCounts(mirror, null);
            DescribedHandle cursor = (DescribedHandle) answered(walker.call("open", openWalk(1L)));

            value(fixture.host.unloadLibrary(library));
            boolean releasedByTheUnload = cursor.isReleased();
            List<Long> afterTheUnload = releaseCounts(mirror, before);
            cursor.release();

            assertTrue(releasedByTheUnload);
            assertEquals(Arrays.asList(0L, 0L, 0L, 1L, 0L, 0L), afterTheUnload);
            assertEquals(afterTheUnload, releaseCounts(mirror, before));
        }
    }

    @Test
    void theOperationsADescriptionMarksBlockingAreReportedAndCalledOnTheCallingThread() throws Exception {
        try (HostFixture fixture = checking()) {
            Map<String, BoundDefinition> bound = loaded(fixture);
            BoundDefinition walker = bound.get(walkerId());

            Object waited = answered(walker.call("wait", inputs("ticks", 7L)));

            assertEquals(Collections.singletonList("wait"), walker.blockingOperations());
            assertEquals(Collections.<String>emptyList(), bound.get(mirrorId()).blockingOperations());
            assertEquals(7L, waited);
        }
    }

    /**
     * The unload ends every answer carrying a definition that its holder has not released: a call through the
     * definition is the caller's mistake at once, though the walker it was got through is still held and callable.
     */
    @Test
    void aDefinitionAnAnswerCarriedIsRefusedOnceItsLibraryIsUnloaded() throws Exception {
        try (HostFixture fixture = checking()) {
            LoadedLibrary library = value(fixture.host.loadLibrary(DescribedFixture.binary(), declaration()));
            TestEntry consumer = activated(fixture, mirrorId(), walkerId());
            BoundDefinition walker = (BoundDefinition) value(consumer.context.get(walkerId()));
            DescribedAnswer.Answered loan = assertInstanceOf(DescribedAnswer.Answered.class, walker.call("lend",
                    Collections.<String, Object>emptyMap()));
            BoundDefinition counter = (BoundDefinition) ((Map<?, ?>) loan.value()).get("counter");
            Object beforeTheUnload = answered(counter.call("add", inputs("amount", 5L)));

            value(fixture.host.unloadLibrary(library));

            assertEquals(1005L, beforeTheUnload);
            assertRefused(() -> counter.call("add", inputs("amount", 5L)), "carried by an answer that has ended");
            assertEquals(7L, answered(walker.call("wait", inputs("ticks", 7L))));
            loan.release();
        }
    }

    @Test
    void aCallOnceTheBinaryClosedAnswersUnloadedAndCallsNothing() throws Exception {
        try (HostFixture fixture = checking()) {
            LoadedLibrary library = value(fixture.host.loadLibrary(DescribedFixture.binary(), declaration()));
            BoundDefinition mirror = (BoundDefinition) library.definitions().get(mirrorId());

            value(fixture.host.unloadLibrary(library));

            assertInstanceOf(DescribedAnswer.Unloaded.class, mirror.call("find", inputs("key", "alpha")));
        }
    }

    @Test
    void anOperationTheProviderOffersIsAnsweredByItsOwnObjectWithNoLookup() throws Exception {
        try (HostFixture fixture = checking()) {
            BoundDefinition mirror = loaded(fixture).get(mirrorId());
            BoundOperation halve = mirror.operation("halve");

            assertTrue(halve.isOffered());
            assertEquals(-1.5, answered(halve.call(inputs("number", -3))));
            assertThrows(IllegalArgumentException.class, () -> mirror.operation("double"));
        }
    }

    @Test
    void aListOrRecordIsWrittenAsOneSnapshotHoweverItsSizeDisagreesWithWhatItHolds() throws Exception {
        try (HostFixture fixture = checking()) {
            BoundDefinition mirror = loaded(fixture).get(mirrorId());
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("tags", new UnderCountedList<>(Arrays.asList("a", "b", "c")));
            Map<String, Object> record = new UnderCountedMap<>();
            record.put("x", 1L);
            record.put("y", 2L);
            record.put("z", 3L);

            Map<?, ?> reflection = (Map<?, ?>) answered(mirror.call("reflect", inputs("sample", sample)));

            assertEquals(Arrays.asList("a", "b", "c"), ((Map<?, ?>) reflection.get("sample")).get("tags"));
            assertEquals(4L, answered(mirror.call("weigh", inputs("value",
                    new UnderCountedList<>(Arrays.<Object>asList(1L, 2L, 3L))))));
            assertEquals(4L, answered(mirror.call("weigh", inputs("value", record))));
        }
    }

    @Test
    void aHandleAnsweredAfterTheUnloadIsReleasedWhenTheBinaryCloses() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses, DescribedFixture.standard().descriptionCheck())) {
            LoadedLibrary library = value(fixture.host.loadLibrary(DescribedFixture.binary(), declaration()));
            TestEntry consumer = activated(fixture, walkerId());
            List<PluginEvent.Revoked> revoked = DefinitionsTest.revocationsHeardBy(consumer);
            BoundDefinition walker = (BoundDefinition) value(consumer.context.get(walkerId()));
            value(fixture.host.unloadLibrary(library));

            DescribedHandle late = (DescribedHandle) answered(walker.call("open", openWalk(2L)));
            boolean heldUntilTheClose = !late.isReleased();
            assertEquals(true, value(consumer.context.releaseHolding(DefinitionsTest.awaiting(revoked))));

            assertTrue(heldUntilTheClose);
            assertEquals(Collections.singletonList(declaration().importName()), pauses.closed());
            assertTrue(late.isReleased(), "the close releases a handle answered after the unload's");
        }
    }

    private static HostFixture checking() {
        return new HostFixture(DescribedFixture.standard().descriptionCheck());
    }

    private static LibraryDeclaration declaration() {
        return DescribedFixture.declaration(DECLARATION);
    }

    private static String mirrorId() {
        return declaration().services().get(0).id();
    }

    private static String walkerId() {
        return declaration().services().get(1).id();
    }

    /** Loads the library, answering each service's bound definition by its id. */
    private static Map<String, BoundDefinition> loaded(HostFixture fixture) {
        LoadedLibrary library = value(fixture.host.loadLibrary(DescribedFixture.binary(), declaration()));
        Map<String, BoundDefinition> bound = new HashMap<>();
        for (Map.Entry<String, Object> definition : library.definitions().entrySet()) {
            bound.put(definition.getKey(), (BoundDefinition) definition.getValue());
        }
        return bound;
    }

    private static TestEntry activated(HostFixture fixture, String... consumes) {
        TestEntry entry = fixture.entry("consumer");
        fixture.activate(fixture.register(consuming("consumer", consumes), entry));
        return entry;
    }

    private static Map<String, Object> inputs(String name, Object value) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(name, value);
        return inputs;
    }

    private static Map<String, Object> openWalk(long start) {
        Map<String, Object> inputs = inputs("label", "walk");
        inputs.put("start", start);
        return inputs;
    }

    private static Object answered(DescribedAnswer answer) {
        DescribedAnswer.Answered answered = assertInstanceOf(DescribedAnswer.Answered.class, answer);
        assertTrue(answered.hasValue(), answer.toString());
        return answered.value();
    }

    /** The release counts the mirror reads now, in the order it answers them, minus {@code before} unless null. */
    private static List<Long> releaseCounts(BoundDefinition mirror, List<Long> before) {
        Map<?, ?> counts = (Map<?, ?>) answered(mirror.call("count_releases", Collections.<String, Object>emptyMap()));
        List<Long> read = new ArrayList<>();
        int index = 0;
        for (Object count : counts.values()) {
            read.add((Long) count - (before == null ? 0 : before.get(index++)));
        }
        return read;
    }

    /** The answered struct's keys in the order its type lists its members. */
    private static List<Object> keysInOrderTheyAreDescribed(BoundDefinition mirror, Map<?, ?> answered) {
        for (Description.Type type : mirror.description().types()) {
            List<Object> keys = new ArrayList<>();
            for (Description.Member member : type.members()) {
                if (answered.containsKey(member.name())) {
                    keys.add(member.name());
                }
            }
            if (keys.size() == answered.size() && type.members().stream()
                    .anyMatch(member -> member.kind() == DescribedKind.VALUE)) {
                return keys;
            }
        }
        throw new AssertionError("no described struct holds every key of " + answered.keySet());
    }

    private static void assertRefused(Runnable call, String naming) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, call::run);
        assertTrue(refused.getMessage().contains(naming), refused.getMessage());
    }

    /** A list whose size says it holds one element fewer than it does, as one another thread grows does. */
    private static final class UnderCountedList<T> extends ArrayList<T> {
        private static final long serialVersionUID = 1L;

        UnderCountedList(List<T> items) {
            super(items);
        }

        @Override
        public int size() {
            return super.size() - 1;
        }
    }

    /** A map whose size says it holds one entry fewer than it does. */
    private static final class UnderCountedMap<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;

        @Override
        public int size() {
            return super.size() - 1;
        }
    }
}
