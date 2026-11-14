package com.weivel.timer;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.CoreProbes.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.Core;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreEvent;
import com.weivel.core.CoreProbes;
import com.weivel.core.Failure;
import com.weivel.core.Refusal;
import com.weivel.core.WantOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The timer, each rule of the contract's {@code timer} section, on a clock and scheduler the test owns. */
class TimerTest {
    private static final String ADVANCE = "weivel_advance";
    private static final String NEXT_DEADLINE = "weivel_next_deadline";

    private final List<CoreEvent> heard = Collections.synchronizedList(new ArrayList<>());
    private final List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
    private final CoreProbes.Crossings crossings = new CoreProbes.Crossings();
    private final Core core = CoreProbes.counted(crossings, heard::add, failures::add);
    private final TestScheduler scheduler = new TestScheduler();

    @AfterEach
    void destroy() {
        core.destroy();
        core.close();
        assertEquals(List.of(), failures);
    }

    @Test
    void theTimerIsArmedFromTheLatestAnnouncementReplacedAndDisarmedByLaterOnes() {
        CoreTimer timer = started(scheduler);
        long later = ok(core.call(CoreCalls.wantService(HOST, "later", WantOptions.timed(0, 100))));
        assertEquals(OptionalLong.of(100), timer.armed());
        assertEquals(List.of(100L), scheduler.live());

        long earlier = ok(core.call(CoreCalls.wantService(HOST, "earlier", WantOptions.timed(0, 50))));
        assertEquals(OptionalLong.of(50), timer.armed());
        assertEquals(List.of(50L), scheduler.live());

        ok(core.call(CoreCalls.registerService(HOST, "unrelated")));
        assertEquals(List.of(100L, 50L), scheduler.scheduled());

        ok(core.call(CoreCalls.unregister(earlier)));
        assertEquals(OptionalLong.of(100), timer.armed());
        ok(core.call(CoreCalls.unregister(later)));

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(List.of(), scheduler.live());
        assertEquals(List.of(100L, 50L, 100L), scheduler.scheduled());
    }

    @Test
    void theTimerIsArmedBeforeAnyEventOfTheCallIsDispatched() {
        CoreTimer timer = started(scheduler);
        List<Object> armedWhenHeard = new ArrayList<>();
        core.addListener(event -> {
            if (event instanceof CoreEvent.DeadlineEvent deadline) {
                armedWhenHeard.add(List.of(deadline.at(), timer.armed(), scheduler.live()));
            }
        });

        ok(core.call(CoreCalls.wantService(HOST, "announced", WantOptions.timed(0, 70))));

        assertEquals(List.of(List.of(70L, OptionalLong.of(70), List.of(70L))), armedWhenHeard);
    }

    @Test
    void whenTheTimerFiresItHandsTheCoreTheTimeAndItsEventsRearmIt() {
        CoreTimer timer = started(scheduler);
        long first = ok(core.call(CoreCalls.wantService(HOST, "first", WantOptions.timed(0, 100))));
        ok(core.call(CoreCalls.wantService(HOST, "second", WantOptions.timed(0, 200))));

        scheduler.moveTo(100);

        assertEquals(List.of(100L), crossings.advanceTimes());
        assertEquals(List.of(first), timedOut());
        assertEquals(OptionalLong.of(200), timer.armed());
        assertEquals(List.of(200L), scheduler.live());

        scheduler.moveTo(200);

        assertEquals(List.of(100L, 200L), crossings.advanceTimes());
        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(List.of(), scheduler.live());
    }

    @Test
    void itAdvancesOnlyOnTheTimeTheClockAnswered() {
        started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "late", WantOptions.timed(0, 100))));

        scheduler.setClock(137);
        scheduler.moveTo(137);

        assertEquals(List.of(137L), crossings.advanceTimes());
        assertEquals(1, timedOut().size());
    }

    @Test
    void aSchedulerThatCannotWaitSoLongWaitsAsLongAsItCanAndNeverAdvancesEarly() {
        scheduler.limitWaitsTo(40);
        CoreTimer timer = started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "far", WantOptions.timed(0, 100))));
        assertEquals(List.of(40L), scheduler.live());

        scheduler.moveTo(40);
        scheduler.moveTo(80);
        scheduler.moveTo(99);

        assertEquals(List.of(), crossings.advanceTimes());
        assertEquals(List.of(40L, 80L, 100L), scheduler.scheduled());
        assertEquals(OptionalLong.of(100), timer.armed());

        scheduler.moveTo(100);

        assertEquals(List.of(100L), crossings.advanceTimes());
        assertEquals(1, timedOut().size());
    }

    @Test
    void theWrapperAdvancesFirstWhenTheClockReachedTheDeadlineBeforeTheTimerRan() {
        started(scheduler);
        long want = ok(core.call(CoreCalls.wantService(HOST, "raced", WantOptions.timed(0, 100))));
        scheduler.setClock(100);
        int readsBefore = scheduler.reads();

        ok(core.call(CoreCalls.registerService(HOST, "raced")));

        assertEquals(List.of(want), timedOut());
        assertEquals(List.of(), served());
        assertEquals(List.of(100L), crossings.advanceTimes());
        assertEquals(1, scheduler.reads() - readsBefore);
        assertEquals(List.of(), scheduler.live());
        assertEquals(List.of(), scheduler.fired());
    }

    @Test
    void theClockIsReadOnlyByTheWrappersCheckAndTheCallback() {
        started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "counted", WantOptions.timed(0, 100))));
        assertEquals(0, scheduler.reads());

        assertInstanceOf(Answer.Absent.class, core.call(CoreCalls.getService(HOST, "counted")));
        assertEquals(1, scheduler.reads());

        scheduler.moveTo(100);
        assertEquals(2, scheduler.reads());

        ok(core.call(CoreCalls.registerService(HOST, "counted")));
        assertEquals(2, scheduler.reads());
    }

    @Test
    void aBoundedSchedulerIsReadOnceAsEachWaitIsScheduled() {
        scheduler.limitWaitsTo(40);
        started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "stepped", WantOptions.timed(0, 100))));
        assertEquals(1, scheduler.reads());

        scheduler.moveTo(40);

        assertEquals(2, scheduler.reads());
        assertEquals(List.of(40L, 80L), scheduler.scheduled());
    }

    @Test
    void withNoDeadlineThereIsNoTimer() {
        CoreTimer timer = started(scheduler);

        ok(core.call(CoreCalls.wantService(HOST, "unbounded", WantOptions.unbounded())));
        ok(core.call(CoreCalls.registerService(HOST, "other")));

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(List.of(), scheduler.scheduled());
        assertEquals(0, scheduler.reads());
    }

    @Test
    void itNeverAsksTheCoreForItsDeadline() {
        scheduler.limitWaitsTo(30);
        CoreTimer timer = started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "polled", WantOptions.timed(0, 100))));
        scheduler.moveTo(60);
        ok(core.call(CoreCalls.registerService(HOST, "other")));
        scheduler.moveTo(100);
        timer.destroy();

        assertEquals(0, crossings.count(NEXT_DEADLINE));
        assertEquals(1, crossings.count(ADVANCE));
    }

    @Test
    void destroyCancelsTheOneTaskAndAStaleCallbackDoesNothing() {
        CoreTimer timer = started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "abandoned", WantOptions.timed(0, 100))));
        TestScheduler.TestTask task = scheduler.last();

        timer.destroy();

        assertTrue(task.cancelled());
        assertEquals(OptionalLong.empty(), timer.armed());
        scheduler.setClock(200);
        int readsBefore = scheduler.reads();
        task.runAnyway();
        ok(core.call(CoreCalls.registerService(HOST, "after")));

        assertEquals(readsBefore, scheduler.reads());
        assertEquals(List.of(), crossings.advanceTimes());
        assertEquals(List.of(), timedOut());
    }

    @Test
    void aTimerOnNoSchedulerArmsNothingAndReadsNoClock() {
        CoreTimer timer = started(Scheduler.none());

        ok(core.call(CoreCalls.wantService(HOST, "untimed", WantOptions.timed(0, 100))));
        ok(core.call(CoreCalls.registerService(HOST, "other")));

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(OptionalLong.empty(), timer.time());
        assertEquals(List.of(), crossings.advanceTimes());
    }

    @Test
    void theTimeIsTheSchedulersClock() {
        CoreTimer timer = started(scheduler);
        scheduler.setClock(42);

        assertEquals(OptionalLong.of(42), timer.time());
    }

    @Test
    void aTimedWantArmsTheTimerAtItsDeadline() {
        CoreTimer timer = started(scheduler);
        long want = ok(core.call(CoreCalls.wantService(HOST, "accounts", WantOptions.timed(0, 1000))));
        assertEquals(OptionalLong.of(1000), timer.armed());

        scheduler.moveTo(10_000);

        assertEquals(List.of(want), timedOut());
        assertTimedOutAbout("accounts");
        assertEquals(List.of(1000L), crossings.advanceTimes());
        assertEquals(List.of(1000L), scheduler.fired());
        assertEquals(OptionalLong.empty(), timer.armed());
    }

    @Test
    void aSettledWantDisarmsTheTimer() {
        CoreTimer timer = started(scheduler);
        long want = ok(core.call(CoreCalls.wantService(HOST, "accounts", WantOptions.timed(0, 1000))));
        scheduler.moveTo(500);

        ok(core.call(CoreCalls.registerService(HOST, "accounts")));
        assertEquals(OptionalLong.empty(), timer.armed());
        scheduler.moveTo(10_000);

        assertEquals(List.of(want), served());
        assertEquals(List.of(), timedOut());
        assertEquals(List.of(), scheduler.fired());
        assertEquals(List.of(), scheduler.live());
    }

    @Test
    void aRegistrationAtTheDeadlineLoses() {
        started(scheduler);
        long want = ok(core.call(CoreCalls.wantService(HOST, "accounts", WantOptions.timed(0, 1000))));

        scheduler.moveTo(1000);
        ok(core.call(CoreCalls.registerService(HOST, "accounts")));
        scheduler.moveTo(10_000);

        assertEquals(List.of(want), timedOut());
        assertEquals(List.of(), served());
        assertTimedOutAbout("accounts");
        assertEquals(List.of(1000L), crossings.advanceTimes());
    }

    @Test
    void aRegistrationPastAnUnfiredDeadlineLoses() {
        started(scheduler);
        long want = ok(core.call(CoreCalls.wantService(HOST, "accounts", WantOptions.timed(0, 1000))));

        scheduler.setClock(1500);
        ok(core.call(CoreCalls.registerService(HOST, "accounts")));
        scheduler.moveTo(10_000);

        assertEquals(List.of(want), timedOut());
        assertEquals(List.of(), served());
        assertTimedOutAbout("accounts");
        assertEquals(List.of(1500L), crossings.advanceTimes());
        assertEquals(List.of(), scheduler.fired());
    }

    private CoreTimer started(Scheduler chosen) {
        return ok(CoreTimer.start(core, chosen));
    }

    private List<CoreEvent.WantSettledEvent> settlements() {
        synchronized (heard) {
            return heard.stream().filter(CoreEvent.WantSettledEvent.class::isInstance)
                    .map(CoreEvent.WantSettledEvent.class::cast).toList();
        }
    }

    private List<Long> timedOut() {
        return settlements().stream()
                .filter(settled -> settled.refusal().map(Refusal::code).orElse(0) == CoreConstants.REFUSAL_TIMED_OUT)
                .map(CoreEvent.WantSettledEvent::want).toList();
    }

    private List<Long> served() {
        return settlements().stream().filter(settled -> settled.refusal().isEmpty())
                .map(CoreEvent.WantSettledEvent::want).toList();
    }

    private void assertTimedOutAbout(String serviceId) {
        Refusal refusal = settlements().getFirst().refusal().orElseThrow();
        assertEquals(CoreConstants.REFUSAL_TIMED_OUT, refusal.code());
        assertEquals(Optional.of(serviceId), refusal.subject());
        assertTrue(refusal.hasFix());
        assertFalse(refusal.fix().isEmpty());
    }
}
