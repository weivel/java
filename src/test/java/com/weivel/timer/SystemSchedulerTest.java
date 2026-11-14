package com.weivel.timer;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.CoreProbes.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Core;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreEvent;
import com.weivel.core.CoreProbes;
import com.weivel.core.Failure;
import com.weivel.core.WantOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** {@link Schedulers#system()} on its real executor. */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SystemSchedulerTest {
    private static final long WAIT_SECONDS = 20;

    @Test
    void aTimedWantTimesOutOnTheSystemScheduler() throws Exception {
        CompletableFuture<CoreEvent.WantSettledEvent> settled = new CompletableFuture<>();
        CompletableFuture<String> heardOn = new CompletableFuture<>();
        List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
        Core core = CoreProbes.core(event -> {
            if (event instanceof CoreEvent.WantSettledEvent) {
                CoreEvent.WantSettledEvent want = (CoreEvent.WantSettledEvent) event;
                heardOn.complete(Thread.currentThread().getName());
                settled.complete(want);
            }
        }, failures::add);
        try (SystemScheduler system = Schedulers.system()) {
            CoreTimer timer = ok(CoreTimer.start(core, system));
            long want = ok(core.call(CoreCalls.wantService(HOST, "never-registered",
                    WantOptions.timed(timer.time().getAsLong(), 50))));

            CoreEvent.WantSettledEvent heard = settled.get(WAIT_SECONDS, TimeUnit.SECONDS);

            assertEquals(want, heard.want());
            assertEquals(CoreConstants.REFUSAL_TIMED_OUT, heard.refusal().get().code());
            assertEquals("weivel-timer", heardOn.get());
            timer.destroy();
        } finally {
            core.destroy();
            closeOnceTheTimersDispatchEnds(core);
        }
        assertEquals(Collections.emptyList(), failures);
    }

    /** The timer's thread may still be leaving the dispatch that delivered the settlement. */
    private static void closeOnceTheTimersDispatchEnds(Core core) throws InterruptedException {
        long giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (true) {
            try {
                core.close();
                return;
            } catch (IllegalStateException stillDispatching) {
                if (System.nanoTime() > giveUp) {
                    throw stillDispatching;
                }
                Thread.sleep(10);
            }
        }
    }

    @Test
    void aCancelledTaskNeverRunsAndATaskThatRanCannotBeCancelled() throws Exception {
        try (SystemScheduler system = Schedulers.system()) {
            CountDownLatch ran = new CountDownLatch(1);
            List<String> late = Collections.synchronizedList(new ArrayList<>());

            Scheduler.Task cancelled = system.schedule(system.now() + 60_000, () -> late.add("ran"));
            Scheduler.Task due = system.schedule(system.now(), ran::countDown);

            assertTrue(cancelled.cancel());
            assertTrue(ran.await(WAIT_SECONDS, TimeUnit.SECONDS));
            assertFalse(due.cancel());
            assertEquals(Collections.emptyList(), late);
        }
    }

    @Test
    void aTaskWhoseCallbackHasBegunCannotBeCancelled() throws Exception {
        try (SystemScheduler system = Schedulers.system()) {
            CountDownLatch begun = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(1);
            Scheduler.Task running = system.schedule(system.now(), () -> {
                begun.countDown();
                awaitQuietly(finish);
            });
            assertTrue(begun.await(WAIT_SECONDS, TimeUnit.SECONDS));

            boolean cancelledWhileRunning = running.cancel();
            finish.countDown();

            assertFalse(cancelledWhileRunning);
        }
    }

    @Test
    void eachCallMakesASchedulerOfItsOwnWithAThreadOfItsOwn() throws Exception {
        try (SystemScheduler first = Schedulers.system(); SystemScheduler second = Schedulers.system()) {
            assertNotSame(first, second);
            assertNotSame(threadOf(first), threadOf(second));
        }
    }

    @Test
    void closingASchedulerEndsItsThreadAndLeavesAnotherRunning() throws Exception {
        try (SystemScheduler kept = Schedulers.system()) {
            SystemScheduler closed = Schedulers.system();
            Thread closedThread = threadOf(closed);
            threadOf(kept);

            closed.close();

            assertTrue(closed.awaitClosed(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)));
            closedThread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
            assertFalse(closedThread.isAlive());
            assertThrows(IllegalStateException.class, () -> closed.schedule(closed.now(), () -> { }));
            assertTrue(threadOf(kept).isAlive());
        }
    }

    @Test
    void aDeadlineFurtherThanALongCanCountIsWaitedForAsLongAsCanBe() {
        assertEquals(Long.MAX_VALUE, SystemScheduler.delayUntil(Long.MAX_VALUE, -10));
        assertEquals(0, SystemScheduler.delayUntil(Long.MIN_VALUE, 10));
        assertEquals(0, SystemScheduler.delayUntil(5, 10));
        assertEquals(30, SystemScheduler.delayUntil(40, 10));
    }

    private static Thread threadOf(Scheduler scheduler) throws Exception {
        CompletableFuture<Thread> thread = new CompletableFuture<>();
        scheduler.schedule(scheduler.now(), () -> thread.complete(Thread.currentThread()));
        return thread.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void closingLetsARunningCallbackFinishUninterruptedAndRunsNoQueuedOne() throws Exception {
        SystemScheduler system = Schedulers.system();
        CountDownLatch begun = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        List<String> happened = Collections.synchronizedList(new ArrayList<>());
        system.schedule(system.now(), () -> {
            begun.countDown();
            try {
                finish.await(WAIT_SECONDS, TimeUnit.SECONDS);
                happened.add(Thread.currentThread().isInterrupted() ? "interrupted" : "finished");
            } catch (InterruptedException interrupted) {
                happened.add("interrupted");
            }
        });
        system.schedule(system.now(), () -> happened.add("queued ran"));
        assertTrue(begun.await(WAIT_SECONDS, TimeUnit.SECONDS));

        system.close();
        finish.countDown();

        assertTrue(system.awaitClosed(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)));
        assertEquals(Collections.singletonList("finished"), happened);
    }
}
