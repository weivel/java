package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The binder itself, handed the described library's real definition and its real description read from the binary,
 * or a copy of either changed in one place, so each refusal at the bind and each rule of a call is proven on its own.
 * No type of the library is named: a changed member is found by its kind, a parameter by its operation.
 */
class DescribedBindingTest {
    @BeforeEach
    void onlyWhereDescriptionsAreBound() {
        assumeFalse(DescribedFixture.onJna(), "a described definition is not yet bound on JNA");
    }

    @Test
    void theRealDescriptionBindsEveryOperation() {
        try (Opened mirror = Opened.service(0)) {
            BoundDefinition bound = mirror.bind(mirror.description);

            assertTrue(bound.operationNames().contains("reflect"), bound.toString());
            assertEquals("the first letter", answered(bound.call("find", Collections.singletonMap("key", "alpha"))));
        }
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
    void aTextAnswerNamingAReleaseIsRefused() {
        try (Opened mirror = Opened.service(0)) {
            String release = firstRelease(mirror.description);
            Description freed = withParameter(mirror.description, "find",
                    parameter -> parameter.kind() == DescribedKind.TEXT && parameter.role() == Description.ANSWER,
                    parameter -> new Description.Parameter(parameter.name(), parameter.kind(), parameter.role(),
                            parameter.element(), parameter.type(), parameter.count(), Optional.of(release)));

            assertRefusedWith(mirror, freed, "not yet ruled");
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
                Collections.<Description.Operation>emptyList());
        StructPlan link = new DescribedLayouts(access, chain).plan(Optional.of("link"), "the test");
        List<Throwable> refused = new ArrayList<>();
        Thread small = new Thread(null, () -> {
            try (NativeArena arena = new NativeArena(access)) {
                DescribedCall call = new DescribedCall(access, arena, new DescribedHandles());
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
            }
        }, "small stack", 256 * 1024);
        small.start();
        small.join(TimeUnit.SECONDS.toMillis(30));

        assertEquals(4, refused.size(), String.valueOf(refused));
        assertInstanceOf(IllegalStateException.class, refused.get(0));
        assertInstanceOf(IllegalArgumentException.class, refused.get(1));
        assertInstanceOf(IllegalStateException.class, refused.get(2));
        assertInstanceOf(IllegalArgumentException.class, refused.get(3));
        for (Throwable each : refused) {
            assertTrue(each.getMessage().contains("deeper than " + ValueTrees.DEPTH_LIMIT), each.toString());
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
            DescribedCall call = new DescribedCall(access, arena, holdings);
            call.reached(handlePlan, failing, "the first");
            call.reached(handlePlan, other, "the second");
            failing.release();
            other.release();

            IllegalStateException thrown = assertThrows(IllegalStateException.class, call::leaveUsedHandles);

            assertEquals("the release failed", thrown.getMessage());
            assertEquals(2, freed.get(), "both releases ran, the failing one first");
        }
    }

    /** A release counting every handle it frees, failing when asked to after counting. */
    private static ReleasePlan counting(AtomicInteger freed, boolean failing) {
        return new ReleasePlan("counting", arguments -> {
            freed.incrementAndGet();
            if (failing) {
                throw new IllegalStateException("the release failed");
            }
            return 0;
        }, 0, true);
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
        Object last = null;
        for (Object count : counts.values()) {
            last = count;
        }
        return (Long) last;
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

    private static String firstRelease(Description description) {
        for (Description.Operation operation : description.operations()) {
            if (operation.returns() == Description.NOTHING) {
                return operation.name();
            }
        }
        throw new AssertionError("the description has no release");
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
        return new Description(description.definition(), types, description.enums(), description.operations());
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
        return new Description(description.definition(), description.types(), description.enums(), operations);
    }

    private static Description withFirstShapeAtMost(Description description, long size) {
        List<Description.Type> types = new ArrayList<>();
        for (Description.Type type : description.types()) {
            types.add(type.name().equals(description.definition())
                    ? new Description.Type(type.name(), type.size(), Math.min(type.firstShape(), size), type.members())
                    : type);
        }
        return new Description(description.definition(), types, description.enums(), description.operations());
    }

    /** One service of the described library, its binary opened through the seam and its description read. */
    private static final class Opened implements AutoCloseable {
        final NativeAccess access;
        final OpenLibrary library;
        final long definition;
        final Description description;

        private Opened(NativeAccess access, OpenLibrary library, long definition, Description description) {
            this.access = access;
            this.library = library;
            this.definition = definition;
            this.description = description;
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
            return new Opened(access, library, definition, new DescriptionReader(access).read(description));
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
