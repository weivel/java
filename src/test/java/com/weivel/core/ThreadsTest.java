package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The wrapper under several threads, each interleaving forced by gates. */
class ThreadsTest {
    private static final long GATE_SECONDS = 20;

    private final TestCores.RecordingSink sink = new TestCores.RecordingSink();
    private final TestCores.RecordingListener listener = new TestCores.RecordingListener();

    @Test
    @Timeout(60)
    void aCallFromAnotherThreadWhileADispatchRunsJoinsItsQueueAndDispatchesNothing() throws Exception {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "from-a")));
        ok(core.call(CoreCalls.watchService(HOST, "from-b")));
        CountDownLatch dispatching = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        core.addListener(event -> {
            if (first.getAndSet(false)) {
                dispatching.countDown();
                await(release);
            }
        });
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Thread> threadA = threads.submit(() -> {
                ok(core.call(CoreCalls.registerService(HOST, "from-a")));
                return Thread.currentThread();
            });
            assertTrue(dispatching.await(GATE_SECONDS, TimeUnit.SECONDS));
            Thread threadB = threads.submit(() -> {
                ok(core.call(CoreCalls.registerService(HOST, "from-b")));
                return Thread.currentThread();
            }).get(GATE_SECONDS, TimeUnit.SECONDS);
            assertEquals(1, listener.events().size());
            release.countDown();
            Thread dispatcher = threadA.get(GATE_SECONDS, TimeUnit.SECONDS);

            assertEquals(2, listener.events().size());
            listener.heard().forEach(heard -> assertSame(dispatcher, heard.thread()));
            assertFalse(listener.heard().stream().anyMatch(heard -> heard.thread() == threadB));
        } finally {
            release.countDown();
            threads.shutdownNow();
            core.destroy();
        }
    }

    @Test
    @Timeout(60)
    void noEventOfACallIsDispatchedBeforeTheTimerIsToldItsAnnouncement() throws Exception {
        Core core = TestCores.core(listener, sink);
        ok(core.call(CoreCalls.watchService(HOST, "running")));
        CountDownLatch dispatching = new CountDownLatch(1);
        CountDownLatch releaseListener = new CountDownLatch(1);
        CountDownLatch following = new CountDownLatch(1);
        CountDownLatch releaseFollow = new CountDownLatch(1);
        AtomicBoolean firstEvent = new AtomicBoolean(true);
        core.addListener(event -> {
            if (firstEvent.getAndSet(false)) {
                dispatching.countDown();
                await(releaseListener);
            }
        });
        CoreInternals.installDeadlineHooks(core, new DeadlineHooks() {
            @Override
            public long now() {
                return 0;
            }

            @Override
            public void follow(long at, boolean set) {
                following.countDown();
                await(releaseFollow);
            }
        });
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> threadA = threads.submit(() -> ok(core.call(CoreCalls.registerService(HOST, "running"))));
            assertTrue(dispatching.await(GATE_SECONDS, TimeUnit.SECONDS));
            Future<?> threadB = threads.submit(() ->
                    ok(core.call(CoreCalls.wantService(HOST, "timed", WantOptions.timed(0, 100)))));
            assertTrue(following.await(GATE_SECONDS, TimeUnit.SECONDS));
            releaseListener.countDown();
            threadA.get(GATE_SECONDS, TimeUnit.SECONDS);

            assertEquals(1, listener.events().size(), "an event fenced behind the timer was dispatched");
            releaseFollow.countDown();
            threadB.get(GATE_SECONDS, TimeUnit.SECONDS);
            assertEquals(2, listener.events().size());
            assertTrue(TestCores.last(listener.events()) instanceof CoreEvent.DeadlineEvent);
        } finally {
            releaseListener.countDown();
            releaseFollow.countDown();
            threads.shutdownNow();
            core.destroy();
        }
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void anAnnouncementDrainedWhileFollowRunsIsToldAgainBeforeAnyOfItsEventsIsHeard() throws Exception {
        Core core = TestCores.core(listener, sink);
        GatedFollow follow = new GatedFollow(core);
        List<Integer> returnedWhenHeard = Collections.synchronizedList(new ArrayList<>());
        core.addListener(event -> returnedWhenHeard.add(follow.returned.get()));
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> threadA = threads.submit(() ->
                    ok(core.call(CoreCalls.wantService(HOST, "from-a", WantOptions.timed(0, 100)))));
            assertTrue(follow.firstEntered.await(GATE_SECONDS, TimeUnit.SECONDS));
            threads.submit(() -> ok(core.call(CoreCalls.wantService(HOST, "from-b", WantOptions.timed(0, 50)))))
                    .get(GATE_SECONDS, TimeUnit.SECONDS);

            assertEquals(Collections.emptyList(), listener.events(), "an event was heard while follow still ran");
            follow.releaseFirst.countDown();
            threadA.get(GATE_SECONDS, TimeUnit.SECONDS);

            assertEquals(Arrays.asList(new DeadlineHooksTest.Follow(100, true), new DeadlineHooksTest.Follow(50, true)),
                    follow.follows());
            assertEquals(2, listener.events().size());
            assertEquals(Arrays.asList(2, 2), returnedWhenHeard);
            assertEquals(1, follow.mostAtOnce.get());
        } finally {
            follow.releaseFirst.countDown();
            threads.shutdownNow();
            core.destroy();
        }
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void twoCallsToFollowNeverOverlap() throws Exception {
        Core core = TestCores.core(listener, sink);
        GatedFollow follow = new GatedFollow(core);
        ExecutorService threads = Executors.newFixedThreadPool(3);
        try {
            Future<?> threadA = threads.submit(() ->
                    ok(core.call(CoreCalls.wantService(HOST, "from-a", WantOptions.timed(0, 100)))));
            assertTrue(follow.firstEntered.await(GATE_SECONDS, TimeUnit.SECONDS));
            threads.submit(() -> ok(core.call(CoreCalls.wantService(HOST, "from-b", WantOptions.timed(0, 50)))))
                    .get(GATE_SECONDS, TimeUnit.SECONDS);
            threads.submit(() -> ok(core.call(CoreCalls.wantService(HOST, "from-c", WantOptions.timed(0, 20)))))
                    .get(GATE_SECONDS, TimeUnit.SECONDS);

            assertEquals(1, follow.follows().size(), "another thread called follow while the first still ran");
            follow.releaseFirst.countDown();
            threadA.get(GATE_SECONDS, TimeUnit.SECONDS);

            assertEquals(1, follow.mostAtOnce.get());
            assertEquals(new DeadlineHooksTest.Follow(20, true), TestCores.last(follow.follows()));
        } finally {
            follow.releaseFirst.countDown();
            threads.shutdownNow();
            core.destroy();
        }
    }

    /** Deadline hooks whose first {@code follow} waits on a gate, counting calls at once and calls returned. */
    private static final class GatedFollow implements DeadlineHooks {
        final CountDownLatch firstEntered = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final AtomicInteger returned = new AtomicInteger();
        final AtomicInteger mostAtOnce = new AtomicInteger();
        private final AtomicInteger atOnce = new AtomicInteger();
        private final List<DeadlineHooksTest.Follow> follows = Collections.synchronizedList(new ArrayList<>());

        GatedFollow(Core core) {
            assertEquals(new Answer.Ok<Void>(null), CoreInternals.installDeadlineHooks(core, this));
        }

        @Override
        public long now() {
            return 0;
        }

        @Override
        public void follow(long at, boolean set) {
            mostAtOnce.accumulateAndGet(atOnce.incrementAndGet(), Math::max);
            follows.add(new DeadlineHooksTest.Follow(at, set));
            if (follows.size() == 1) {
                firstEntered.countDown();
                await(releaseFirst);
            }
            atOnce.decrementAndGet();
            returned.incrementAndGet();
        }

        List<DeadlineHooksTest.Follow> follows() {
            synchronized (follows) {
                return Collections.unmodifiableList(new ArrayList<>(follows));
            }
        }
    }

    @Test
    @Timeout(60)
    void nothingOfTheCallersRunsWhileTheLayerLockIsHeld() {
        List<Boolean> lockSeen = Collections.synchronizedList(new ArrayList<>());
        Core[] holder = new Core[1];
        EventCopyTest.FailingCopy incomplete = new EventCopyTest.FailingCopy(sequence -> true, sequence -> false);
        Core core = TestCores.core(TestCores.functions(), incomplete, event -> {
            lockSeen.add(holder[0].isLockHeld());
            throw new IllegalStateException("a listener failing so the sink is told");
        }, failure -> lockSeen.add(holder[0].isLockHeld()));
        holder[0] = core;
        DeadlineHooksTest.TestHooks hooks = DeadlineHooksTest.TestHooks.on(core);
        ok(core.call(CoreCalls.wantService(HOST, "timed", WantOptions.timed(0, 100))));
        hooks.clock = 100;
        CoreInternals.defer(core, () -> {
            lockSeen.add(holder[0].isLockHeld());
            return DeferredStep.Status.DONE;
        });

        ok(core.call(CoreCalls.registerService(HOST, "timed")));

        assertTrue(lockSeen.size() >= 5, lockSeen.toString());
        assertTrue(lockSeen.stream().noneMatch(Boolean::booleanValue), lockSeen.toString());
        assertTrue(hooks.lockSeen().size() >= 2);
        assertTrue(hooks.lockSeen().stream().noneMatch(Boolean::booleanValue));
        core.destroy();
    }

    @Test
    @Timeout(120)
    void manyThreadsCallingAtOnceHaveEveryEventDispatchedOnceInDrainedOrderByOneThreadAtATime()
            throws Exception {
        int threadCount = 8;
        int callsEach = 200;
        Core core = TestCores.core(listener, sink);
        for (int thread = 0; thread < threadCount; thread++) {
            ok(core.call(CoreCalls.watchService(HOST, "service-" + thread)));
        }
        AtomicInteger dispatchers = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        core.addListener(event -> {
            mostAtOnce.accumulateAndGet(dispatchers.incrementAndGet(), Math::max);
            Thread.yield();
            dispatchers.decrementAndGet();
        });
        CyclicBarrier start = new CyclicBarrier(threadCount);
        ExecutorService threads = Executors.newFixedThreadPool(threadCount);
        try {
            List<CompletableFuture<Void>> running = new ArrayList<>();
            for (int thread = 0; thread < threadCount; thread++) {
                String id = "service-" + thread;
                running.add(CompletableFuture.runAsync(() -> registerAndUnregister(core, start, id, callsEach),
                        threads));
            }
            CompletableFuture.allOf(running.toArray(new CompletableFuture[0])).get(100, TimeUnit.SECONDS);
            assertEquals(new Answer.Absent<Long>(), core.call(CoreCalls.getService(HOST, "settled")));
        } finally {
            threads.shutdownNow();
        }

        List<Long> sequences = listener.events().stream().map(CoreEvent::sequence).collect(Collectors.toList());
        long expected = (long) threadCount * callsEach * 3;
        assertEquals(LongStream.rangeClosed(1, expected).boxed().collect(Collectors.toList()), sequences);
        assertEquals(1, mostAtOnce.get());
        assertEquals(Collections.emptyList(), sink.failures());
        assertEachWatchAlternates();
        core.destroy();
    }

    private void assertEachWatchAlternates() {
        Map<Long, Integer> lastChange = new HashMap<>();
        for (CoreEvent.WatchEvent watch : listener.of(CoreEvent.WatchEvent.class)) {
            int previous = lastChange.getOrDefault(watch.watch(), CoreConstants.CHANGE_UNREGISTERED);
            assertTrue(previous != watch.change(), "a watch heard the same change twice in a row");
            lastChange.put(watch.watch(), watch.change());
        }
    }

    private static void registerAndUnregister(Core core, CyclicBarrier start, String id, int calls) {
        try {
            start.await(GATE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception interrupted) {
            throw new IllegalStateException(interrupted);
        }
        for (int call = 0; call < calls; call++) {
            long registration = ok(core.call(CoreCalls.registerService(HOST, id)));
            ok(core.call(CoreCalls.unregister(registration)));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(GATE_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("a gate was never opened");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /**
     * A refused call answers its own refusal, never another's: four threads make first-time refusals of distinct ids
     * while a listener makes nested refused calls from inside the dispatch.
     *
     * @implNote The wrapper keeps one record of the refusal a drain held, so a record not emptied by each drain would
     * hand a call the refusal an earlier call met.
     */
    @Test
    @Timeout(180)
    void eachRefusedCallAnswersItsOwnRefusalAcrossThreadsAndNestedCalls() throws Exception {
        List<String> wrong = Collections.synchronizedList(new ArrayList<String>());
        AtomicInteger inner = new AtomicInteger();
        Core[] core = new Core[1];
        long[] plugin = new long[1];
        CoreListener calling = event -> {
            if (event instanceof CoreEvent.ReachRefusedEvent && inner.get() < 2000) {
                String id = "inner-" + inner.incrementAndGet();
                refusedAsItsOwn(core[0].call(CoreCalls.getService(plugin[0], id)), id, wrong);
                refusedWithoutARefusal(core[0].call(CoreCalls.getService(plugin[0], id)), "inner " + id, wrong);
            }
        };
        core[0] = TestCores.core(calling, sink);
        plugin[0] = WrapperCallTest.openedPlugin(core[0], "reacher");
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> running = new ArrayList<>();
        for (int thread = 0; thread < 4; thread++) {
            String prefix = "t" + thread + "-";
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    return;
                }
                for (int index = 0; index < 3000; index++) {
                    String id = prefix + index;
                    refusedAsItsOwn(core[0].call(CoreCalls.getService(plugin[0], id)), id, wrong);
                    refusedWithoutARefusal(core[0].call(CoreCalls.getService(plugin[0], id)), id, wrong);
                }
            });
            worker.start();
            running.add(worker);
        }
        start.countDown();
        for (Thread worker : running) {
            worker.join();
        }
        core[0].destroy();

        assertTrue(inner.get() > 0, "no nested call was made");
        assertEquals(Collections.emptyList(), wrong.subList(0, Math.min(10, wrong.size())));
        assertEquals(Collections.emptyList(), sink.failures());
    }

    private static void refusedAsItsOwn(Answer<Long> answer, String id, List<String> wrong) {
        if (!(answer instanceof Answer.Refused)) {
            wrong.add(id + " answered " + answer);
            return;
        }
        Optional<Refusal> refusal = ((Answer.Refused<Long>) answer).refusal();
        if (!refusal.isPresent() || !refusal.get().subject().equals(Optional.of(id))) {
            wrong.add(id + " carried " + refusal);
        }
    }

    /** A repeated reach the core refused already is refused again without a refusal of its own. */
    private static void refusedWithoutARefusal(Answer<Long> answer, String what, List<String> wrong) {
        if (!(answer instanceof Answer.Refused)) {
            wrong.add("repeated " + what + " answered " + answer);
        } else if (((Answer.Refused<Long>) answer).refusal().isPresent()) {
            wrong.add("repeated " + what + " carried " + ((Answer.Refused<Long>) answer).refusal());
        }
    }
}
