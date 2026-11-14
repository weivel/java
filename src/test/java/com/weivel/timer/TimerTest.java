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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The timer, each rule of the contract's {@code timer} section, on a clock and scheduler the test owns. */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
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
        assertEquals(Collections.emptyList(), failures);
    }

    @Test
    void theTimerIsArmedFromTheLatestAnnouncementReplacedAndDisarmedByLaterOnes() {
        CoreTimer timer = started(scheduler);
        long later = ok(core.call(CoreCalls.wantService(HOST, "later", WantOptions.timed(0, 100))));
        assertEquals(OptionalLong.of(100), timer.armed());
        assertEquals(Collections.singletonList(100L), scheduler.live());

        long earlier = ok(core.call(CoreCalls.wantService(HOST, "earlier", WantOptions.timed(0, 50))));
        assertEquals(OptionalLong.of(50), timer.armed());
        assertEquals(Collections.singletonList(50L), scheduler.live());

        ok(core.call(CoreCalls.registerService(HOST, "unrelated")));
        assertEquals(Arrays.asList(100L, 50L), scheduler.scheduled());

        ok(core.call(CoreCalls.unregister(earlier)));
        assertEquals(OptionalLong.of(100), timer.armed());
        ok(core.call(CoreCalls.unregister(later)));

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(Collections.emptyList(), scheduler.live());
        assertEquals(Arrays.asList(100L, 50L, 100L), scheduler.scheduled());
    }

    @Test
    void theTimerIsArmedBeforeAnyEventOfTheCallIsDispatched() {
        CoreTimer timer = started(scheduler);
        List<Object> armedWhenHeard = new ArrayList<>();
        core.addListener(event -> {
            if (event instanceof CoreEvent.DeadlineEvent) {
                CoreEvent.DeadlineEvent deadline = (CoreEvent.DeadlineEvent) event;
                armedWhenHeard.add(Arrays.asList(deadline.at(), timer.armed(), scheduler.live()));
            }
        });

        ok(core.call(CoreCalls.wantService(HOST, "announced", WantOptions.timed(0, 70))));

        assertEquals(Collections.singletonList(Arrays.asList(70L, OptionalLong.of(70),
                Collections.singletonList(70L))), armedWhenHeard);
    }

    @Test
    void whenTheTimerFiresItHandsTheCoreTheTimeAndItsEventsRearmIt() {
        CoreTimer timer = started(scheduler);
        long first = ok(core.call(CoreCalls.wantService(HOST, "first", WantOptions.timed(0, 100))));
        ok(core.call(CoreCalls.wantService(HOST, "second", WantOptions.timed(0, 200))));

        scheduler.moveTo(100);

        assertEquals(Collections.singletonList(100L), crossings.advanceTimes());
        assertEquals(Collections.singletonList(first), timedOut());
        assertEquals(OptionalLong.of(200), timer.armed());
        assertEquals(Collections.singletonList(200L), scheduler.live());

        scheduler.moveTo(200);

        assertEquals(Arrays.asList(100L, 200L), crossings.advanceTimes());
        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(Collections.emptyList(), scheduler.live());
    }

    @Test
    void itAdvancesOnlyOnTheTimeTheClockAnswered() {
        started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "late", WantOptions.timed(0, 100))));

        scheduler.setClock(137);
        scheduler.moveTo(137);

        assertEquals(Collections.singletonList(137L), crossings.advanceTimes());
        assertEquals(1, timedOut().size());
    }

    @Test
    void aSchedulerThatCannotWaitSoLongWaitsAsLongAsItCanAndNeverAdvancesEarly() {
        scheduler.limitWaitsTo(40);
        CoreTimer timer = started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "far", WantOptions.timed(0, 100))));
        assertEquals(Collections.singletonList(40L), scheduler.live());

        scheduler.moveTo(40);
        scheduler.moveTo(80);
        scheduler.moveTo(99);

        assertEquals(Collections.emptyList(), crossings.advanceTimes());
        assertEquals(Arrays.asList(40L, 80L, 100L), scheduler.scheduled());
        assertEquals(OptionalLong.of(100), timer.armed());

        scheduler.moveTo(100);

        assertEquals(Collections.singletonList(100L), crossings.advanceTimes());
        assertEquals(1, timedOut().size());
    }

    @Test
    void theWrapperAdvancesFirstWhenTheClockReachedTheDeadlineBeforeTheTimerRan() {
        started(scheduler);
        long want = ok(core.call(CoreCalls.wantService(HOST, "raced", WantOptions.timed(0, 100))));
        scheduler.setClock(100);
        int readsBefore = scheduler.reads();

        ok(core.call(CoreCalls.registerService(HOST, "raced")));

        assertEquals(Collections.singletonList(want), timedOut());
        assertEquals(Collections.emptyList(), served());
        assertEquals(Collections.singletonList(100L), crossings.advanceTimes());
        assertEquals(1, scheduler.reads() - readsBefore);
        assertEquals(Collections.emptyList(), scheduler.live());
        assertEquals(Collections.emptyList(), scheduler.fired());
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
        assertEquals(Arrays.asList(40L, 80L), scheduler.scheduled());
    }

    @Test
    void withNoDeadlineThereIsNoTimer() {
        CoreTimer timer = started(scheduler);

        ok(core.call(CoreCalls.wantService(HOST, "unbounded", WantOptions.unbounded())));
        ok(core.call(CoreCalls.registerService(HOST, "other")));

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(Collections.emptyList(), scheduler.scheduled());
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
        assertEquals(Collections.emptyList(), crossings.advanceTimes());
        assertEquals(Collections.emptyList(), timedOut());
    }

    @Test
    void aSecondTimerOnOneCoreIsTheCallersMistakeAndTheFirstKeepsFollowing() {
        CoreTimer first = started(scheduler);
        TestScheduler other = new TestScheduler();

        Answer<CoreTimer> second = CoreTimer.start(core, other);

        assertInstanceOf(Answer.Mistake.class, second);
        ok(core.call(CoreCalls.wantService(HOST, "followed", WantOptions.timed(0, 100))));
        assertEquals(OptionalLong.of(100), first.armed());
        assertEquals(Collections.singletonList(100L), scheduler.live());
        assertEquals(Collections.emptyList(), other.scheduled());
    }

    @Test
    void aDestroyedTimerTakesItsHooksOffSoAnotherMayFollow() {
        started(scheduler).destroy();
        TestScheduler other = new TestScheduler();

        CoreTimer second = started(other);
        ok(core.call(CoreCalls.wantService(HOST, "followed", WantOptions.timed(0, 100))));

        assertEquals(OptionalLong.of(100), second.armed());
        assertEquals(Collections.singletonList(100L), other.live());
        assertEquals(Collections.emptyList(), scheduler.scheduled());
    }

    @Test
    void aTimerOnNoSchedulerArmsNothingAndReadsNoClock() {
        CoreTimer timer = started(Scheduler.none());

        ok(core.call(CoreCalls.wantService(HOST, "untimed", WantOptions.timed(0, 100))));
        ok(core.call(CoreCalls.registerService(HOST, "other")));

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(OptionalLong.empty(), timer.time());
        assertEquals(Collections.emptyList(), crossings.advanceTimes());
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
        List<List<Long>> moves = movesOf(timer);
        long want = ok(core.call(CoreCalls.wantService(HOST, "accounts", WantOptions.timed(0, 1000))));
        assertEquals(OptionalLong.of(1000), timer.armed());

        scheduler.moveTo(10_000);

        assertEquals(Collections.singletonList(want), timedOut());
        assertTimedOutAbout("accounts");
        assertEquals(Collections.singletonList(1000L), crossings.advanceTimes());
        assertEquals(Collections.singletonList(1000L), scheduler.fired());
        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(Arrays.asList(Arrays.asList(0L, 1000L), Arrays.asList(1000L, null)), moves);
    }

    @Test
    void aSettledWantDisarmsTheTimer() {
        CoreTimer timer = started(scheduler);
        List<List<Long>> moves = movesOf(timer);
        long want = ok(core.call(CoreCalls.wantService(HOST, "accounts", WantOptions.timed(0, 1000))));
        scheduler.moveTo(500);

        ok(core.call(CoreCalls.registerService(HOST, "accounts")));
        assertEquals(OptionalLong.empty(), timer.armed());
        scheduler.moveTo(10_000);

        assertEquals(Collections.singletonList(want), served());
        assertEquals(Collections.emptyList(), timedOut());
        assertEquals(Collections.emptyList(), scheduler.fired());
        assertEquals(Collections.emptyList(), scheduler.live());
        assertEquals(Arrays.asList(Arrays.asList(0L, 1000L), Arrays.asList(500L, null)), moves);
    }

    @Test
    void aRegistrationAtTheDeadlineLoses() {
        started(scheduler);
        long want = ok(core.call(CoreCalls.wantService(HOST, "accounts", WantOptions.timed(0, 1000))));

        scheduler.moveTo(1000);
        ok(core.call(CoreCalls.registerService(HOST, "accounts")));
        scheduler.moveTo(10_000);

        assertEquals(Collections.singletonList(want), timedOut());
        assertEquals(Collections.emptyList(), served());
        assertTimedOutAbout("accounts");
        assertEquals(Collections.singletonList(1000L), crossings.advanceTimes());
    }

    @Test
    void aRegistrationPastAnUnfiredDeadlineLoses() {
        List<List<Long>> moves = movesOf(started(scheduler));
        long want = ok(core.call(CoreCalls.wantService(HOST, "accounts", WantOptions.timed(0, 1000))));

        scheduler.setClock(1500);
        ok(core.call(CoreCalls.registerService(HOST, "accounts")));
        scheduler.moveTo(10_000);

        assertEquals(Collections.singletonList(want), timedOut());
        assertEquals(Collections.emptyList(), served());
        assertTimedOutAbout("accounts");
        assertEquals(Collections.singletonList(1500L), crossings.advanceTimes());
        assertEquals(Collections.emptyList(), scheduler.fired());
        assertEquals(Arrays.asList(Arrays.asList(0L, 1000L), Arrays.asList(1500L, null)), moves);
    }

    @Test
    void aSchedulerThatCannotWaitAtAllIsTheCallersMistake() {
        scheduler.limitWaitsTo(0);

        assertInstanceOf(Answer.Mistake.class, CoreTimer.start(core, scheduler));
        TestScheduler other = new TestScheduler();
        CoreTimer followed = started(other);
        ok(core.call(CoreCalls.wantService(HOST, "followed", WantOptions.timed(0, 100))));
        assertEquals(OptionalLong.of(100), followed.armed());
        assertEquals(Collections.emptyList(), scheduler.scheduled());
    }

    @Test
    void anAdvanceTheCoreDoesNotTakeLeavesTheTimerDisarmed() {
        CoreTimer timer = started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "abandoned", WantOptions.timed(0, 100))));
        core.destroy();

        scheduler.moveTo(100);

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(Collections.emptyList(), scheduler.live());
    }

    @Test
    void aTaskScheduledWhileANewerDeadlineArrivesIsCancelledNotKept() {
        scheduler.limitWaitsTo(40);
        CoreTimer timer = started(scheduler);
        ok(core.call(CoreCalls.wantService(HOST, "far", WantOptions.timed(0, 100))));
        scheduler.whenScheduled(at -> {
            if (at == 80) {
                ok(core.call(CoreCalls.wantService(HOST, "sooner", WantOptions.timed(0, 60))));
            }
        });

        scheduler.moveTo(40);

        assertEquals(OptionalLong.of(60), timer.armed());
        assertEquals(Collections.singletonList(60L), scheduler.live());
        timer.destroy();
        assertEquals(Collections.emptyList(), scheduler.live());
    }

    @Test
    void aTimerOnNoSchedulerForADestroyedCoreIsAnsweredDestroyed() {
        core.destroy();

        assertInstanceOf(Answer.Destroyed.class, CoreTimer.start(core, Scheduler.none()));
    }

    @Test
    void anAnnouncementADestroyedTimerIsStillToldArmsNothing() {
        CoreTimer timer = started(scheduler);
        timer.destroy();

        timer.hooks().follow(200, true);

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(Collections.emptyList(), scheduler.scheduled());
    }

    /** Records each move of the timer as the layer makes it: the clock's time then, and what it armed, or null. */
    private List<List<Long>> movesOf(CoreTimer timer) {
        List<List<Long>> moves = new ArrayList<>();
        OptionalLong[] last = {OptionalLong.empty()};
        core.addListener(event -> {
            OptionalLong armed = timer.armed();
            if (!armed.equals(last[0])) {
                last[0] = armed;
                moves.add(Arrays.asList(scheduler.time(), armed.isPresent() ? armed.getAsLong() : null));
            }
        });
        return moves;
    }

    private CoreTimer started(Scheduler chosen) {
        return ok(CoreTimer.start(core, chosen));
    }

    private List<CoreEvent.WantSettledEvent> settlements() {
        synchronized (heard) {
            return heard.stream().filter(CoreEvent.WantSettledEvent.class::isInstance)
                    .map(CoreEvent.WantSettledEvent.class::cast).collect(Collectors.toList());
        }
    }

    private List<Long> timedOut() {
        return settlements().stream()
                .filter(settled -> settled.refusal().map(Refusal::code).orElse(0) == CoreConstants.REFUSAL_TIMED_OUT)
                .map(CoreEvent.WantSettledEvent::want).collect(Collectors.toList());
    }

    private List<Long> served() {
        return settlements().stream().filter(settled -> !settled.refusal().isPresent())
                .map(CoreEvent.WantSettledEvent::want).collect(Collectors.toList());
    }

    private void assertTimedOutAbout(String serviceId) {
        Refusal refusal = settlements().get(0).refusal().get();
        assertEquals(CoreConstants.REFUSAL_TIMED_OUT, refusal.code());
        assertEquals(Optional.of(serviceId), refusal.subject());
        assertTrue(refusal.hasFix());
        assertFalse(refusal.fix().isEmpty());
    }

    @Test
    void aSchedulerThatFailsToScheduleLeavesTheTimerDisarmedAndTellsTheSink() {
        CoreTimer timer = started(scheduler);
        scheduler.whenScheduled(at -> {
            throw new IllegalStateException("a scheduler failing on purpose");
        });

        ok(core.call(CoreCalls.wantService(HOST, "unscheduled", WantOptions.timed(0, 100))));

        assertEquals(OptionalLong.empty(), timer.armed());
        assertEquals(1, failures.size());
        failures.clear();
    }
}
