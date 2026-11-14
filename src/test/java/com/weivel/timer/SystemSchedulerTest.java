package com.weivel.timer;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.CoreProbes.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
class SystemSchedulerTest {
    private static final long WAIT_SECONDS = 20;

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
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
        try {
            Scheduler system = Schedulers.system();
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
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aCancelledTaskNeverRunsAndATaskThatRanCannotBeCancelled() throws Exception {
        Scheduler system = Schedulers.system();
        CountDownLatch ran = new CountDownLatch(1);
        List<String> late = Collections.synchronizedList(new ArrayList<>());

        Scheduler.Task cancelled = system.schedule(system.now() + 60_000, () -> late.add("ran"));
        Scheduler.Task due = system.schedule(system.now(), ran::countDown);

        assertTrue(cancelled.cancel());
        assertTrue(ran.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertFalse(due.cancel());
        assertEquals(Collections.emptyList(), late);
        assertSame(system, Schedulers.system());
    }
}
