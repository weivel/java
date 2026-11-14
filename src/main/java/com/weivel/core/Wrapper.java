package com.weivel.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The one wrapper every call into one core goes through: it hands the core a deadline that fell due, makes the call
 * under the layer lock, drains its events under the same hold, answers the call from what the core answered and
 * queued, lets the lock go, tells the deadline hook, and dispatches.
 *
 * @implNote {@code lock} guards every field below it that is not final or volatile, is held across a call and its
 * drain, and is never held while a listener, a step, a hook or the sink runs. {@code dispatching} is true exactly
 * while one thread delivers the queue. Whoever adds to the queue either finds a dispatch running or becomes it before
 * it returns, so with no dispatch running the queue holds only what a thread still on its way out drained, one
 * telling the {@code follow} hook first, whose entries the next thread to leave delivers if it gets there sooner.
 *
 * <p>{@code deadlineAt} and {@code deadlineSet} are the latest announcement drained, and {@code deadlineSerial}
 * counts every reason to tell the {@code follow} hook again. {@code following} is true exactly while one thread calls
 * {@code follow}, and that thread does not stop until {@code followedSerial} has caught up, so the last call
 * {@code follow} receives carries the latest announcement, whichever thread drained it. Every event a call drains
 * along with an announcement is fenced with the serial that announcement left, and a dispatch stops at the first
 * event whose fence {@code followedSerial} has not reached, until the thread telling {@code follow} returns and
 * delivers the rest itself. So no event of a call is dispatched before the timer is armed for its announcement.
 */
final class Wrapper {
    private static final int STATUS_OK = 0;
    private static final int STATUS_NONE = 1;
    private static final int STATUS_MALFORMED = 2;
    private static final int STATUS_REFUSED = 3;
    private static final int STATUS_OUT_OF_MEMORY = 4;

    private final CoreFunctions functions;
    private final EventCopying copying;
    private final NativeArena arena;
    private final NativeRegion buffer;
    private final NativeRegion pending;
    private final CopyOnWriteArrayList<CoreListener> listeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile ErrorSink sink;
    private volatile boolean destroyed;

    private long core;
    private final ArrayDeque<QueueEntry> queue = new ArrayDeque<>();
    private boolean dispatching;
    private boolean closed;
    private long sequence;
    private DeadlineHooks hooks;
    private long deadlineAt;
    private boolean deadlineSet;
    private long deadlineSerial;
    private long followedSerial;
    private boolean following;

    private Wrapper(CoreFunctions functions, EventCopying copying, ErrorSink sink) {
        this.functions = functions;
        this.copying = copying;
        this.sink = sink;
        this.arena = new NativeArena(functions.access());
        this.buffer = arena.allocate(CoreLayouts.EVENT);
        this.pending = arena.allocate(Layout.INT);
    }

    static Answer<Wrapper> start(CoreBinary binary, CoreOptions options, Optional<CoreListener> listener,
                                 ErrorSink sink) {
        return start(binary.functions(), EventCopy.INSTANCE, options, listener, sink);
    }

    /** Creates a core through {@code functions}: the seam a test counts or fails the core's functions through. */
    static Answer<Wrapper> start(CoreFunctions functions, EventCopying copying, CoreOptions options,
                                 Optional<CoreListener> listener, ErrorSink sink) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(sink, "sink");
        Optional<String> mistake = CallerTexts.inOptions(options);
        if (mistake.isPresent()) {
            return new Answer.Mistake<Wrapper>(mistake.get());
        }
        Wrapper wrapper = new Wrapper(functions, copying, sink);
        listener.ifPresent(wrapper.listeners::add);
        return wrapper.create(options);
    }

    private Answer<Wrapper> create(CoreOptions options) {
        hold();
        CallNotes notes = new CallNotes();
        int status;
        try {
            status = createCore(options);
            if (status == STATUS_OK) {
                drain(waiting(), notes, new OwnRefusal());
            }
        } catch (Throwable thrown) {
            abandonCreation();
            throw rethrown(thrown);
        }
        if (status != STATUS_OK) {
            discardUncreated();
            return creationRefused(status);
        }
        leave(notes, null);
        return new Answer.Ok<>(this);
    }

    private int createCore(CoreOptions options) throws Throwable {
        try (NativeArena callArena = new NativeArena(functions.access())) {
            NativeRegion slot = callArena.allocate(Layout.ADDRESS);
            pending.putInt(0, 0);
            int status = functions.status(CoreFunction.CORE_CREATE,
                    Marshal.coreOptions(callArena, options).address(), slot.address(), pending.address());
            core = status == STATUS_OK ? slot.getAddress(0) : 0;
            return status;
        }
    }

    /** Entered with the lock held and left without it: destroys a core created before the creation failed. */
    private void abandonCreation() {
        try {
            if (core != 0) {
                destroyCore();
            }
        } finally {
            discardUncreated();
        }
    }

    private void discardUncreated() {
        destroyed = true;
        closed = true;
        lock.unlock();
        arena.close();
    }

    private Answer<Wrapper> creationRefused(int status) {
        switch (status) {
            case STATUS_MALFORMED:
                return new Answer.Refused<>(RefusalStatus.MALFORMED, Optional.<Refusal>empty());
            case STATUS_OUT_OF_MEMORY:
                return new Answer.Refused<>(RefusalStatus.OUT_OF_MEMORY, Optional.<Refusal>empty());
            default:
                return unknownStatus(status);
        }
    }

    private <T> Answer<T> unknownStatus(int status) {
        String text = "the core answered a status the layer does not know: " + status;
        report(new Failure(FailureKind.LAYER_DEFECT, text, 0, 0, Optional.empty()));
        return new Answer.Defect<>(text);
    }

    /** Makes a call; when it is answered OK, {@code after} joins the queue behind its events in the same hold. */
    <T> Answer<T> call(CoreCall<T> call, CallSteps<T> steps, Optional<DeferredStep> after) {
        Optional<String> mistake = call.mistake();
        if (mistake.isPresent()) {
            return new Answer.Mistake<>(mistake.get());
        }
        if (!enter()) {
            return new Answer.Destroyed<>();
        }
        if (!call.handsTheTime() && !handOverADueDeadline()) {
            return new Answer.Destroyed<>();
        }
        CallNotes notes = new CallNotes();
        Answer<T> answer;
        try {
            Optional<Answer<T>> cancelled = guarded(steps.guard(), notes);
            answer = cancelled.isPresent() ? cancelled.get() : callHeld(call, steps, notes);
            if (answer instanceof Answer.Ok) {
                after.ifPresent(step -> queue.addLast(new QueueEntry.StepEntry(step)));
            }
        } catch (Throwable thrown) {
            lock.unlock();
            throw rethrown(thrown);
        }
        leave(notes, null);
        return answer;
    }

    private <T> Answer<T> callHeld(CoreCall<T> call, CallSteps<T> steps, CallNotes notes) throws Throwable {
        CoreCalls.Crossed<T> crossed;
        Optional<Throwable> unread = Optional.empty();
        try {
            crossed = cross(call.crossing());
        } catch (RuntimeException | OutOfMemoryError notCopied) {
            crossed = null;
            unread = Optional.of(notCopied);
        }
        OwnRefusal own = new OwnRefusal();
        drain(waiting(), notes, own);
        if (unread.isPresent()) {
            return defect(notes, "the layer could not copy what the core answered a call", unread);
        }
        Answer<T> answer = answerOf(crossed, call.carriesCallersParts(), own, notes);
        return answer instanceof Answer.Ok
                ? recorded(steps.answered(), ((Answer.Ok<T>) answer).value(), notes)
                : answer;
    }

    /**
     * Makes one crossing into the core, under the lock, in an arena of its own: the one place a call's crossing is
     * made, so nothing else in the package needs the table of the core's functions.
     */
    <T> CoreCalls.Crossed<T> cross(CoreCalls.Crossing<T> crossing) throws Throwable {
        try (NativeArena callArena = new NativeArena(functions.access())) {
            pending.putInt(0, 0);
            return crossing.cross(functions, callArena, core, pending.address());
        }
    }

    private <T> Optional<Answer<T>> guarded(CallSteps.CallGuard<T> guard, CallNotes notes) {
        try {
            Optional<Answer<T>> checked = guard.check();
            return checked != null ? checked
                    : Optional.of(defect(notes, "a call's guard answered nothing", Optional.empty()));
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            return Optional.of(defect(notes, "a call's guard failed in the layer's own code", Optional.of(failure)));
        }
    }

    private <T> Answer<T> recorded(CallSteps.AnsweredStep<T> step, T value, CallNotes notes) {
        try {
            Answer<T> answer = step.record(value);
            return answer != null ? answer : defect(notes, "a call's answered step answered nothing", Optional.empty());
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            return defect(notes, "a call's answered step failed in the layer's own code", Optional.of(failure));
        }
    }

    private <T> Answer<T> answerOf(CoreCalls.Crossed<T> crossed, boolean carriesCallersParts, OwnRefusal own,
                                   CallNotes notes) {
        switch (crossed.status()) {
            case STATUS_OK:
                return new Answer.Ok<>(crossed.value());
            case STATUS_NONE:
                return new Answer.Absent<>();
            case STATUS_REFUSED:
                return new Answer.Refused<>(RefusalStatus.REFUSED, own.refusal());
            case STATUS_OUT_OF_MEMORY:
                return new Answer.Refused<>(RefusalStatus.OUT_OF_MEMORY, own.refusal());
            case STATUS_MALFORMED:
                return carriesCallersParts
                        ? new Answer.Refused<T>(RefusalStatus.MALFORMED, own.refusal())
                        : Wrapper.<T>defect(notes, "the core called a call the layer built malformed: "
                                + own.refusal().map(Refusal::detail).orElse("the core queued no refusal saying why"),
                                Optional.<Throwable>empty());
            default:
                return defect(notes, "the core answered a status the layer does not know: " + crossed.status(),
                        Optional.<Throwable>empty());
        }
    }

    private static <T> Answer<T> defect(CallNotes notes, String text, Optional<Throwable> cause) {
        notes.noteDefect(text, cause);
        return new Answer.Defect<>(text);
    }

    private int waiting() {
        return pending.getInt(0);
    }

    /** Drains a call's events onto the queue, fencing them all when one of them is an announcement. */
    private void drain(int waiting, CallNotes notes, OwnRefusal own) {
        long serialBefore = deadlineSerial;
        List<QueueEntry.EventEntry> drained = new ArrayList<>();
        try {
            int left = waiting;
            while (left > 0) {
                left = drainOne(notes, own, drained);
            }
        } finally {
            if (deadlineSerial != serialBefore) {
                drained.forEach(entry -> entry.fenceAt(deadlineSerial));
            }
            queue.addAll(drained);
        }
    }

    /** Drains one event: copied, recorded when it is an announcement, and released whatever the copy did. */
    private int drainOne(CallNotes notes, OwnRefusal own, List<QueueEntry.EventEntry> drained) {
        buffer.clear();
        buffer.putInt(0, (int) CoreLayouts.EVENT.byteSize());
        pending.putInt(0, 0);
        int status = nextEvent();
        if (status == STATUS_NONE) {
            return 0;
        }
        if (status != STATUS_OK) {
            notes.noteDefect("the core refused the layer's event struct, so the rest of its queue was not drained",
                    Optional.empty());
            return 0;
        }
        int left = waiting();
        long number = ++sequence;
        int kind = EventCopy.kindOf(buffer);
        try {
            recordDeadline(kind);
            if (EventCopy.isKnownKind(kind)) {
                copied(kind, number, notes).ifPresent(event -> {
                    drained.add(new QueueEntry.EventEntry(event));
                    own.offer(event);
                });
            }
        } finally {
            releaseEvent(notes);
        }
        return left;
    }

    private int nextEvent() {
        return functions.status(CoreFunction.NEXT_EVENT, core, buffer.address(), pending.address());
    }

    private void releaseEvent(CallNotes notes) {
        int status = functions.status(CoreFunction.EVENT_RELEASE, buffer.address());
        if (status != STATUS_OK) {
            notes.noteDefect("the core refused to release an event the layer drained", Optional.empty());
        }
    }

    private void recordDeadline(int kind) {
        if (kind == CoreConstants.EVENT_DEADLINE) {
            deadlineAt = EventCopy.deadlineAt(buffer);
            deadlineSet = EventCopy.deadlineSet(buffer);
            deadlineSerial++;
        }
    }

    private Optional<CoreEvent> copied(int kind, long number, CallNotes notes) {
        try {
            return Optional.of(copying.whole(buffer, number));
        } catch (RuntimeException | OutOfMemoryError notWhole) {
            return copiedFixedPart(kind, number, notes, notWhole);
        }
    }

    private Optional<CoreEvent> copiedFixedPart(int kind, long number, CallNotes notes, Throwable notWhole) {
        try {
            CoreEvent fixed = copying.fixedPart(buffer, number);
            notes.note(FailureKind.EVENT_INCOMPLETE,
                    "an event's texts or arrays could not be copied, so it is heard without them", kind, number,
                    Optional.of(notWhole));
            return Optional.of(fixed);
        } catch (RuntimeException | OutOfMemoryError notEvenFixed) {
            notes.note(FailureKind.EVENT_DROPPED, "not even an event's fixed part could be held, so it was dropped",
                    kind, number, Optional.of(notEvenFixed));
            return Optional.empty();
        }
    }

    /**
     * Entered with the lock held: answers true with it held, or false without it once the core is destroyed. When a
     * deadline is recorded it reads the clock once, outside the lock, and when the deadline has fallen due it
     * advances the core, drains, dispatches and takes the lock again.
     *
     * @implNote The time read is still good under the second hold: time only moves on, so a deadline it had reached
     * is due whatever was drained meanwhile.
     */
    private boolean handOverADueDeadline() {
        DeadlineHooks clock = hooks;
        if (!deadlineSet || clock == null) {
            return true;
        }
        lock.unlock();
        OptionalTime now = readClock(clock);
        if (!enter()) {
            return false;
        }
        if (now.unread() || !deadlineSet || now.time() < deadlineAt) {
            return true;
        }
        CallNotes notes = new CallNotes();
        try {
            advanceHeld(now.time(), notes);
        } catch (Throwable thrown) {
            lock.unlock();
            throw rethrown(thrown);
        }
        leave(notes, null);
        return enter();
    }

    private static final class OptionalTime {
        private final boolean unread;
        private final long time;

        private OptionalTime(boolean unread, long time) {
            this.unread = unread;
            this.time = time;
        }

        public boolean unread() {
            return unread;
        }

        public long time() {
            return time;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof OptionalTime)) {
                return false;
            }
            OptionalTime that = (OptionalTime) other;
            return unread == that.unread
                    && time == that.time;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Boolean.hashCode(unread);
            hash = 31 * hash + Long.hashCode(time);
            return hash;
        }

        @Override
        public String toString() {
            return "OptionalTime[unread=" + unread + ", time=" + time + "]";
        }
    }

    private OptionalTime readClock(DeadlineHooks clock) {
        try {
            return new OptionalTime(false, clock.now());
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report(new Failure(FailureKind.DEADLINE_HOOK,
                    "the clock failed, so no due deadline was handed over before a call", 0, 0,
                    Optional.of(failure)));
            return new OptionalTime(true, 0);
        }
    }

    private void advanceHeld(long now, CallNotes notes) throws Throwable {
        CoreCalls.Crossed<Void> crossed = cross(CoreCalls.advanceCrossing(now));
        if (crossed.status() == STATUS_MALFORMED) {
            notes.noteDefect("the core called the advance made for a due deadline malformed", Optional.empty());
        } else if (crossed.status() != STATUS_OK) {
            notes.note(FailureKind.DUE_ADVANCE,
                    "the core did not take the time of a deadline that fell due, which stays due", 0, 0,
                    Optional.empty());
        }
        drain(waiting(), notes, new OwnRefusal());
    }

    /**
     * Entered with the lock held and left without it: tells the {@code follow} hook of a deadline this hold drained,
     * lets the lock go, tells the sink what the call met, and dispatches unless a dispatch is already running.
     * Answers whether this thread dispatched {@code watched}, which may be null.
     */
    private boolean leave(CallNotes notes, QueueEntry watched) {
        boolean claimed;
        try {
            followTheDeadline();
            claimed = claimTheDispatch();
        } finally {
            lock.unlock();
        }
        try {
            tell(notes);
        } catch (VirtualMachineError error) {
            if (claimed) {
                endDispatch();
            }
            throw error;
        }
        return claimed && dispatch(watched);
    }

    private static final class FollowTurn {
        private final DeadlineHooks hooks;
        private final long at;
        private final boolean set;
        private final long serial;

        private FollowTurn(DeadlineHooks hooks, long at, boolean set, long serial) {
            this.hooks = hooks;
            this.at = at;
            this.set = set;
            this.serial = serial;
        }

        public DeadlineHooks hooks() {
            return hooks;
        }

        public long at() {
            return at;
        }

        public boolean set() {
            return set;
        }

        public long serial() {
            return serial;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FollowTurn)) {
                return false;
            }
            FollowTurn that = (FollowTurn) other;
            return Objects.equals(hooks, that.hooks)
                    && at == that.at
                    && set == that.set
                    && serial == that.serial;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(hooks);
            hash = 31 * hash + Long.hashCode(at);
            hash = 31 * hash + Boolean.hashCode(set);
            hash = 31 * hash + Long.hashCode(serial);
            return hash;
        }

        @Override
        public String toString() {
            return "FollowTurn[hooks=" + hooks + ", at=" + at + ", set=" + set + ", serial=" + serial + "]";
        }
    }

    /**
     * Entered with the lock held and left with it held: tells {@code follow} the latest announcement, outside the
     * lock, and again for as long as a newer one was drained meanwhile.
     */
    private void followTheDeadline() {
        FollowTurn turn = takeFollowingTurn();
        while (turn != null) {
            lock.unlock();
            try {
                tellFollow(turn);
            } finally {
                lock.lock();
                followedSerial = turn.serial();
                following = false;
            }
            turn = takeFollowingTurn();
        }
    }

    private FollowTurn takeFollowingTurn() {
        if (following || followedSerial == deadlineSerial) {
            return null;
        }
        if (hooks == null) {
            followedSerial = deadlineSerial;
            return null;
        }
        following = true;
        return new FollowTurn(hooks, deadlineAt, deadlineSet, deadlineSerial);
    }

    private void tellFollow(FollowTurn turn) {
        try {
            turn.hooks().follow(turn.at(), turn.set());
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report(new Failure(FailureKind.DEADLINE_HOOK, "the deadline hook failed for an announced deadline", 0,
                    0, Optional.of(failure)));
        }
    }

    private boolean claimTheDispatch() {
        if (queue.isEmpty() || dispatching || firstIsFenced()) {
            return false;
        }
        dispatching = true;
        return true;
    }

    private boolean firstIsFenced() {
        QueueEntry first = queue.peekFirst();
        return first instanceof QueueEntry.EventEntry && ((QueueEntry.EventEntry) first).fence() > followedSerial;
    }

    private static final class Taken {
        private final QueueEntry entry;
        private final boolean fenced;

        private Taken(QueueEntry entry, boolean fenced) {
            this.entry = entry;
            this.fenced = fenced;
        }

        public QueueEntry entry() {
            return entry;
        }

        public boolean fenced() {
            return fenced;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Taken)) {
                return false;
            }
            Taken that = (Taken) other;
            return Objects.equals(entry, that.entry)
                    && fenced == that.fenced;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(entry);
            hash = 31 * hash + Boolean.hashCode(fenced);
            return hash;
        }

        @Override
        public String toString() {
            return "Taken[entry=" + entry + ", fenced=" + fenced + "]";
        }
    }

    /**
     * Delivers the queue until it is empty or reaches a fence. Only the thread that set {@code dispatching} calls it,
     * without the lock. Answers whether {@code watched} is no longer waiting for another thread: this thread took it,
     * or the queue emptied, as a destroy made inside the dispatch empties it.
     */
    private boolean dispatch(QueueEntry watched) {
        boolean tookWatched = false;
        try {
            Taken taken = takeNext();
            while (taken.entry() != null) {
                tookWatched |= taken.entry() == watched;
                deliver(taken.entry());
                taken = takeNext();
            }
            return tookWatched || !taken.fenced();
        } catch (VirtualMachineError error) {
            endDispatch();
            throw error;
        }
    }

    private Taken takeNext() {
        hold();
        try {
            boolean fenced = firstIsFenced();
            QueueEntry entry = fenced ? null : queue.pollFirst();
            if (entry == null) {
                dispatching = false;
            }
            return new Taken(entry, fenced);
        } finally {
            lock.unlock();
        }
    }

    private void endDispatch() {
        hold();
        try {
            dispatching = false;
        } finally {
            lock.unlock();
        }
    }

    private void deliver(QueueEntry entry) {
        if (entry instanceof QueueEntry.EventEntry) {
            hear(((QueueEntry.EventEntry) entry).event());
        } else {
            run(((QueueEntry.StepEntry) entry).step());
        }
    }

    private void hear(CoreEvent event) {
        for (CoreListener listener : listeners) {
            if (destroyed) {
                return;
            }
            try {
                listener.hear(event);
            } catch (VirtualMachineError error) {
                throw error;
            } catch (Throwable failure) {
                report(new Failure(FailureKind.LISTENER, "a listener threw while it heard an event",
                        EventCopy.kindOf(event), event.sequence(), Optional.of(failure)));
            }
        }
    }

    private void run(DeferredStep step) {
        try {
            if (step.run() != DeferredStep.Status.DONE) {
                report(new Failure(FailureKind.STEP, "a deferred step answered a failure", 0, 0, Optional.empty()));
            }
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            report(new Failure(FailureKind.STEP, "a deferred step threw", 0, 0, Optional.of(failure)));
        }
    }

    private void tell(CallNotes notes) {
        notes.failures().forEach(this::report);
    }

    void report(Failure failure) {
        if (lock.isHeldByCurrentThread()) {
            throw new IllegalStateException(
                    "a defect of the layer: a failure was reported under the layer lock, where no sink may run");
        }
        ErrorSink current = sink;
        try {
            current.report(failure);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable sinkFailure) {
            System.err.println("weivel: the error sink threw " + sinkFailure + " and so was not told: "
                    + failure.text());
        }
    }

    DeferredStep.Deferral defer(DeferredStep step) {
        Objects.requireNonNull(step, "step");
        if (!enter()) {
            return DeferredStep.Deferral.DESTROYED;
        }
        QueueEntry.StepEntry entry = new QueueEntry.StepEntry(step);
        queue.addLast(entry);
        return leave(new CallNotes(), entry)
                ? DeferredStep.Deferral.RAN_HERE
                : DeferredStep.Deferral.LEFT_TO_THE_DISPATCH;
    }

    /**
     * Runs {@code step} as the dispatcher, behind whatever is queued, when no dispatch runs and no fenced event
     * waits; otherwise queues nothing and runs nothing.
     *
     * @implNote The dispatch is claimed in the same hold that found none running, so no other thread can take the
     * step between, and no {@code follow} is told here: that is left to the next thread to leave a call, the step's
     * own calls among them. Following here would let the lock go with the step queued and the dispatch unclaimed.
     */
    boolean runAsTheDispatcher(DeferredStep step) {
        Objects.requireNonNull(step, "step");
        if (!enter()) {
            return false;
        }
        if (dispatching || holdsAFencedEvent()) {
            lock.unlock();
            return false;
        }
        QueueEntry.StepEntry entry = new QueueEntry.StepEntry(step);
        queue.addLast(entry);
        dispatching = true;
        lock.unlock();
        dispatch(entry);
        return true;
    }

    private boolean holdsAFencedEvent() {
        for (QueueEntry entry : queue) {
            if (entry instanceof QueueEntry.EventEntry && ((QueueEntry.EventEntry) entry).fence() > followedSerial) {
                return true;
            }
        }
        return false;
    }

    /** Installs deadline hooks where none are; hooks installed while a deadline is recorded are told it at once. */
    Answer<Void> installDeadlineHooks(DeadlineHooks installing) {
        Objects.requireNonNull(installing, "hooks");
        if (!enter()) {
            return new Answer.Destroyed<>();
        }
        if (hooks != null) {
            lock.unlock();
            return new Answer.Mistake<>("other deadline hooks follow this core already; take them off first");
        }
        hooks = installing;
        if (deadlineSerial != 0) {
            deadlineSerial++;
        }
        leave(new CallNotes(), null);
        return new Answer.Ok<>(null);
    }

    /** Takes the deadline hooks off when they are {@code removing}, and leaves any others installed. */
    boolean removeDeadlineHooks(DeadlineHooks removing) {
        if (!enter()) {
            return false;
        }
        boolean installed = hooks == removing;
        if (installed) {
            hooks = null;
        }
        leave(new CallNotes(), null);
        return installed;
    }

    void addListener(CoreListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    boolean removeListener(CoreListener listener) {
        return listeners.remove(listener);
    }

    void setErrorSink(ErrorSink replacement) {
        sink = Objects.requireNonNull(replacement, "sink");
    }

    boolean isDestroyed() {
        return destroyed;
    }

    void destroy() {
        hold();
        try {
            if (core != 0) {
                destroyCore();
            }
        } finally {
            lock.unlock();
        }
    }

    private void destroyCore() {
        long destroying = core;
        core = 0;
        destroyed = true;
        queue.clear();
        listeners.clear();
        hooks = null;
        deadlineSet = false;
        functions.status(CoreFunction.CORE_DESTROY, destroying);
    }

    void close() {
        hold();
        try {
            if (closed) {
                return;
            }
            if (core != 0) {
                throw new IllegalStateException("the core is not destroyed; destroy it before closing it");
            }
            if (dispatching || following) {
                throw new IllegalStateException("a dispatch or the arming of the timer is still running");
            }
            closed = true;
            arena.close();
        } finally {
            lock.unlock();
        }
    }

    /** Whether any thread holds the layer lock: what a test reads from code that must run outside it. */
    boolean isLockHeld() {
        return lock.isLocked();
    }

    boolean isLockHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    long drainedCount() {
        hold();
        try {
            return sequence;
        } finally {
            lock.unlock();
        }
    }

    private boolean enter() {
        hold();
        if (core != 0) {
            return true;
        }
        lock.unlock();
        return false;
    }

    private void hold() {
        if (lock.isHeldByCurrentThread()) {
            throw new IllegalStateException(
                    "a defect of the layer: the layer lock was taken again on the thread that holds it");
        }
        lock.lock();
    }

    private static RuntimeException rethrown(Throwable thrown) {
        if (thrown instanceof RuntimeException) {
            return (RuntimeException) thrown;
        }
        if (thrown instanceof Error) {
            throw (Error) thrown;
        }
        return new IllegalStateException(thrown);
    }

    /** The first refusal-kind event a call's drain held: the call's own, since a refused call queues nothing else. */
    private static final class OwnRefusal {
        private Optional<Refusal> refusal = Optional.empty();

        void offer(CoreEvent event) {
            if (refusal.isPresent()) {
                return;
            }
            if (event instanceof CoreEvent.RefusalEvent) {
                refusal = Optional.of(((CoreEvent.RefusalEvent) event).refusal());
            } else if (event instanceof CoreEvent.ReachRefusedEvent) {
                refusal = Optional.of(((CoreEvent.ReachRefusedEvent) event).refusal());
            }
        }

        Optional<Refusal> refusal() {
            return refusal;
        }
    }
}
