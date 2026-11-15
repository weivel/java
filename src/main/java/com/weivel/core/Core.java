package com.weivel.core;

import java.util.Objects;
import java.util.Optional;

/**
 * One core, behind the one wrapper every call into it goes through.
 *
 * <p>A call is made under the layer lock and its events are drained under the same hold, so the events drained after
 * a call are that call's. Each event is copied into a {@link CoreEvent} and released in the core before the next is
 * drained. Events are dispatched to the listeners first in, first out, outside the lock, by one thread at a time. A
 * call made while a dispatch runs, from a listener or from another thread, drains its own events onto the end of the
 * queue and returns; the running dispatch delivers them after everything drained before them. A call made while none
 * runs dispatches before it returns, so a listener can hear of a handle before the call that answered it returns.
 * Nothing of the caller's is ever called under the lock, and what a listener throws goes to the error sink.
 *
 * <p>A {@link VirtualMachineError} a listener or a step throws is the one failure not told to the sink: it is
 * rethrown to the thread that was dispatching once the dispatcher is free again, and the events and steps still queued
 * behind it stay queued, to be delivered by the next call that dispatches.
 *
 * <p>Any thread may call. {@link #destroy()} destroys the core; {@link #close()} then frees what the object holds.
 */
public final class Core implements AutoCloseable {
    private final Wrapper wrapper;

    private Core(Wrapper wrapper) {
        this.wrapper = wrapper;
    }

    /**
     * Creates a core, with no listener and the standard error sink.
     *
     * @param binary the loaded binary, which its caller keeps open until this core is closed
     * @param options the core's options, handed over unchanged
     * @return the core; or the core's refusal of the options, a malformed answer being the caller's mistake
     */
    public static Answer<Core> create(CoreBinary binary, CoreOptions options) {
        return create(binary, options, Optional.empty(), ErrorSink.STANDARD_ERROR);
    }

    /**
     * Creates a core. Its creation is drained as any call is, so what it queues is heard only by {@code listener}.
     *
     * @param binary the loaded binary, which its caller keeps open until this core is closed
     * @param options the core's options, handed over unchanged
     * @param listener a first listener, added before the core is created; empty for none
     * @param sink where failures go until it is replaced
     * @return the core; or the core's refusal of the options, a malformed answer being the caller's mistake
     */
    public static Answer<Core> create(CoreBinary binary, CoreOptions options, Optional<CoreListener> listener,
                                      ErrorSink sink) {
        Objects.requireNonNull(binary, "binary");
        return wrapped(Wrapper.start(binary, options, listener, sink));
    }

    static Answer<Core> wrapped(Answer<Wrapper> started) {
        if (started instanceof Answer.Ok) {
            return new Answer.Ok<>(new Core(((Answer.Ok<Wrapper>) started).value()));
        }
        if (started instanceof Answer.Refused) {
            Answer.Refused<Wrapper> refused = (Answer.Refused<Wrapper>) started;
            return new Answer.Refused<>(refused.status(), refused.refusal());
        }
        if (started instanceof Answer.Defect) {
            return new Answer.Defect<>(((Answer.Defect<Wrapper>) started).detail());
        }
        if (started instanceof Answer.Absent) {
            return new Answer.Defect<>("the core's creation answered nothing");
        }
        if (started instanceof Answer.Detached) {
            return new Answer.Detached<>();
        }
        if (started instanceof Answer.Destroyed) {
            return new Answer.Destroyed<>();
        }
        return new Answer.Mistake<>(((Answer.Mistake<Wrapper>) started).detail());
    }

    /**
     * Makes one call through the wrapper.
     *
     * @param call the call, as {@link CoreCalls} builds it
     * @param <T> the call's result
     * @return what the core answered, or {@link Answer.Destroyed} once this core is destroyed
     */
    public <T> Answer<T> call(CoreCall<T> call) {
        return wrapper.call(Objects.requireNonNull(call, "call"), CallSteps.none(), Optional.empty());
    }

    <T> Answer<T> call(CoreCall<T> call, CallSteps<T> steps) {
        return wrapper.call(Objects.requireNonNull(call, "call"), Objects.requireNonNull(steps, "steps"),
                Optional.empty());
    }

    /**
     * Adds a listener behind those already added; each event is heard by every listener in the order they were added.
     *
     * @param listener the listener
     */
    public void addListener(CoreListener listener) {
        wrapper.addListener(listener);
    }

    /**
     * Removes a listener. The one call a dispatch on another thread had already taken it for may still be running.
     *
     * @param listener the listener, compared by identity
     * @return whether it was listening
     */
    public boolean removeListener(CoreListener listener) {
        return wrapper.removeListener(listener);
    }

    /**
     * Replaces the error sink.
     *
     * @param sink where failures go from now on
     */
    public void setErrorSink(ErrorSink sink) {
        wrapper.setErrorSink(sink);
    }

    <T> Answer<T> callThenDefer(CoreCall<T> call, CallSteps<T> steps, DeferredStep step) {
        return wrapper.call(Objects.requireNonNull(call, "call"), Objects.requireNonNull(steps, "steps"),
                Optional.of(Objects.requireNonNull(step, "step")));
    }

    long drainedCount() {
        return wrapper.drainedCount();
    }

    boolean isLockHeldByCurrentThread() {
        return wrapper.isLockHeldByCurrentThread();
    }

    DeferredStep.Deferral defer(DeferredStep step) {
        return wrapper.defer(step);
    }

    boolean runAsTheDispatcher(DeferredStep step) {
        return wrapper.runAsTheDispatcher(step);
    }

    Answer<Void> installDeadlineHooks(DeadlineHooks hooks) {
        return wrapper.installDeadlineHooks(hooks);
    }

    boolean removeDeadlineHooks(DeadlineHooks hooks) {
        return wrapper.removeDeadlineHooks(hooks);
    }

    void report(Failure failure) {
        wrapper.report(Objects.requireNonNull(failure, "failure"));
    }

    Optional<BareServiceGet> bareServiceGet(long plugin, String id) {
        return wrapper.bareServiceGet(plugin, id);
    }

    boolean isLockHeld() {
        return wrapper.isLockHeld();
    }

    /**
     * Destroys the core. Events drained and not yet dispatched, and steps still waiting, are dropped; every later call
     * answers {@link Answer.Destroyed}. It may be called from a listener, whose dispatch then ends with it.
     */
    public void destroy() {
        wrapper.destroy();
    }

    /**
     * Whether the core is destroyed.
     *
     * @return whether {@link #destroy()} has been called
     */
    public boolean isDestroyed() {
        return wrapper.isDestroyed();
    }

    /**
     * Frees what this object holds, once the core is destroyed and no dispatch or arming of the timer still runs.
     *
     * @throws IllegalStateException when the core is not destroyed, or a dispatch or the arming of the timer is still
     *     running, which is where a listener that destroyed it still is
     */
    @Override
    public void close() {
        wrapper.close();
    }
}
