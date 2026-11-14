package com.weivel.core;

/**
 * The wrapper's operations the layer's own packages use and its callers do not: a call with steps run under the layer
 * lock, a deferred step, the deadline hooks, and the error sink.
 *
 * <p>It is for the layer's own packages alone, {@code handles}, {@code timer}, {@code context} and {@code host},
 * until the layer has a module descriptor that can keep it from everyone else. A caller outside them that uses it can
 * run its own code under the layer lock, through a guard or an answered step, which the layer forbids: nothing of a
 * caller's may run there.
 *
 * @implNote Without a module descriptor Java cannot limit a public member to some packages, so this class is the one
 * public route to them, kept apart from {@link Core} so that its public surface is what a caller uses. Once the
 * layer has a module descriptor, it moves to a package the module does not export.
 */
public final class CoreInternals {
    private CoreInternals() {
    }

    /**
     * Makes one call through the wrapper with steps run under the layer lock around it.
     *
     * @param core the core
     * @param call the call
     * @param steps the guard checked before it and the step run once the core answered it OK
     * @param <T> the call's result
     * @return what the core answered, or what the guard or the step answered in its place
     */
    public static <T> Answer<T> call(Core core, CoreCall<T> call, CallSteps<T> steps) {
        return core.call(call, steps);
    }

    /**
     * Puts a step on the dispatch queue behind whatever is queued. While no dispatch runs, the calling thread becomes
     * the dispatcher and the step has run when this returns, behind whatever a call still arming the timer drained.
     *
     * @param core the core
     * @param step the step
     * @return whether the step ran here, is left to the dispatch, or will never run because the core is destroyed
     */
    public static DeferredStep.Deferral defer(Core core, DeferredStep step) {
        return core.defer(step);
    }

    /**
     * Installs the hooks a timer follows the core's deadline through, where none are installed; hooks installed while
     * a deadline is recorded are told it at once. One core is followed by one timer.
     *
     * @param core the core
     * @param hooks the hooks
     * @return {@link Answer.Ok}; {@link Answer.Mistake} when other hooks are installed, which stay;
     *     {@link Answer.Destroyed} for a destroyed core
     */
    public static Answer<Void> installDeadlineHooks(Core core, DeadlineHooks hooks) {
        return core.installDeadlineHooks(hooks);
    }

    /**
     * Takes the deadline hooks off the core when they are the very hooks given, and leaves any others installed, so a
     * timer never takes off what it did not install.
     *
     * @param core the core
     * @param hooks the hooks to take off, compared by identity
     * @return whether they were installed and are now taken off
     */
    public static boolean removeDeadlineHooks(Core core, DeadlineHooks hooks) {
        return core.removeDeadlineHooks(hooks);
    }

    /**
     * Tells the core's error sink of a failure, outside the layer lock; a sink that throws goes to the standard error
     * output.
     *
     * @param core the core
     * @param failure what failed
     * @throws IllegalStateException when the calling thread holds the layer lock, a defect of the layer, since the sink
     *     may not run there; from a guard or an answered step it is answered as the call's {@link Answer.Defect}
     */
    public static void report(Core core, Failure failure) {
        core.report(failure);
    }
}
