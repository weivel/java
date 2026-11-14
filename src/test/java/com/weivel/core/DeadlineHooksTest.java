package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The wrapper's deadline hooks: the announcement recorded at drain time, the timer told before dispatch, the due check. */
class DeadlineHooksTest {
    private final TestCores.RecordingSink sink = new TestCores.RecordingSink();
    private final TestCores.RecordingListener listener = new TestCores.RecordingListener();

    @Test
    void theTimerIsToldTheLatestAnnouncementBeforeAnyEventOfItsCallIsDispatched() {
        Core core = TestCores.core(listener, sink);
        TestHooks hooks = TestHooks.on(core);
        List<Integer> toldWhenHeard = new ArrayList<>();
        core.addListener(event -> toldWhenHeard.add(hooks.follows().size()));

        ok(core.call(CoreCalls.wantService(HOST, "awaited", WantOptions.timed(0, 100))));

        assertEquals(List.of(new Follow(100, true)), hooks.follows());
        assertEquals(List.of(1), toldWhenHeard);
        assertEquals(List.of(false), hooks.lockSeen());
        core.destroy();
    }

    @Test
    void anAnnouncementIsRecordedWhateverItsCopyDid() {
        EventCopyTest.FailingCopy failing = new EventCopyTest.FailingCopy(sequence -> true, sequence -> true);
        Core core = TestCores.core(TestCores.functions(), failing, listener, sink);
        TestHooks hooks = TestHooks.on(core);

        ok(core.call(CoreCalls.wantService(HOST, "awaited", WantOptions.timed(0, 100))));

        assertEquals(List.of(), listener.events());
        assertEquals(List.of(new Follow(100, true)), hooks.follows());
        assertEquals(1, sink.of(FailureKind.EVENT_DROPPED).size());
        core.destroy();
    }

    @Test
    void aDeadlineThatFellDueIsHandedOverBeforeTheCallIsMade() {
        TestCores.CallLog log = new TestCores.CallLog();
        Core core = TestCores.core(log.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
        TestHooks hooks = TestHooks.on(core);
        long want = ok(core.call(CoreCalls.wantService(HOST, "late", WantOptions.timed(0, 100))));
        hooks.clock = 100;
        int readsBefore = hooks.reads.get();
        log.clear();

        ok(core.call(CoreCalls.registerService(HOST, "late")));

        CoreEvent.WantSettledEvent settled = listener.of(CoreEvent.WantSettledEvent.class).getFirst();
        assertEquals(want, settled.want());
        assertEquals(CoreConstants.REFUSAL_TIMED_OUT, settled.refusal().orElseThrow().code());
        assertEquals(1, hooks.reads.get() - readsBefore);
        assertEquals(List.of(CoreFunction.ADVANCE, CoreFunction.REGISTER_SERVICE),
                log.calls().stream().filter(function -> function == CoreFunction.ADVANCE
                        || function == CoreFunction.REGISTER_SERVICE).toList());
        assertEquals(new Follow(0, false), hooks.follows().getLast());
        assertTrue(hooks.lockSeen().stream().noneMatch(Boolean::booleanValue));
        core.destroy();
    }

    @Test
    void anAdvanceTheCallerAsksForIsNotCheckedFirst() {
        Core core = TestCores.core(listener, sink);
        TestHooks hooks = TestHooks.on(core);
        ok(core.call(CoreCalls.wantService(HOST, "waiting", WantOptions.timed(0, 100))));
        hooks.clock = 200;
        int readsBefore = hooks.reads.get();

        ok(core.call(CoreCalls.advance(50)));

        assertEquals(readsBefore, hooks.reads.get());
        assertEquals(List.of(), listener.of(CoreEvent.WantSettledEvent.class));
        core.destroy();
    }

    @Test
    void noClockIsReadWhileNoDeadlineIsArmed() {
        Core core = TestCores.core(listener, sink);
        TestHooks hooks = TestHooks.on(core);

        ok(core.call(CoreCalls.registerService(HOST, "untimed")));

        assertEquals(0, hooks.reads.get());
        core.destroy();
    }

    @Test
    void inADispatchTheDueAdvancesEventsJoinTheEndAheadOfTheCallsOwn() {
        Core core = TestCores.core(listener, sink);
        TestHooks hooks = TestHooks.on(core);
        ok(core.call(CoreCalls.watchService(HOST, "trigger")));
        ok(core.call(CoreCalls.watchService(HOST, "trigger")));
        ok(core.call(CoreCalls.watchService(HOST, "inner")));
        ok(core.call(CoreCalls.wantService(HOST, "inner", WantOptions.timed(0, 100))));
        listener.clear();
        AtomicBoolean first = new AtomicBoolean(true);
        core.addListener(event -> {
            if (first.getAndSet(false)) {
                hooks.clock = 100;
                ok(core.call(CoreCalls.registerService(HOST, "inner")));
            }
        });

        ok(core.call(CoreCalls.registerService(HOST, "trigger")));

        List<Class<?>> heard = listener.events().stream().<Class<?>>map(Object::getClass).toList();
        assertEquals(List.of(CoreEvent.WatchEvent.class, CoreEvent.WatchEvent.class,
                CoreEvent.WantSettledEvent.class, CoreEvent.DeadlineEvent.class, CoreEvent.WatchEvent.class), heard);
        core.destroy();
    }

    @Test
    void hooksSetWhileADeadlineIsRecordedAreToldItAtOnce() {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.wantService(HOST, "earlier", WantOptions.timed(0, 70))));

        TestHooks hooks = TestHooks.on(core);

        assertEquals(List.of(new Follow(70, true)), hooks.follows());
        core.destroy();
    }

    @Test
    void aFailingHookGoesToTheSink() {
        Core core = TestCores.core(listener, sink);
        CoreInternals.installDeadlineHooks(core, new DeadlineHooks() {
            @Override
            public long now() {
                throw new IllegalStateException("a clock failing on purpose");
            }

            @Override
            public void follow(long at, boolean set) {
                throw new IllegalStateException("a timer failing on purpose");
            }
        });

        ok(core.call(CoreCalls.wantService(HOST, "failing", WantOptions.timed(0, 100))));
        assertEquals(1, sink.of(FailureKind.DEADLINE_HOOK).size());

        assertEquals(new Answer.Absent<Long>(), core.call(CoreCalls.getService(HOST, "failing")));

        assertEquals(2, sink.of(FailureKind.DEADLINE_HOOK).size());
        core.destroy();
    }

    @Test
    void hooksAreTakenOffOnlyByWhoeverInstalledThemAndNeverInstalledOverOthers() {
        Core core = TestCores.core(listener, sink);
        TestHooks installed = TestHooks.on(core);
        TestHooks other = new TestHooks(core);

        assertInstanceOf(Answer.Mistake.class, CoreInternals.installDeadlineHooks(core, other));
        assertFalse(CoreInternals.removeDeadlineHooks(core, other));
        ok(core.call(CoreCalls.wantService(HOST, "followed", WantOptions.timed(0, 100))));
        assertTrue(CoreInternals.removeDeadlineHooks(core, installed));
        ok(core.call(CoreCalls.wantService(HOST, "unfollowed", WantOptions.timed(0, 50))));

        assertEquals(List.of(new Follow(100, true)), installed.follows());
        assertEquals(List.of(), other.follows());
        core.destroy();
    }

    record Follow(long at, boolean set) {
    }

    /** Hooks over a clock the test sets, recording what they are told and whether the layer lock was held. */
    static final class TestHooks implements DeadlineHooks {
        final AtomicInteger reads = new AtomicInteger();
        volatile long clock;
        private final Core core;
        private final List<Follow> follows = Collections.synchronizedList(new ArrayList<>());
        private final List<Boolean> lockSeen = Collections.synchronizedList(new ArrayList<>());

        private TestHooks(Core core) {
            this.core = core;
        }

        static TestHooks on(Core core) {
            TestHooks hooks = new TestHooks(core);
            assertEquals(new Answer.Ok<Void>(null), CoreInternals.installDeadlineHooks(core, hooks));
            return hooks;
        }

        @Override
        public long now() {
            reads.incrementAndGet();
            lockSeen.add(core.isLockHeld());
            return clock;
        }

        @Override
        public void follow(long at, boolean set) {
            lockSeen.add(core.isLockHeld());
            follows.add(new Follow(at, set));
        }

        List<Follow> follows() {
            synchronized (follows) {
                return List.copyOf(follows);
            }
        }

        List<Boolean> lockSeen() {
            synchronized (lockSeen) {
                return List.copyOf(lockSeen);
            }
        }
    }

    @Test
    void theDestroyedCoreTakesNoHooks() {
        Core core = TestCores.core(listener, sink);
        core.destroy();

        assertInstanceOf(Answer.Destroyed.class, CoreInternals.installDeadlineHooks(core, new TestHooks(core)));
        assertInstanceOf(Answer.Destroyed.class, core.call(CoreCalls.nextDeadline()));
    }
}
