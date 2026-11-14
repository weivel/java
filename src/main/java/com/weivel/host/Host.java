package com.weivel.host;

import com.weivel.context.PluginContext;
import com.weivel.context.PluginContexts;
import com.weivel.context.ServiceRegistration;
import com.weivel.core.ActivationPlan;
import com.weivel.core.Answer;
import com.weivel.core.Core;
import com.weivel.core.CoreBinary;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreInternals;
import com.weivel.core.DeferredStep;
import com.weivel.core.EdgeSource;
import com.weivel.core.Failure;
import com.weivel.core.FailureKind;
import com.weivel.core.Manifest;
import com.weivel.core.QuarantineReason;
import com.weivel.core.RefusalStatus;
import com.weivel.core.Verification;
import com.weivel.handles.Handles;
import com.weivel.timer.CoreTimer;
import com.weivel.timer.Scheduler;
import com.weivel.timer.Schedulers;
import com.weivel.timer.SystemScheduler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registers plugins written in Java on one core, activates, verifies and releases them, offers the host's own
 * services, and destroys it all. Every decision is the core's; the host carries its answers out. It never needs the
 * standard: reading a manifest file, discovering plugins on disk and reading a library declaration feed
 * {@link #register} from outside.
 *
 * <ul>
 *   <li>{@link #activate} asks the core for the plan, then starts each plugin of its order in that order on the
 *   calling thread, never waiting for an activate to finish, and answers the plan at once; each outcome arrives as a
 *   stage. A settled activate is verified, and a refused verification quarantined in the same hold of the layer
 *   lock; a failed activate is quarantined with a reason saying so.</li>
 *   <li>{@link #release} calls the plugin's deactivate while it is whole, releases it in the core, lets every event
 *   that release queued reach it, and only then ends its entries, forgets it and drops it. Inside a dispatch those
 *   last steps wait as a step behind the release's events.</li>
 *   <li>{@link #destroy} releases every plugin opened, the last opened first, waits for the opens and settlings under
 *   way, lets every event and step run, then disarms the timer and destroys the core. Every other call is quiet from
 *   the moment it is called, answering {@link Answer.Destroyed}.</li>
 *   <li>{@link #close} frees what the host holds, the class loaders it opened among them, once the destroy has
 *   finished.</li>
 * </ul>
 *
 * <p>What fails in code the host calls, a deactivate or a release, goes to the error sink, never to a caller. Any
 * thread may call.
 *
 * @implNote The host's lock guards its tables and counts alone. It is never held while the core, a plugin or a
 * caller's code runs, and nothing run under the layer lock takes it, so it never orders against the layer lock.
 */
public final class Host implements AutoCloseable {
    private final Core core;
    private final CoreTimer timer;
    private final PluginContexts contexts;
    private final Optional<SystemScheduler> ownScheduler;
    private final Opening opening;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Long, PluginRecord> plugins = new HashMap<>();
    private final List<PluginRecord> opened = new ArrayList<>();
    private final Map<String, Integer> hostServices = new LinkedHashMap<>();
    private final List<URLClassLoader> loaders = new ArrayList<>();
    private final List<PluginRecord> leftForTheDestroy = new ArrayList<>();
    private final List<Long> releasedByTheDestroy = new ArrayList<>();
    private final CompletableFuture<DestroyResult> destruction = new CompletableFuture<>();
    private int opensUnderWay;
    private int settlingsUnderWay;
    private int releasesUnderWay;
    private boolean destroyCalled;
    private boolean destroyWalked;
    private boolean destroyEnding;
    private boolean closed;

    private Host(Core core, CoreTimer timer, PluginContexts contexts, Optional<SystemScheduler> ownScheduler,
                 Opening opening) {
        this.core = core;
        this.timer = timer;
        this.contexts = contexts;
        this.ownScheduler = ownScheduler;
        this.opening = opening;
    }

    /**
     * Creates a core from {@code binary} and a host on it.
     *
     * @param binary the core's loaded binary, which its caller keeps open until the host is closed
     * @param options how the host is made
     * @return the host; the core's refusal of its options, or the timer's refusal of the scheduler
     */
    public static Answer<Host> create(CoreBinary binary, HostOptions options) {
        return create(binary, options, Opening.THROUGH_THE_CONTEXTS);
    }

    static Answer<Host> create(CoreBinary binary, HostOptions options, Opening opening) {
        Objects.requireNonNull(binary, "binary");
        Objects.requireNonNull(options, "options");
        Answer<Core> created = Core.create(binary, options.core(), Optional.empty(), options.sink());
        if (!(created instanceof Answer.Ok)) {
            return Answer.retyped(created);
        }
        Core core = ((Answer.Ok<Core>) created).value();
        Optional<SystemScheduler> own = options.scheduler().isPresent()
                ? Optional.<SystemScheduler>empty()
                : Optional.of(Schedulers.system());
        Scheduler scheduler = own.isPresent() ? own.get() : options.scheduler().get();
        Answer<CoreTimer> started = CoreTimer.start(core, scheduler);
        if (!(started instanceof Answer.Ok)) {
            core.destroy();
            core.close();
            own.ifPresent(SystemScheduler::close);
            return Answer.retyped(started);
        }
        CoreTimer timer = ((Answer.Ok<CoreTimer>) started).value();
        PluginContexts contexts = PluginContexts.create(core, new Handles(), timer, options.log());
        return new Answer.Ok<>(new Host(core, timer, contexts, own, opening));
    }

    /**
     * Registers a plugin from its manifest value and its entry.
     *
     * @param manifest the plugin's manifest value, kept until the host is freed
     * @param entry the plugin's entry
     * @return the plugin's handle; the core's refusal
     * @implNote A {@link Manifest} copies every list it is given and changes no more, so keeping the value given is
     * keeping a copy no caller can change.
     */
    public Answer<Long> register(Manifest manifest, Entry entry) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(entry, "entry");
        if (isDestroyCalled()) {
            return new Answer.Destroyed<>();
        }
        Answer<Long> registered = core.call(CoreCalls.registerManifest(manifest));
        if (registered instanceof Answer.Ok) {
            long plugin = ((Answer.Ok<Long>) registered).value();
            lock.lock();
            try {
                plugins.put(plugin, new PluginRecord(plugin, manifest, entry));
            } finally {
                lock.unlock();
            }
        }
        return registered;
    }

    /**
     * The manifest value the host registered a plugin with.
     *
     * @param plugin the plugin's handle
     * @return the manifest; empty once the plugin is forgotten, or for one the host never registered
     */
    public Optional<Manifest> manifest(long plugin) {
        return record(plugin).map(record -> record.manifest);
    }

    /**
     * The context the host built for a plugin.
     *
     * @param plugin the plugin's handle
     * @return the context; empty before the host first opened the plugin and once it is forgotten
     */
    public Optional<PluginContext> context(long plugin) {
        return record(plugin).map(PluginRecord::context);
    }

    /**
     * Registers a service the host itself supplies, under {@code WEIVEL_HOST}: an activation plan counts it as
     * provided by the host, and a consumer resolving it receives {@code service} itself.
     *
     * @param id the service id
     * @param service the host's own object
     * @return the registration; the core's refusal
     */
    public Answer<HostService> registerHostService(String id, Object service) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(service, "service");
        if (isDestroyCalled()) {
            return new Answer.Destroyed<>();
        }
        Answer<ServiceRegistration> registered = contexts.registerHostService(id, service);
        if (!(registered instanceof Answer.Ok)) {
            return Answer.retyped(registered);
        }
        countHostService(id, 1);
        return new Answer.Ok<>(new HostService(id, ((Answer.Ok<ServiceRegistration>) registered).value(),
                unregistered -> countHostService(unregistered, -1)));
    }

    private void countHostService(String id, int change) {
        lock.lock();
        try {
            int count = hostServices.getOrDefault(id, 0) + change;
            if (count > 0) {
                hostServices.put(id, count);
            } else {
                hostServices.remove(id);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads a plugin's entry by its class name from a JAR, in a class loader of its own whose parent is the loader of
     * {@link Entry}, which the host closes only when it is closed.
     *
     * @param jar the JAR's path
     * @param className the entry's binary class name
     * @return the entry; {@link Answer.Mistake} as {@link Entries#load} answers it
     */
    public Answer<Entry> loadEntry(Path jar, String className) {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(className, "className");
        URL location;
        try {
            location = jar.toUri().toURL();
        } catch (MalformedURLException unreadable) {
            return new Answer.Mistake<>("the JAR's path is no URL: " + unreadable.getMessage());
        }
        URLClassLoader loader;
        lock.lock();
        try {
            if (destroyCalled) {
                return new Answer.Destroyed<>();
            }
            loader = new URLClassLoader(new URL[] {location}, Entry.class.getClassLoader());
            loaders.add(loader);
        } finally {
            lock.unlock();
        }
        return Entries.load(className, loader);
    }

    /**
     * Plans the activation of {@code plugins} with the core, then starts each plugin of the plan's order in that
     * order: builds its context no later than it opens it, opens it and calls its activate, never waiting for one
     * activate before starting the next.
     *
     * @param plugins the plugins to activate, as the host registered them
     * @param source which declarations draw the plan's edges, the caller's choice
     * @return once every activate has been started, the plan and each plugin's outcome to come; the core's refusal
     *     of the plan; {@link Answer.Destroyed} once the destroy has been called
     */
    public Answer<Activation> activate(List<Long> plugins, EdgeSource source) {
        Objects.requireNonNull(plugins, "plugins");
        Objects.requireNonNull(source, "source");
        List<String> provided;
        lock.lock();
        try {
            if (destroyCalled) {
                return new Answer.Destroyed<>();
            }
            provided = new ArrayList<>(hostServices.keySet());
        } finally {
            lock.unlock();
        }
        Answer<ActivationPlan> planned = core.call(CoreCalls.planActivation(plugins, source, provided));
        if (!(planned instanceof Answer.Ok)) {
            return Answer.retyped(planned);
        }
        ActivationPlan plan = ((Answer.Ok<ActivationPlan>) planned).value();
        List<CompletableFuture<Outcome>> outcomes = new ArrayList<>(plan.order().size());
        for (long plugin : plan.order()) {
            outcomes.add(start(plugin));
        }
        return new Answer.Ok<>(new Activation(plan, outcomes));
    }

    private CompletableFuture<Outcome> start(long plugin) {
        PluginRecord record;
        lock.lock();
        try {
            if (destroyCalled) {
                return answered(plugin, ActivationState.RELEASED);
            }
            record = plugins.get(plugin);
            opensUnderWay++;
        } finally {
            lock.unlock();
        }
        if (record == null) {
            return failedOpen(plugin, "the core planned a plugin the host holds no record of");
        }
        return open(record);
    }

    private CompletableFuture<Outcome> open(PluginRecord record) {
        PluginContext context;
        try {
            context = record.contextIn(contexts);
        } catch (RuntimeException unmade) {
            return failedOpen(record.plugin, "the host could not build a plugin's context: " + unmade);
        }
        AtomicBoolean heldOpen = new AtomicBoolean();
        Answer<Void> answer = opening.open(contexts, context, () -> heldOpen.set(true));
        if (answer instanceof Answer.Ok) {
            return activated(record, context);
        }
        if (isTheCoresRefusal(answer)) {
            endOpen();
            return CompletableFuture.completedFuture(new Outcome(record.plugin, ActivationState.REFUSED,
                    ((Answer.Refused<Void>) answer).refusal()));
        }
        String detail = "a plugin's open failed for a reason other than the core's refusal: " + answer;
        return heldOpen.get() ? releasedAfterAFailedOpen(record, detail) : failedOpen(record.plugin, detail);
    }

    private static boolean isTheCoresRefusal(Answer<?> answer) {
        return answer instanceof Answer.Refused && ((Answer.Refused<?>) answer).status() == RefusalStatus.REFUSED;
    }

    private CompletableFuture<Outcome> failedOpen(long plugin, String detail) {
        report(FailureKind.LAYER_DEFECT, detail, Optional.<Throwable>empty());
        endOpen();
        return answered(plugin, ActivationState.FAILED);
    }

    /**
     * Releases and forgets a plugin whose open failed though the core holds it open, by the release steps that still
     * apply, and answers it {@code failed} once they have run.
     *
     * @implNote Its activate was never called, so no deactivate is owed; left alone it would stay open in the core
     * until the destroy, with no outcome of its own.
     */
    private CompletableFuture<Outcome> releasedAfterAFailedOpen(PluginRecord record, String detail) {
        report(FailureKind.LAYER_DEFECT, detail, Optional.<Throwable>empty());
        ReleaseStart start;
        lock.lock();
        try {
            opensUnderWay--;
            start = beginRelease(record);
        } finally {
            lock.unlock();
        }
        CompletableFuture<Void> released = start != null ? goOnReleasing(start) : record.release;
        return released.thenApply(nothing -> new Outcome(record.plugin, ActivationState.FAILED,
                Optional.empty()));
    }

    private void endOpen() {
        boolean ending;
        lock.lock();
        try {
            opensUnderWay--;
            ending = destroyMayEnd();
        } finally {
            lock.unlock();
        }
        if (ending) {
            endTheDestroy();
        }
    }

    /**
     * Takes a plugin the core opened as activating and calls its activate, unless the destroy was called while it was
     * being opened: it is then released at once without its activate being called, and answered released.
     */
    private CompletableFuture<Outcome> activated(PluginRecord record, PluginContext context) {
        CompletableFuture<Outcome> outcome = new CompletableFuture<>();
        ReleaseStart start = null;
        lock.lock();
        try {
            opensUnderWay--;
            if (destroyCalled) {
                start = beginRelease(record);
            } else {
                opened.remove(record);
                opened.add(record);
                record.activation = outcome;
                record.activateCalled = true;
            }
        } finally {
            lock.unlock();
        }
        if (start != null) {
            goOnReleasing(start);
            outcome.complete(new Outcome(record.plugin, ActivationState.RELEASED, Optional.empty()));
            return outcome;
        }
        callActivate(record, context, outcome);
        return outcome;
    }

    private void callActivate(PluginRecord record, PluginContext context, CompletableFuture<Outcome> outcome) {
        CompletionStage<?> activating;
        try {
            activating = record.entry.activate(context);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable thrown) {
            settle(record, context, outcome, thrown);
            return;
        }
        if (activating == null) {
            settle(record, context, outcome, new IllegalStateException("its activate answered no stage"));
            return;
        }
        activating.whenComplete((value, failure) -> settle(record, context, outcome, failure));
    }

    /**
     * Verifies or quarantines a plugin whose activate settled, unless it was released or the destroy called since,
     * and answers its outcome.
     */
    private void settle(PluginRecord record, PluginContext context, CompletableFuture<Outcome> outcome,
                        Throwable failure) {
        CompletableFuture<Void> settling = new CompletableFuture<>();
        lock.lock();
        try {
            if (destroyCalled || record.activation != outcome) {
                return;
            }
            settlingsUnderWay++;
            record.settling = settling;
        } finally {
            lock.unlock();
        }
        ActivationState state = ActivationState.QUARANTINED;
        try {
            state = failure == null ? verified(context) : quarantined(context, reasonFor(failure));
        } finally {
            endSettling(record, outcome, settling, state);
        }
    }

    private void endSettling(PluginRecord record, CompletableFuture<Outcome> outcome,
                             CompletableFuture<Void> settling, ActivationState state) {
        boolean ending;
        lock.lock();
        try {
            settlingsUnderWay--;
            record.settling = null;
            if (record.activation == outcome) {
                record.activation = null;
            }
            ending = destroyMayEnd();
        } finally {
            lock.unlock();
        }
        settling.complete(null);
        outcome.complete(new Outcome(record.plugin, state, Optional.empty()));
        if (ending) {
            endTheDestroy();
        }
    }

    private ActivationState verified(PluginContext context) {
        Answer<Verification> verification = contexts.verify(context);
        if (!(verification instanceof Answer.Ok)) {
            return quarantined(context, new QuarantineReason(CoreConstants.REFUSAL_NONE,
                    "its activation could not be verified: " + verification,
                    "release it and activate it again"));
        }
        Verification answered = ((Answer.Ok<Verification>) verification).value();
        if (answered.verified()) {
            return ActivationState.ACTIVE;
        }
        if (!answered.quarantinedInTheCore()) {
            report(FailureKind.LAYER_DEFECT, "the core did not quarantine a plugin whose activation it refused: "
                    + answered, Optional.<Throwable>empty());
        }
        return ActivationState.QUARANTINED;
    }

    private ActivationState quarantined(PluginContext context, QuarantineReason reason) {
        Answer<Void> quarantine = contexts.quarantine(context, Optional.of(reason));
        if (!(quarantine instanceof Answer.Ok)) {
            report(FailureKind.LAYER_DEFECT, "the core did not quarantine a plugin whose activate failed: "
                    + quarantine, Optional.<Throwable>empty());
        }
        return ActivationState.QUARANTINED;
    }

    private static QuarantineReason reasonFor(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return new QuarantineReason(CoreConstants.REFUSAL_NONE, "its activate failed: " + cause,
                "make the plugin's activate complete normally");
    }

    /**
     * Verifies a plugin's activation as a settled activate is verified: a plugin the host holds open is quarantined
     * in the same hold of the layer lock when the core refuses, and one it does not, registered only or never
     * registered, is answered as the core answers it, quarantining nothing.
     *
     * @param plugin the plugin's handle
     * @return {@link ActivationState#ACTIVE} or {@link ActivationState#QUARANTINED}; the core's refusal for a plugin
     *     the host holds nothing of open; {@link Answer.Destroyed} once the destroy has been called
     */
    public Answer<ActivationState> verify(long plugin) {
        PluginRecord record;
        boolean heldOpen;
        lock.lock();
        try {
            if (destroyCalled) {
                return new Answer.Destroyed<>();
            }
            record = plugins.get(plugin);
            heldOpen = record != null && opened.contains(record);
        } finally {
            lock.unlock();
        }
        if (!heldOpen) {
            Answer<Void> verified = core.call(CoreCalls.verifyActivation(plugin));
            return verified instanceof Answer.Ok
                    ? new Answer.Ok<>(ActivationState.ACTIVE)
                    : Answer.<ActivationState>retyped(verified);
        }
        Answer<Verification> verification = contexts.verify(record.context());
        if (!(verification instanceof Answer.Ok)) {
            return Answer.retyped(verification);
        }
        return new Answer.Ok<>(((Answer.Ok<Verification>) verification).value().verified()
                ? ActivationState.ACTIVE
                : ActivationState.QUARANTINED);
    }

    /**
     * Releases a plugin the host opened: calls its deactivate while it is still whole, releases it in the core, lets
     * every event that release queued reach it, then ends its entries, forgets it and drops it from every table. One
     * whose activate is still running is released the same way, its outcome answered released as the release begins.
     *
     * @param plugin the plugin's handle
     * @return a stage completing once the release has run: true; false at once, doing nothing, for a plugin the host
     *     has not opened, registered only or already forgotten; {@link Answer.Destroyed} once the destroy has been
     *     called
     */
    public CompletionStage<Answer<Boolean>> release(long plugin) {
        ReleaseStart start = null;
        CompletableFuture<Void> release;
        lock.lock();
        try {
            if (destroyCalled) {
                return CompletableFuture.completedFuture(new Answer.Destroyed<>());
            }
            PluginRecord record = plugins.get(plugin);
            if (record == null || record.release == null && !opened.contains(record)) {
                return CompletableFuture.completedFuture(new Answer.Ok<>(false));
            }
            if (record.release == null) {
                start = beginRelease(record);
            }
            release = record.release;
        } finally {
            lock.unlock();
        }
        if (start != null) {
            goOnReleasing(start);
        }
        return release.thenApply(nothing -> new Answer.Ok<>(true));
    }

    /**
     * Takes a plugin for its release, under the host's lock: it leaves the opened plugins, its activation in flight
     * is taken from it, and the release is counted, so a destroy waits for it.
     *
     * @return what the release goes on with outside the lock; null for a plugin whose release began already
     */
    private ReleaseStart beginRelease(PluginRecord record) {
        if (record.release != null) {
            return null;
        }
        record.release = new CompletableFuture<>();
        releasesUnderWay++;
        opened.remove(record);
        CompletableFuture<Outcome> inFlight = record.settling == null ? record.activation : null;
        ReleaseStart start = new ReleaseStart(record, inFlight, record.activateCalled, record.settling);
        record.activation = null;
        record.activateCalled = false;
        return start;
    }

    /** Answers an activation in flight released, then deactivates once a settling under way has ended. */
    private CompletableFuture<Void> goOnReleasing(ReleaseStart start) {
        if (start.inFlight != null) {
            start.inFlight.complete(new Outcome(start.record.plugin, ActivationState.RELEASED, Optional.empty()));
        }
        CompletableFuture<Void> settled = start.settling != null
                ? start.settling
                : CompletableFuture.<Void>completedFuture(null);
        settled.whenComplete((nothing, unused) -> deactivateThenRelease(start));
        return start.record.release;
    }

    private void deactivateThenRelease(ReleaseStart start) {
        CompletionStage<?> deactivated = start.owesDeactivate
                ? deactivate(start.record)
                : CompletableFuture.completedFuture(null);
        deactivated.whenComplete((nothing, unused) -> releaseInTheCore(start.record));
    }

    private CompletionStage<?> deactivate(PluginRecord record) {
        if (!(record.entry instanceof Deactivating)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<?> deactivating;
        try {
            deactivating = ((Deactivating) record.entry).deactivate();
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable thrown) {
            reportDeactivate(Optional.of(thrown));
            return CompletableFuture.completedFuture(null);
        }
        if (deactivating == null) {
            reportDeactivate(Optional.<Throwable>empty());
            return CompletableFuture.completedFuture(null);
        }
        return deactivating.handle((value, failure) -> {
            if (failure != null) {
                reportDeactivate(Optional.of(failure));
            }
            return null;
        });
    }

    private void reportDeactivate(Optional<Throwable> cause) {
        report(FailureKind.PLUGIN_DEACTIVATE, cause.isPresent()
                ? "a plugin's deactivate failed" : "a plugin's deactivate answered no stage", cause);
    }

    /**
     * Releases the plugin in the core, its entries ending once that release's events are dispatched, and puts its
     * forget on the dispatch queue behind them, so inside a dispatch the forget waits for them.
     */
    private void releaseInTheCore(PluginRecord record) {
        Answer<Void> released = contexts.release(record.context());
        if (!(released instanceof Answer.Ok)) {
            report(FailureKind.LAYER_DEFECT, "the core did not release a plugin the host held open: " + released,
                    Optional.<Throwable>empty());
        }
        DeferredStep.Deferral deferral = CoreInternals.defer(core, () -> {
            forget(record);
            return DeferredStep.Status.DONE;
        });
        if (deferral == DeferredStep.Deferral.DESTROYED) {
            endRelease(record);
        }
    }

    private void forget(PluginRecord record) {
        Answer<Void> forgotten = contexts.forget(record.context());
        if (!(forgotten instanceof Answer.Ok)) {
            report(FailureKind.LAYER_DEFECT, "the core did not forget a plugin the host released: " + forgotten,
                    Optional.<Throwable>empty());
        }
        endRelease(record);
    }

    private void endRelease(PluginRecord record) {
        boolean ending;
        lock.lock();
        try {
            plugins.remove(record.plugin, record);
            releasesUnderWay--;
            ending = destroyMayEnd();
        } finally {
            lock.unlock();
        }
        record.release.complete(null);
        if (ending) {
            endTheDestroy();
        }
    }

    /**
     * Destroys the host: releases every plugin it opened and has not released, the last opened first and one whose
     * activate is in flight or which is quarantined included, each after the one before has been released; waits for
     * every open and every settling under way as it began; lets every event and step those left run; then disarms the
     * timer and destroys the core. Inside a dispatch it waits as a step behind what is queued already.
     *
     * @return a stage completing normally once the destroy has finished; a second destroy answers the same stage
     */
    public CompletionStage<DestroyResult> destroy() {
        lock.lock();
        try {
            if (destroyCalled) {
                return destruction;
            }
            destroyCalled = true;
        } finally {
            lock.unlock();
        }
        CoreInternals.defer(core, () -> {
            beginTheDestroy();
            return DeferredStep.Status.DONE;
        });
        return destruction;
    }

    private void beginTheDestroy() {
        lock.lock();
        try {
            leftForTheDestroy.addAll(opened);
            Collections.reverse(leftForTheDestroy);
        } finally {
            lock.unlock();
        }
        releaseTheNextForTheDestroy();
    }

    private void releaseTheNextForTheDestroy() {
        ReleaseStart start = null;
        boolean ending = false;
        lock.lock();
        try {
            while (start == null && !leftForTheDestroy.isEmpty()) {
                start = beginRelease(leftForTheDestroy.remove(0));
            }
            if (start != null) {
                releasedByTheDestroy.add(start.record.plugin);
            } else {
                destroyWalked = true;
                ending = destroyMayEnd();
            }
        } finally {
            lock.unlock();
        }
        if (start != null) {
            goOnReleasing(start).whenComplete((nothing, unused) -> releaseTheNextForTheDestroy());
        } else if (ending) {
            endTheDestroy();
        }
    }

    /** Under the host's lock: whether the destroy may now take its last steps, which only one caller is told. */
    private boolean destroyMayEnd() {
        if (!destroyWalked || destroyEnding || opensUnderWay > 0 || settlingsUnderWay > 0 || releasesUnderWay > 0) {
            return false;
        }
        destroyEnding = true;
        return true;
    }

    /** Puts the destroy's last steps on the dispatch queue, behind every event and step its releases left. */
    private void endTheDestroy() {
        DeferredStep.Deferral deferral = CoreInternals.defer(core, () -> {
            finishTheDestroy();
            return DeferredStep.Status.DONE;
        });
        if (deferral == DeferredStep.Deferral.DESTROYED) {
            finishTheDestroy();
        }
    }

    private void finishTheDestroy() {
        try {
            timer.destroy();
        } finally {
            core.destroy();
            List<Long> released;
            lock.lock();
            try {
                released = new ArrayList<>(releasedByTheDestroy);
            } finally {
                lock.unlock();
            }
            destruction.complete(new DestroyResult(released));
        }
    }

    /**
     * Frees what the host holds: the core, the contexts, a scheduler it made itself, and every class loader it
     * opened. A plugin's code may run until then, so its loader stays open until then.
     *
     * @throws IllegalStateException when the destroy has not finished, or the thread that finished it is still
     *     dispatching; nothing is freed then, and the close may be made again
     * @throws UncheckedIOException when a class loader failed to close, once every other has been closed
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            if (!destruction.isDone()) {
                throw new IllegalStateException("the host's destroy has not finished; wait for it before closing");
            }
        } finally {
            lock.unlock();
        }
        core.close();
        List<URLClassLoader> closing;
        lock.lock();
        try {
            closed = true;
            closing = new ArrayList<>(loaders);
            loaders.clear();
        } finally {
            lock.unlock();
        }
        contexts.close();
        ownScheduler.ifPresent(SystemScheduler::close);
        closeLoaders(closing);
    }

    private static void closeLoaders(List<URLClassLoader> closing) {
        IOException first = null;
        for (URLClassLoader loader : closing) {
            try {
                loader.close();
            } catch (IOException failed) {
                if (first == null) {
                    first = failed;
                } else {
                    first.addSuppressed(failed);
                }
            }
        }
        if (first != null) {
            throw new UncheckedIOException("a plugin's class loader failed to close", first);
        }
    }

    private boolean isDestroyCalled() {
        lock.lock();
        try {
            return destroyCalled;
        } finally {
            lock.unlock();
        }
    }

    private Optional<PluginRecord> record(long plugin) {
        lock.lock();
        try {
            return Optional.ofNullable(plugins.get(plugin));
        } finally {
            lock.unlock();
        }
    }

    private static CompletableFuture<Outcome> answered(long plugin, ActivationState state) {
        return CompletableFuture.completedFuture(new Outcome(plugin, state, Optional.empty()));
    }

    private void report(FailureKind kind, String text, Optional<Throwable> cause) {
        CoreInternals.report(core, new Failure(kind, text, 0, 0, cause));
    }

    /** What a release goes on with once it has been taken under the host's lock. */
    private static final class ReleaseStart {
        private final PluginRecord record;
        private final CompletableFuture<Outcome> inFlight;
        private final boolean owesDeactivate;
        private final CompletableFuture<Void> settling;

        private ReleaseStart(PluginRecord record, CompletableFuture<Outcome> inFlight, boolean owesDeactivate,
                             CompletableFuture<Void> settling) {
            this.record = record;
            this.inFlight = inFlight;
            this.owesDeactivate = owesDeactivate;
            this.settling = settling;
        }
    }
}
