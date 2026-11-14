package com.weivel.core;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.TestCores.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
        CoreInternals.setDeadlineHooks(core, new DeadlineHooks() {
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
            assertTrue(listener.events().getLast() instanceof CoreEvent.DeadlineEvent);
        } finally {
            releaseListener.countDown();
            releaseFollow.countDown();
            threads.shutdownNow();
            core.destroy();
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
            Thread.onSpinWait();
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
            CompletableFuture.allOf(running.toArray(CompletableFuture[]::new)).get(100, TimeUnit.SECONDS);
            assertEquals(new Answer.Absent<Long>(), core.call(CoreCalls.getService(HOST, "settled")));
        } finally {
            threads.shutdownNow();
        }

        List<Long> sequences = listener.events().stream().map(CoreEvent::sequence).toList();
        long expected = (long) threadCount * callsEach * 3;
        assertEquals(LongStream.rangeClosed(1, expected).boxed().toList(), sequences);
        assertEquals(1, mostAtOnce.get());
        assertEquals(List.of(), sink.failures());
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
}
