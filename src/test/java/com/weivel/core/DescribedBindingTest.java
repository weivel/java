package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.host.LibraryDeclaration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The binder itself, handed the described library's real definition and its real description read from the binary,
 * or a copy of either changed in one place, so each refusal at the bind and each rule of a call is proven on its own.
 * No type of the library is named: a changed member is found by its kind, a parameter by its operation.
 */
class DescribedBindingTest {
    @Test
    void theRealDescriptionBindsEveryOperation() {
        try (Opened mirror = Opened.service(0)) {
            BoundDefinition bound = mirror.bind(mirror.description);

            assertTrue(bound.operationNames().contains("reflect"), bound.toString());
            assertEquals("the first letter", answered(bound.call("find", Collections.singletonMap("key", "alpha"))));
        }
    }

    /**
     * @implNote Every bind holds each described struct's layout against its description, so a description bound on
     *     both backends is one both lay out alike; on Java 8 the two are the same backend.
     */
    @Test
    void jnaBindsEveryDescriptionTheSelectedBackendBindsAndPassesADoubleByValue() {
        int services = DescribedFixture.declaration("library.json").services().size();
        int halving = 0;
        for (int index = 0; index < services; index++) {
            try (Opened selected = Opened.service(index); Opened jna = Opened.service(index, JnaAccess.INSTANCE)) {
                BoundDefinition onJna = jna.bind(jna.description);

                assertEquals(selected.bind(selected.description).operationNames(), onJna.operationNames());
                if (onJna.operationNames().contains("halve")) {
                    halving++;
                    assertEquals(5e299, answered(onJna.call("halve", Collections.singletonMap("number", 1e300))));
                }
            }
        }
        assertTrue(halving >= 1, "no service of the described library offers halve, so no double was passed");
    }

    @Test
    void aMemberWhoseOffsetDisagreesWithThePlatformsLayoutIsRefusedNamingIt() {
        try (Opened mirror = Opened.service(0)) {
            Description moved = withMember(mirror.description, (type, member) -> member.kind() == DescribedKind.U64,
                    member -> new Description.Member(member.name(), member.offset() + 8, member.size(), member.kind(),
                            member.element(), member.type(), member.count(), member.release()));

            DescriptionRefused refused = assertThrows(DescriptionRefused.class, () -> mirror.bind(moved));

            assertTrue(refused.getMessage().contains("is described at offset"), refused.getMessage());
        }
    }

    @Test
    void aMemberOfAKindAboveTheLastIsRefusedWhole() {
        try (Opened mirror = Opened.service(0)) {
            Description unknown = withMember(mirror.description, (type, member) -> member.kind() == DescribedKind.F64,
                    member -> new Description.Member(member.name(), member.offset(), member.size(),
                            DescribedKind.LAST + 1, 0, member.type(), member.count(), member.release()));

            assertRefusedWith(mirror, unknown, "which this binding does not know");
        }
    }

    @Test
    void anArrayMemberReachingAnUnknownKindIsRefusedAtTheBind() {
        try (Opened mirror = Opened.service(0)) {
            Description unknown = withMember(mirror.description,
                    (type, member) -> member.kind() == DescribedKind.ARRAY, member -> new Description.Member(
                            member.name(), member.offset(), member.size(), member.kind(), 99, member.type(),
                            member.count(), member.release()));

            assertRefusedWith(mirror, unknown, "reaches elements of kind 99");
        }
    }

    @Test
    void aPointerMemberNamingAStructTheDescriptionLacksIsRefusedAtTheBind() {
        try (Opened mirror = Opened.service(0)) {
            Description undescribed = withMember(mirror.description, (type, member) ->
                    member.kind() == DescribedKind.POINTER && member.element() == DescribedKind.STRUCT,
                    member -> new Description.Member(member.name(), member.offset(), member.size(), member.kind(),
                            member.element(), Optional.of("undescribed"), member.count(), member.release()));

            assertRefusedWith(mirror, undescribed, "describes none by that name");
        }
    }

    @Test
    void aPointerParameterReachingAnUnknownKindIsRefusedAtTheBind() {
        try (Opened mirror = Opened.service(0)) {
            Description unknown = withParameter(mirror.description, "reflect",
                    parameter -> parameter.kind() == DescribedKind.POINTER && parameter.role() == Description.INPUT,
                    parameter -> new Description.Parameter(parameter.name(), parameter.kind(), parameter.role(), 99,
                            parameter.type(), parameter.count(), parameter.release()));

            assertRefusedWith(mirror, unknown, "reaches elements of kind 99");
        }
    }

    @Test
    void anOperationPastTheSizeItsDefinitionAnsweredIsNotOffered() {
        try (Opened mirror = Opened.service(0)) {
            long growAt = mirror.description.operation("grow").get().offset();
            long copy = mirror.copiedDefinition(growAt, Optional.<String>empty());
            try {
                BoundDefinition shortened = mirror.bindAt(withFirstShapeAtMost(mirror.description, growAt), copy);

                assertEquals(-1.5, answered(shortened.call("halve", Collections.singletonMap("number", -3.0))));
                assertFalse(shortened.operation("grow").isOffered());
                UnsupportedOperationException refused = assertThrows(UnsupportedOperationException.class,
                        () -> shortened.call("grow", Collections.singletonMap("amount", 1L)));
                assertTrue(refused.getMessage().contains("does not offer grow"), refused.getMessage());
                assertFalse(shortened.operation("count_releases").isOffered());
            } finally {
                mirror.access.free(copy);
            }
        }
    }

    @Test
    void anOperationTheStructHoldsAsNullIsNotOffered() {
        try (Opened mirror = Opened.service(0)) {
            long copy = mirror.copiedDefinition(0, Optional.of("halve"));
            try {
                BoundDefinition holed = mirror.bindAt(mirror.description, copy);

                assertFalse(holed.operation("halve").isOffered());
                assertThrows(UnsupportedOperationException.class, () -> holed.call("halve",
                        Collections.singletonMap("number", 1.0)));
                assertEquals(2L, answered(holed.call("grow", Collections.singletonMap("amount", 1L))));
            } finally {
                mirror.access.free(copy);
            }
        }
    }

    /**
     * Every layout and native function is made at the bind, and a call reuses its arena, so once the arena has grown to
     * hold a call no call lays out, binds or allocates anything native again.
     */
    @Test
    void aCallBuildsNothingAndAllocatesNoNativeMemoryOnceItsArenaHasGrown() {
        CountingAccess counting = new CountingAccess();
        try (Opened mirror = Opened.service(0, counting)) {
            BoundDefinition bound = mirror.bind(mirror.description);
            BoundOperation reflect = bound.operation("reflect");
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("label", "request");
            sample.put("tags", Arrays.asList("accept", "host", "content-length"));
            Map<String, Object> inputs = Collections.<String, Object>singletonMap("sample", sample);
            answered(reflect.call(inputs));
            answered(reflect.call(inputs));
            long work = counting.boundWork();
            long allocations = counting.allocations();

            for (int call = 0; call < 50; call++) {
                answered(reflect.call(inputs));
            }

            assertEquals(work, counting.boundWork());
            assertEquals(allocations, counting.allocations());
        }
    }

    @Test
    void aDescriptionBindsWhateverOrderItListsItsStructsIn() {
        try (Opened mirror = Opened.service(0)) {
            BoundDefinition reversed = mirror.bind(DescribedFixture.withTypesReversed(mirror.description));
            Map<String, Object> origin = new LinkedHashMap<>();
            origin.put("label", "embedded");
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("origin", origin);

            Map<?, ?> reflection = (Map<?, ?>) answered(reversed.call("reflect",
                    Collections.<String, Object>singletonMap("sample", sample)));

            assertEquals("embedded", ((Map<?, ?>) ((Map<?, ?>) reflection.get("sample")).get("origin")).get("label"));
        }
    }

    /**
     * A release of a handle while a call that was handed it is still inside that call waits for the call, which then
     * frees it, so the provider never reads a freed handle.
     */
    @Test
    void aHandleReleasedWhileACallHoldsItIsFreedOnlyOnceThatCallReturns() throws Exception {
        CountDownLatch parked = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean park = new AtomicBoolean();
        Runnable inside = () -> {
            if (park.getAndSet(false)) {
                parked.countDown();
                awaited(resume);
            }
        };
        DescribedHandles holdings = new DescribedHandles();
        try (Opened walker = Opened.service(1); Opened mirror = Opened.service(0)) {
            BoundDefinition bound = walker.bindWith(walker.description, holdings, inside);
            BoundDefinition counting = mirror.bind(mirror.description);
            Map<String, Object> open = new LinkedHashMap<>();
            open.put("label", "parked");
            open.put("start", 7L);
            DescribedHandle cursor = (DescribedHandle) answered(bound.call("open", open));
            long before = cursors(counting);
            park.set(true);
            ExecutorService thread = Executors.newSingleThreadExecutor();
            try {
                Future<DescribedAnswer> using = thread.submit(() -> bound.call("use",
                        Collections.<String, Object>singletonMap("cursor", cursor)));
                awaited(parked);

                holdings.releaseAll();
                long whileTheCallRuns = cursors(counting);
                resume.countDown();
                Object start = answered(using.get(10, TimeUnit.SECONDS));

                assertTrue(cursor.isReleased());
                assertEquals(before, whileTheCallRuns, "freed under the call that was handed it");
                assertEquals(7L, start);
                assertEquals(before + 1, cursors(counting));
            } finally {
                resume.countDown();
                thread.shutdownNow();
            }
        }
    }

    /**
     * Run on a thread of 256 KiB of stack, below the 512 KiB a small plugin thread may have and below every CI
     * runner's default, since a walk that recursed per level overflowed a Linux runner's stack before its limit.
     */
    @Test
    void structsAndValuesNestedPastTheDepthLimitAreRefusedBothWaysOnASmallStack() throws Exception {
        NativeAccess access = NativeBackends.current();
        Description.Member next = new Description.Member("next", 0, 8, DescribedKind.POINTER, DescribedKind.STRUCT,
                Optional.of("link"), Optional.<String>empty(), Optional.<String>empty());
        Description.Member number = new Description.Member("number", 8, 8, DescribedKind.U64, 0,
                Optional.<String>empty(), Optional.<String>empty(), Optional.<String>empty());
        Description chain = new Description("link", Collections.singletonList(new Description.Type("link", 16, 16,
                Arrays.asList(next, number))), Collections.<Description.Enumeration>emptyList(),
                Collections.<Description.Operation>emptyList(), Collections.<String>emptyList(),
                Collections.<Description>emptyList());
        StructPlan link = new DescribedLayouts(access, chain).plan(Optional.of("link"), "the test");
        StructPlan branch = branchPlan(access);
        Map<String, Object> deepest = deepestBranch();
        List<Throwable> refused = new ArrayList<>();
        Thread small = new Thread(null, () -> {
            try (NativeArena arena = new NativeArena(access)) {
                DescribedCall call = new DescribedCall(access, arena, new DescribedHandles(),
                        DescribedCall.Carrier.NONE, false);
                long node = arena.allocateAddress(16);
                access.putLong(node, 0, node);
                Map<String, Object> cycle = new HashMap<>();
                cycle.put("next", cycle);
                long value = arena.allocateAddress(ValueTrees.NODE_SIZE);
                access.putInt(value, DescriptionLayouts.VALUE.offsetOf("kind"), ValueTrees.LIST);
                access.putLong(value, DescriptionLayouts.VALUE.offsetOf("items"), value);
                access.putLong(value, DescriptionLayouts.VALUE.offsetOf("count"), 1);
                List<Object> nested = new ArrayList<>();
                nested.add(nested);

                refused.add(thrown(() -> call.readStruct(link, node)));
                refused.add(thrown(() -> call.writeStruct(link, arena.allocateAddress(16), cycle, "the cycle")));
                refused.add(thrown(() -> ValueTrees.read(access, value)));
                refused.add(thrown(() -> ValueTrees.write(access, arena, arena.allocateAddress(ValueTrees.NODE_SIZE),
                        nested, "the list")));
                refused.add(thrown(() -> call.writeStruct(branch, arena.allocateAddress(branch.size), deepest,
                        "the branch")));
            }
        }, "small stack", 256 * 1024);
        small.start();
        small.join(TimeUnit.SECONDS.toMillis(30));

        assertEquals(5, refused.size(), String.valueOf(refused));
        assertInstanceOf(IllegalStateException.class, refused.get(0));
        assertInstanceOf(IllegalArgumentException.class, refused.get(1));
        assertInstanceOf(IllegalStateException.class, refused.get(2));
        assertInstanceOf(IllegalArgumentException.class, refused.get(3));
        assertInstanceOf(IllegalArgumentException.class, refused.get(4));
        for (Throwable each : refused) {
            assertTrue(each.getMessage().contains("deeper than " + ValueTrees.DEPTH_LIMIT), each.toString());
        }
        assertTrue(refused.get(4).getMessage().startsWith("the branch.items[0].items[0]."), "the path is spelled");
    }

    /** A struct holding a counted array of itself and a value: {@code items}, {@code item_count}, {@code value}. */
    private static StructPlan branchPlan(NativeAccess access) {
        long valueSize = ValueTrees.NODE_SIZE;
        Description.Member items = new Description.Member("items", 0, 8, DescribedKind.ARRAY, DescribedKind.STRUCT,
                Optional.of("branch"), Optional.of("item_count"), Optional.<String>empty());
        Description.Member count = new Description.Member("item_count", 8, 8, DescribedKind.SIZE, 0,
                Optional.<String>empty(), Optional.<String>empty(), Optional.<String>empty());
        Description.Member value = new Description.Member("value", 16, valueSize, DescribedKind.VALUE, 0,
                Optional.<String>empty(), Optional.<String>empty(), Optional.<String>empty());
        Description tree = new Description("branch", Collections.singletonList(new Description.Type("branch",
                16 + valueSize, 16 + valueSize, Arrays.asList(items, count, value))),
                Collections.<Description.Enumeration>emptyList(), Collections.<Description.Operation>emptyList(),
                Collections.<String>emptyList(), Collections.<Description>emptyList());
        return new DescribedLayouts(access, tree).plan(Optional.of("branch"), "the test");
    }

    /**
     * A branch nested just inside the struct limit through array elements, two path segments a level, whose value
     * then nests past the value limit through records holding lists: a refused place of about 3000 segments.
     */
    private static Map<String, Object> deepestBranch() {
        Object value = null;
        for (int level = 0; level <= ValueTrees.DEPTH_LIMIT; level++) {
            value = level % 2 == 0 ? Collections.singletonList(value) : Collections.singletonMap("k", value);
        }
        Map<String, Object> branch = Collections.singletonMap("value", value);
        for (int level = 0; level < ValueTrees.DEPTH_LIMIT - 1; level++) {
            branch = Collections.<String, Object>singletonMap("items", Collections.singletonList(branch));
        }
        return branch;
    }

    /** value.h holds each key of a record once, so a provider's record repeating one is refused, naming it. */
    @Test
    void aRecordThatRepeatsAKeyIsRefusedAsTheProvidersFault() {
        NativeAccess access = NativeBackends.current();
        Layout node = DescriptionLayouts.VALUE;
        Layout entry = DescriptionLayouts.ENTRY;
        try (NativeArena arena = new NativeArena(access)) {
            long key = arena.allocateAddress(4);
            access.writeBytes(key, 0, new byte[] {'k', 'e', 'y'});
            long entries = arena.allocateAddress(2 * entry.byteSize());
            for (int index = 0; index < 2; index++) {
                long at = entries + index * entry.byteSize();
                access.putLong(at, entry.offsetOf("key"), key);
                access.putLong(at, entry.offsetOf("key_length"), 3);
                access.putInt(at + entry.offsetOf("value"), node.offsetOf("kind"), ValueTrees.NUMBER);
                access.putDouble(at + entry.offsetOf("value"), node.offsetOf("number"), index + 1);
            }
            long record = arena.allocateAddress(node.byteSize());
            access.putInt(record, node.offsetOf("kind"), ValueTrees.RECORD);
            access.putLong(record, node.offsetOf("entries"), entries);
            access.putLong(record, node.offsetOf("count"), 2);

            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> ValueTrees.read(access, record));

            assertTrue(refused.getMessage().contains("the key key twice"), refused.getMessage());
        }
    }

    @Test
    void anAnswerCarryingADefinitionHandsItBoundAndCallableUntilTheAnswerIsReleased() {
        try (Opened walker = Opened.service(1)) {
            BoundDefinition bound = walker.bind(walker.description);
            DescribedAnswer.Answered loan = lend(bound);
            BoundDefinition counter = carried(loan);

            Object added = answered(counter.call("add", amount(5L)));
            loan.release();
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () ->
                    counter.call("add", amount(5L)));
            loan.release();

            assertEquals(1005L, added);
            assertTrue(refused.getMessage().contains("carried by an answer that has ended"), refused.getMessage());
            assertEquals(Collections.<String>emptyList(), counter.blockingOperations());
            assertEquals(Collections.singletonList("wait"), bound.blockingOperations());
        }
    }

    /**
     * A description answered before its {@code blocking} and {@code definitions} were appended, its {@code size} at the
     * first shape, reads as listing none, however its memory goes on.
     */
    @Test
    void aDescriptionAnsweredAtItsFirstShapeReadsAsBlockingNothingAndNestingNothing() {
        try (Opened walker = Opened.service(1)) {
            long whole = DescriptionLayouts.DESCRIPTION.byteSize();
            long firstShape = DescriptionLayouts.DESCRIPTION.offsetOf("blocking");
            long older = walker.access.allocate(whole);
            try {
                walker.access.writeBytes(older, 0, walker.access.readBytes(walker.describedAt, 0, (int) whole));
                walker.access.putInt(older, 0, (int) firstShape);

                Description read = new DescriptionReader(walker.access).read(older);

                assertEquals(Collections.singletonList("wait"), walker.description.blocking());
                assertEquals(1, walker.description.definitions().size());
                assertEquals(Collections.<String>emptyList(), read.blocking());
                assertEquals(Collections.<Description>emptyList(), read.definitions());
                assertEquals(walker.description.operations().size(), read.operations().size());
            } finally {
                walker.access.free(older);
            }
        }
    }

    /**
     * A definition is bound afresh from the struct each answer points at, so a second answer carrying a definition at
     * the very address of one already released is callable, where a binding keeping definitions by address would hand
     * back the released one.
     */
    @Test
    void everyAnswerCarriesADefinitionBoundAfreshFromTheStructItPointsAt() {
        try (Opened walker = Opened.service(1)) {
            BoundDefinition bound = walker.bind(walker.description);
            DescribedAnswer.Answered first = lend(bound);
            BoundDefinition lentFirst = carried(first);
            first.release();

            DescribedAnswer.Answered second = lend(bound);
            BoundDefinition lentSecond = carried(second);

            assertEquals(lentFirst.address(), lentSecond.address(), "the walker lends one static counter");
            assertEquals(1005L, answered(lentSecond.call("add", amount(5L))));
            second.release();
        }
    }

    /**
     * Two threads call through a carried definition, parked inside the call, while a third releases the answer that
     * carried it: a new call is refused at once, both calls in flight answer whole, the refused one's refusal freed by
     * the counter's own release, and the answer is freed only once the last of them has returned.
     */
    @Test
    void releasingTheAnswerRefusesNewCallsAtOnceAndEndsItOnlyOnceTheCallsInFlightReturn() throws Exception {
        CountDownLatch parked = new CountDownLatch(2);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicInteger parking = new AtomicInteger();
        Runnable inside = () -> {
            if (parking.getAndDecrement() > 0) {
                parked.countDown();
                awaited(resume);
            }
        };
        DescribedHandles holdings = new DescribedHandles();
        try (Opened walker = Opened.service(1); Opened mirror = Opened.service(0)) {
            BoundDefinition bound = walker.bindWith(walker.description, holdings, inside);
            BoundDefinition counting = mirror.bind(mirror.description);
            DescribedAnswer.Answered loan = lend(bound);
            BoundDefinition counter = carried(loan);
            long before = releaseCount(counting, "nested_refusals");
            parking.set(2);
            ExecutorService threads = Executors.newFixedThreadPool(2);
            try {
                Future<DescribedAnswer> adding = threads.submit(() -> counter.call("add", amount(5L)));
                Future<DescribedAnswer> refusing = threads.submit(() -> counter.call("add", amount(13L)));
                awaited(parked);

                loan.release();
                boolean keptWhileTheCallsRun = holdings.keepsAnyAnswer();
                Throwable refusedAtOnce = thrown(() -> counter.call("add", amount(5L)));
                resume.countDown();
                DescribedAnswer added = adding.get(10, TimeUnit.SECONDS);
                DescribedAnswer refused = refusing.get(10, TimeUnit.SECONDS);

                assertTrue(keptWhileTheCallsRun, "the answer was freed under the calls running in its definition");
                assertInstanceOf(IllegalArgumentException.class, refusedAtOnce);
                assertEquals(1005L, answered(added));
                assertInstanceOf(DescribedAnswer.Refused.class, refused);
                assertEquals(before + 1, releaseCount(counting, "nested_refusals"));
                assertFalse(holdings.keepsAnyAnswer(), "the last call to return did not free the answer");
            } finally {
                resume.countDown();
                threads.shutdownNow();
            }
        }
    }

    /**
     * Ending a kept answer releases what was got through each definition it carried before the answer itself, and
     * neither while a call still runs in any of them.
     */
    @Test
    void aKeptAnswerReleasesWhatWasGotThroughItsDefinitionsFirstAndOnlyOnceTheirCallsReturn() {
        List<String> order = new ArrayList<>();
        DescribedHandles library = new DescribedHandles();
        CarriedScope first = CarriedScope.carried();
        CarriedScope second = CarriedScope.carried();
        DescribedHandles gotThroughSecond = library.child();
        gotThroughSecond.hold(1, "opaque", recording(order, "the handle"));
        KeptAnswer answer = new KeptAnswer(library, Arrays.asList(new KeptAnswer.Carried(first, library.child()),
                new KeptAnswer.Carried(second, gotThroughSecond)), () -> order.add("the answer"));
        library.keep(answer);
        assertTrue(first.enter());
        assertTrue(second.enter());

        answer.release();
        boolean firstRefused = !first.enter();
        boolean secondRefused = !second.enter();
        List<String> whileBothRun = new ArrayList<>(order);
        first.leave();
        List<String> whileOneRuns = new ArrayList<>(order);
        second.leave();
        answer.release();

        assertTrue(firstRefused && secondRefused, "a new call was let into a definition whose answer ended");
        assertEquals(Collections.<String>emptyList(), whileBothRun);
        assertEquals(Collections.<String>emptyList(), whileOneRuns);
        assertEquals(Arrays.asList("the handle", "the answer"), order);
        assertFalse(library.keepsAnyAnswer());
    }

    /**
     * A handle got through a definition an answer carried is taken by every definition of the library that answered
     * it, and released only by the end of that answer.
     */
    @Test
    void aHandleGotThroughACarriedDefinitionIsTakenAnywhereInItsLibraryAndReleasedByItsAnswer() {
        List<String> order = new ArrayList<>();
        DescribedHandles library = new DescribedHandles();
        DescribedHandles carried = library.child();
        DescribedHandles sibling = library.child();
        DescribedHandle handle = carried.hold(1, "opaque", recording(order, "the handle"));

        DescribedHandle takenBySibling = sibling.use(handle, "opaque", CallPath.of("the sibling"));
        takenBySibling.leave();
        DescribedHandle takenByTheLibrary = library.use(handle, "opaque", CallPath.of("the library"));
        takenByTheLibrary.leave();
        sibling.releaseAll();
        boolean releasedBySibling = handle.isReleased();
        library.releaseAll();
        boolean releasedByTheLibrary = handle.isReleased();
        carried.releaseAll();

        assertSame(handle, takenBySibling);
        assertSame(handle, takenByTheLibrary);
        assertFalse(releasedBySibling);
        assertFalse(releasedByTheLibrary);
        assertTrue(handle.isReleased());
        assertEquals(Collections.singletonList("the handle"), order);
    }

    @Test
    void bindDefinitionBindsADefinitionGotSomeOtherWayWithItsCarriedDefinitions() {
        try (Opened walker = Opened.service(1)) {
            BoundDefinition bound = BoundDefinition.bindDefinition(walker.access.addressed(walker.definition, 8),
                    walker.access.addressed(walker.describedAt, 8));
            DescribedAnswer.Answered loan = lend(bound);
            BoundDefinition counter = carried(loan);

            Object added = answered(counter.call("add", amount(5L)));
            loan.release();

            assertEquals(walker.bind(walker.description).operationNames(), bound.operationNames());
            assertEquals(walker.definition, bound.address());
            assertEquals(1005L, added);
            assertThrows(IllegalArgumentException.class, () -> counter.call("add", amount(5L)));
        }
    }

    @Test
    void bindDefinitionRefusesADescriptionThisPlatformCannotBindAsTheCallersMistake() {
        try (Opened walker = Opened.service(1)) {
            long tooSmall = walker.copiedDefinition(8, Optional.<String>empty());
            try {
                IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () ->
                        BoundDefinition.bindDefinition(walker.access.addressed(tooSmall, 8),
                                walker.access.addressed(walker.describedAt, 8)));

                assertTrue(refused.getMessage().contains("first shape"), refused.getMessage());
            } finally {
                walker.access.free(tooSmall);
            }
        }
    }

    /**
     * A release made while the binary's gate is shut, as one racing the last holder at the unload is, frees nothing
     * then; the close frees it, once.
     */
    @Test
    void aHandleReleasedWhileTheGateIsShutIsFreedAtTheClose() {
        AtomicInteger freed = new AtomicInteger();
        DescribedHandles holdings = new DescribedHandles();
        holdings.gate(new CallGate() {
            @Override
            public boolean enter() {
                return false;
            }

            @Override
            public void leave() {
            }
        });
        DescribedHandle handle = holdings.hold(1, "opaque", counting(freed, false));

        handle.release();
        int freedByTheRelease = freed.get();
        holdings.releaseAtTheClose();
        holdings.releaseAtTheClose();

        assertEquals(0, freedByTheRelease);
        assertEquals(1, freed.get());
    }

    /** A failing release of one handle a call let go of leaves no other handle of that call held for ever. */
    @Test
    void aFailingReleaseLeavesEveryOtherHandleOfTheCallFreed() {
        AtomicInteger freed = new AtomicInteger();
        DescribedHandles holdings = new DescribedHandles();
        DescribedHandle failing = holdings.hold(1, "opaque", counting(freed, true));
        DescribedHandle other = holdings.hold(2, "opaque", counting(freed, false));
        MemberPlan handlePlan = new MemberPlan("handle", DescribedKind.HANDLE, 0, 0, MemberPlan.NO_COUNT, 0,
                "opaque", null);
        NativeAccess access = NativeBackends.current();
        try (NativeArena arena = new NativeArena(access)) {
            DescribedCall call = new DescribedCall(access, arena, holdings, DescribedCall.Carrier.NONE, false);
            call.reached(handlePlan, failing, "the first");
            call.reached(handlePlan, other, "the second");
            failing.release();
            other.release();

            IllegalStateException thrown = assertThrows(IllegalStateException.class, call::leaveUsedHandles);

            assertEquals("the release failed", thrown.getMessage());
            assertEquals(2, freed.get(), "both releases ran, the failing one first");
        }
    }

    private static DescribedAnswer.Answered lend(BoundDefinition walker) {
        return assertInstanceOf(DescribedAnswer.Answered.class, walker.call("lend",
                Collections.<String, Object>emptyMap()));
    }

    /** The one definition the loan answered carries. */
    private static BoundDefinition carried(DescribedAnswer.Answered loan) {
        Map<?, ?> struct = assertInstanceOf(Map.class, loan.value());
        assertEquals(1, struct.size(), struct.toString());
        return assertInstanceOf(BoundDefinition.class, struct.values().iterator().next());
    }

    private static Map<String, Object> amount(long amount) {
        return Collections.<String, Object>singletonMap("amount", amount);
    }

    private static long releaseCount(BoundDefinition mirror, String kind) {
        Map<?, ?> counts = (Map<?, ?>) answered(mirror.call("count_releases", Collections.<String, Object>emptyMap()));
        return (Long) counts.get(kind);
    }

    /** A release adding {@code what} to {@code order} for every handle it frees. */
    private static ReleasePlan recording(List<String> order, String what) {
        return new ReleasePlan("recording", arguments -> {
            order.add(what);
            return 0;
        }, 0);
    }

    /** A release counting every handle it frees, failing when asked to after counting. */
    private static ReleasePlan counting(AtomicInteger freed, boolean failing) {
        return new ReleasePlan("counting", arguments -> {
            freed.incrementAndGet();
            if (failing) {
                throw new IllegalStateException("the release failed");
            }
            return 0;
        }, 0);
    }

    private static Throwable thrown(Runnable walk) {
        try {
            walk.run();
            return new AssertionError("the walk finished");
        } catch (RuntimeException | StackOverflowError refused) {
            return refused;
        }
    }

    private static long cursors(BoundDefinition mirror) {
        Map<?, ?> counts = (Map<?, ?>) answered(mirror.call("count_releases", Collections.<String, Object>emptyMap()));
        return (Long) counts.get("cursors");
    }

    private static void awaited(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("a latch was not counted down in 10 seconds");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void assertRefusedWith(Opened opened, Description description, String naming) {
        DescriptionRefused refused = assertThrows(DescriptionRefused.class, () -> opened.bind(description));
        assertTrue(refused.getMessage().contains(naming), refused.getMessage());
    }

    private static Object answered(DescribedAnswer answer) {
        DescribedAnswer.Answered answered = assertInstanceOf(DescribedAnswer.Answered.class, answer);
        return answered.value();
    }

    /** The description with the first member {@code which} picks changed by {@code change}. */
    private static Description withMember(Description description, BiPredicate<Description.Type, Description.Member>
            which, UnaryOperator<Description.Member> change) {
        List<Description.Type> types = new ArrayList<>();
        boolean changed = false;
        for (Description.Type type : description.types()) {
            List<Description.Member> members = new ArrayList<>();
            for (Description.Member member : type.members()) {
                boolean picked = !changed && which.test(type, member);
                members.add(picked ? change.apply(member) : member);
                changed |= picked;
            }
            types.add(new Description.Type(type.name(), type.size(), type.firstShape(), members));
        }
        assertTrue(changed, "no member was picked");
        return new Description(description.definition(), types, description.enums(), description.operations(),
                description.blocking(), description.definitions());
    }

    /** The description with the first parameter of {@code operation} that {@code which} picks changed. */
    private static Description withParameter(Description description, String operation,
                                             Predicate<Description.Parameter> which,
                                             UnaryOperator<Description.Parameter> change) {
        List<Description.Operation> operations = new ArrayList<>();
        boolean changed = false;
        for (Description.Operation described : description.operations()) {
            List<Description.Parameter> parameters = new ArrayList<>();
            for (Description.Parameter parameter : described.parameters()) {
                boolean picked = !changed && described.name().equals(operation) && which.test(parameter);
                parameters.add(picked ? change.apply(parameter) : parameter);
                changed |= picked;
            }
            operations.add(new Description.Operation(described.name(), described.offset(), described.returns(),
                    parameters));
        }
        assertTrue(changed, "no parameter was picked");
        return new Description(description.definition(), description.types(), description.enums(), operations,
                description.blocking(), description.definitions());
    }

    private static Description withFirstShapeAtMost(Description description, long size) {
        List<Description.Type> types = new ArrayList<>();
        for (Description.Type type : description.types()) {
            types.add(type.name().equals(description.definition())
                    ? new Description.Type(type.name(), type.size(), Math.min(type.firstShape(), size), type.members())
                    : type);
        }
        return new Description(description.definition(), types, description.enums(), description.operations(),
                description.blocking(), description.definitions());
    }

    /** One service of the described library, its binary opened through the seam and its description read. */
    private static final class Opened implements AutoCloseable {
        final NativeAccess access;
        final OpenLibrary library;
        final long definition;
        final long describedAt;
        final Description description;

        private Opened(NativeAccess access, OpenLibrary library, long definition, long describedAt) {
            this.access = access;
            this.library = library;
            this.definition = definition;
            this.describedAt = describedAt;
            this.description = new DescriptionReader(access).read(describedAt);
        }

        static Opened service(int index) {
            return service(index, NativeBackends.current());
        }

        static Opened service(int index, NativeAccess access) {
            LibraryDeclaration.Service service = DescribedFixture.declaration("library.json").services().get(index);
            OpenLibrary library = access.open(DescribedFixture.binary());
            NativeSignature answering = NativeSignature.of(NativeType.ADDRESS);
            long definition = library.function(service.functions().get(0).symbol(), answering).call();
            long description = library.function(service.description().get(), answering).call();
            return new Opened(access, library, definition, description);
        }

        BoundDefinition bind(Description chosen) {
            return bindAt(chosen, definition);
        }

        BoundDefinition bindAt(Description chosen, long at) {
            return DescribedBinder.bind(access, chosen, at, access.addressed(at, 8),
                    new BoundOperation.Bound(access, new SpareArena(access), new DescribedHandles(), CallGate.OPEN));
        }

        BoundDefinition bindWith(Description chosen, DescribedHandles holdings, Runnable insideTheCall) {
            return DescribedBinder.bind(access, chosen, definition, access.addressed(definition, 8),
                    new BoundOperation.Bound(access, new SpareArena(access), holdings, CallGate.OPEN, insideTheCall));
        }

        /**
         * A copy of the definition's struct, its size lowered to {@code size} unless 0, and the named operation's
         * pointer set to NULL when one is named; freed by its caller.
         */
        long copiedDefinition(long size, Optional<String> nulled) {
            int answered = access.getInt(definition, 0);
            long copy = access.allocate(answered);
            access.writeBytes(copy, 0, access.readBytes(definition, 0, answered));
            if (size != 0) {
                access.putInt(copy, 0, (int) size);
            }
            if (nulled.isPresent()) {
                access.putLong(copy, description.operation(nulled.get()).get().offset(), 0);
            }
            return copy;
        }

        @Override
        public void close() {
            library.close();
        }
    }
}
