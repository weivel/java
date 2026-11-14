package com.weivel.timer;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.CoreProbes.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Core;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreProbes;
import com.weivel.core.Failure;
import com.weivel.core.WantOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The timer under several threads, each interleaving forced by gates. */
class TimerThreadsTest {
    private static final long GATE_SECONDS = 20;

    private final List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
    private final CoreProbes.Crossings crossings = new CoreProbes.Crossings();
    private final Core core = CoreProbes.counted(crossings, event -> { }, failures::add);
    private final TestScheduler scheduler = new TestScheduler();
    private final ExecutorService threads = Executors.newSingleThreadExecutor();
    private final CountDownLatch inside = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void destroy() {
        release.countDown();
        threads.shutdownNow();
        core.destroy();
        core.close();
        assertEquals(List.of(), failures);
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void theTimerEndsArmedForTheLatestAnnouncementDrainedWhicheverThreadDrainedIt() throws Exception {
        CoreTimer timer = ok(CoreTimer.start(core, scheduler));
        AtomicBoolean first = new AtomicBoolean(true);
        scheduler.whenScheduled(at -> {
            if (first.getAndSet(false)) {
                gate();
            }
        });

        Future<?> earlier = threads.submit(() ->
                ok(core.call(CoreCalls.wantService(HOST, "from-a", WantOptions.timed(0, 100)))));
        assertTrue(inside.await(GATE_SECONDS, TimeUnit.SECONDS));
        ok(core.call(CoreCalls.wantService(HOST, "from-b", WantOptions.timed(0, 50))));
        release.countDown();
        earlier.get(GATE_SECONDS, TimeUnit.SECONDS);

        assertEquals(OptionalLong.of(50), timer.armed());
        assertEquals(List.of(50L), scheduler.live());
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aCallbackRacingARearmOnAnotherThreadLeavesTheNewArm() throws Exception {
        CoreTimer timer = ok(CoreTimer.start(core, scheduler));
        ok(core.call(CoreCalls.wantService(HOST, "racing", WantOptions.timed(0, 100))));
        Future<?> firing = fireBlockedInItsClockRead();

        ok(core.call(CoreCalls.wantService(HOST, "rearmed", WantOptions.timed(100, 50))));
        release.countDown();
        firing.get(GATE_SECONDS, TimeUnit.SECONDS);

        assertEquals(OptionalLong.of(150), timer.armed());
        assertEquals(List.of(150L), scheduler.live());
        assertEquals(List.of(100L), crossings.advanceTimes());
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aCallbackRacingADestroyOnAnotherThreadDoesNothing() throws Exception {
        CoreTimer timer = ok(CoreTimer.start(core, scheduler));
        ok(core.call(CoreCalls.wantService(HOST, "destroyed", WantOptions.timed(0, 100))));
        Future<?> firing = fireBlockedInItsClockRead();

        timer.destroy();
        release.countDown();
        firing.get(GATE_SECONDS, TimeUnit.SECONDS);

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(List.of(), crossings.advanceTimes());
    }

    /** Runs the task due at 100 on the other thread, holding it inside the clock read its callback makes. */
    private Future<?> fireBlockedInItsClockRead() throws InterruptedException {
        AtomicBoolean firstRead = new AtomicBoolean(true);
        Thread testThread = Thread.currentThread();
        scheduler.whenRead(() -> {
            if (Thread.currentThread() != testThread && firstRead.getAndSet(false)) {
                gate();
            }
        });
        Future<?> firing = threads.submit(() -> scheduler.moveTo(100));
        assertTrue(inside.await(GATE_SECONDS, TimeUnit.SECONDS));
        return firing;
    }

    private void gate() {
        inside.countDown();
        try {
            if (!release.await(GATE_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("a gate was never opened");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
