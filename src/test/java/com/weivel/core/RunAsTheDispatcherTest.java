package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * A step run as the dispatcher: it runs at once on the calling thread, behind whatever is queued unfenced, and is
 * refused, queuing nothing, only while a dispatch runs or fenced events wait.
 *
 * @implNote A {@link StackOverflowError} thrown from a call's answered step is rethrown past the call's leave, which is
 * how a test leaves drained events undispatched and an announcement unfollowed with nobody dispatching or following;
 * an event copy that drops every event leaves that announcement with nothing queued behind it.
 */
class RunAsTheDispatcherTest {
    private static final long GATE_SECONDS = 10;

    private final TestCores.RecordingSink sink = new TestCores.RecordingSink();
    private final List<String> order = Collections.synchronizedList(new ArrayList<String>());

    @Test
    @Timeout(30)
    void aStepRunsOnceOnTheCallingThreadThoughAnAnnouncementIsLeftUnfollowed() throws Exception {
        Core core = TestCores.core(TestCores.functions(),
                new EventCopyTest.FailingCopy(sequence -> true, sequence -> true), event -> { }, sink);
        GatedHooks hooks = new GatedHooks();
        ok(CoreInternals.installDeadlineHooks(core, hooks));
        assertThrows(StackOverflowError.class, () -> CoreInternals.call(core,
                CoreCalls.wantService(HOST, "absent", WantOptions.timed(0, 100)), overflowing()));
        hooks.gating.set(true);
        List<Thread> ranOn = Collections.synchronizedList(new ArrayList<Thread>());
        boolean[] answered = new boolean[1];
        CountDownLatch done = new CountDownLatch(1);
        Thread asker = new Thread(() -> {
            answered[0] = CoreInternals.runAsTheDispatcher(core, () -> {
                ranOn.add(Thread.currentThread());
                return DeferredStep.Status.DONE;
            });
            done.countDown();
        });
        asker.start();
        if (awaitEither(hooks.entered, done)) {
            core.call(CoreCalls.wantService(HOST, "other", WantOptions.unbounded()));
        }
        hooks.released.countDown();
        assertTrue(done.await(GATE_SECONDS, TimeUnit.SECONDS));

        assertEquals(Collections.singletonList(asker), ranOn);
        assertTrue(answered[0]);
        core.destroy();
    }

    @Test
    void aStepRunsBehindEventsLeftQueuedWithNobodyDispatching() {
        Core core = TestCores.core(event -> order.add("heard " + event.getClass().getSimpleName()), sink);
        ok(core.call(CoreCalls.wantService(HOST, "late", WantOptions.unbounded())));
        assertThrows(StackOverflowError.class,
                () -> CoreInternals.call(core, CoreCalls.registerService(HOST, "late"), overflowing()));

        boolean ran = CoreInternals.runAsTheDispatcher(core, () -> {
            order.add("step");
            return DeferredStep.Status.DONE;
        });

        assertTrue(ran);
        assertTrue(order.size() >= 2 && order.get(0).startsWith("heard "), order.toString());
        assertEquals("step", order.get(order.size() - 1));
        core.destroy();
    }

    @Test
    void aStepIsRefusedAndNotQueuedWhileADispatchRuns() {
        List<Boolean> answers = new ArrayList<>();
        AtomicBoolean ranInside = new AtomicBoolean();
        Core core = TestCores.core(event -> answers.add(CoreInternals.runAsTheDispatcher(TestCoresHolder.core,
                () -> {
                    ranInside.set(true);
                    return DeferredStep.Status.DONE;
                })), sink);
        TestCoresHolder.core = core;
        ok(core.call(CoreCalls.wantService(HOST, "served", WantOptions.unbounded())));
        ok(core.call(CoreCalls.registerService(HOST, "served")));

        assertTrue(!answers.isEmpty() && !answers.contains(true), answers.toString());
        assertEquals(false, ranInside.get());
        core.destroy();
    }

    @Test
    void aStepIsRefusedAndNotQueuedWhileAnEventWaitsBehindAnUnfollowedDeadline() {
        Core core = TestCores.core(event -> order.add("heard"), sink);
        ok(CoreInternals.installDeadlineHooks(core, new GatedHooks()));
        assertThrows(StackOverflowError.class, () -> CoreInternals.call(core,
                CoreCalls.wantService(HOST, "absent", WantOptions.timed(0, 100)), overflowing()));
        AtomicBoolean ran = new AtomicBoolean();

        boolean answered = CoreInternals.runAsTheDispatcher(core, () -> {
            ran.set(true);
            return DeferredStep.Status.DONE;
        });

        assertEquals(false, answered);
        assertEquals(false, ran.get());
        assertEquals(Collections.<String>emptyList(), order);
        core.destroy();
    }

    private static <T> CallSteps<T> overflowing() {
        return new CallSteps<T>(() -> Optional.<Answer<T>>empty(), value -> {
            throw new StackOverflowError("thrown by the test after the drain");
        });
    }

    /** Waits until one of the latches opens, answering whether the first did. */
    private static boolean awaitEither(CountDownLatch first, CountDownLatch second) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(GATE_SECONDS);
        while (System.nanoTime() < until) {
            if (first.await(10, TimeUnit.MILLISECONDS)) {
                return true;
            }
            if (second.getCount() == 0) {
                return false;
            }
        }
        throw new AssertionError("neither the follow nor the step was reached: " + Arrays.asList(first, second));
    }

    /** The core a listener reaches before its constructor has answered it. */
    private static final class TestCoresHolder {
        static volatile Core core;
    }

    /** Hooks whose follow, once gated, waits on a gate the test opens. */
    private static final class GatedHooks implements DeadlineHooks {
        final AtomicBoolean gating = new AtomicBoolean();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);

        @Override
        public long now() {
            return 0;
        }

        @Override
        public void follow(long at, boolean set) {
            if (!gating.get()) {
                return;
            }
            entered.countDown();
            try {
                released.await(GATE_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
