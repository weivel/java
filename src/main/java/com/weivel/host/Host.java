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
import com.weivel.core.LibraryBinary;
import com.weivel.core.LibraryFunction;
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
 * caller's code runs, so it is always taken innermost: the registration of a host service counts its id, and a plan
 * reads the ids counted, under the layer lock, which makes the registration and the count one step to every plan.
 */
public final class Host implements AutoCloseable {
    private final Core core;
    private final CoreTimer timer;
    private final PluginContexts contexts;
    private final Optional<SystemScheduler> ownScheduler;
    private final Opening opening;
    private final Pauses pauses;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Long, PluginRecord> plugins = new HashMap<>();
    private final List<PluginRecord> opened = new ArrayList<>();
    private final Map<String, Integer> hostServices = new LinkedHashMap<>();
    private final Map<String, Integer> hostServiceClaims = new HashMap<>();
    private final List<LoadedLibrary> libraries = new ArrayList<>();
    private final List<URLClassLoader> loaders = new ArrayList<>();
    private final List<PluginRecord> leftForTheDestroy = new ArrayList<>();
    private final List<Long> releasedByTheDestroy = new ArrayList<>();
    private final CompletableFuture<DestroyResult> destruction = new CompletableFuture<>();
    private int opensUnderWay;
    private int releasesUnderWay;
    private boolean destroyCalled;
    private boolean destroyWalked;
    private boolean destroyEnding;
    private boolean destroyFinished;
    private boolean closed;

    private Host(Core core, CoreTimer timer, PluginContexts contexts, Optional<SystemScheduler> ownScheduler,
                 Opening opening, Pauses pauses) {
        this.core = core;
        this.timer = timer;
        this.contexts = contexts;
        this.ownScheduler = ownScheduler;
        this.opening = opening;
        this.pauses = pauses;
    }

    /**
     * Creates a core from {@code binary} and a host on it.
     *
     * @param binary the core's loaded binary, which its caller keeps open until the host is closed
     * @param options how the host is made
     * @return the host; the core's refusal of its options, or the timer's refusal of the scheduler
     */
    public static Answer<Host> create(CoreBinary binary, HostOptions options) {
        return create(binary, options, Opening.THROUGH_THE_CONTEXTS, Pauses.NONE);
    }

    static Answer<Host> create(CoreBinary binary, HostOptions options, Opening opening, Pauses pauses) {
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
        return new Answer.Ok<>(new Host(core, timer, contexts, own, opening, pauses));
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
        AtomicBoolean claimed = new AtomicBoolean();
        Answer<ServiceRegistration> registered;
        try {
            registered = contexts.registerHostService(id, service, () -> claimHostService(id, claimed),
                    () -> countHostService(id, 1));
        } finally {
            if (claimed.get()) {
                changeCount(hostServiceClaims, id, -1);
            }
        }
        if (!(registered instanceof Answer.Ok)) {
            return Answer.retyped(registered);
        }
        pauses.afterAHostServiceIsRegistered(id);
        return new Answer.Ok<>(hostService(id, ((Answer.Ok<ServiceRegistration>) registered).value()));
    }

    private HostService hostService(String id, ServiceRegistration registration) {
        return new HostService(id, () -> contexts.unregisterHostService(registration,
                () -> countHostService(id, -1)));
    }

    /**
     * Under the layer lock, before the core is asked: refuses an id a live library declares, and otherwise claims it
     * until the registration is answered, so a library load that finds the claim is refused in its turn.
     */
    private Optional<Answer<Long>> claimHostService(String id, AtomicBoolean claimed) {
        lock.lock();
        try {
            if (aLiveLibraryDeclares(id)) {
                return Optional.of(new Answer.Mistake<>("the service id " + id
                        + " is declared by a C library the host has loaded"));
            }
            changeCount(hostServiceClaims, id, 1);
            claimed.set(true);
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /** Under the host's lock. */
    private boolean aLiveLibraryDeclares(String id) {
        for (LoadedLibrary library : libraries) {
            if (library.isLive() && library.declares(id)) {
                return true;
            }
        }
        return false;
    }

    private List<String> hostProvided() {
        lock.lock();
        try {
            return new ArrayList<>(hostServices.keySet());
        } finally {
            lock.unlock();
        }
    }

    private void countHostService(String id, int change) {
        changeCount(hostServices, id, change);
    }

    private void changeCount(Map<String, Integer> counts, String id, int change) {
        lock.lock();
        try {
            int count = counts.getOrDefault(id, 0) + change;
            if (count > 0) {
                counts.put(id, count);
            } else {
                counts.remove(id);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads a C library and registers each service it declares under {@code WEIVEL_HOST} as a
     * {@link LibraryService}: an activation plan counts each as provided by the host, and a consumer resolving one
     * receives that very object. The host never reads a declaration from a file: the standard does, or the caller
     * builds it.
     *
     * <p>A load that fails once a service is registered is undone as an unload: what it registered is unregistered,
     * and its binary is closed once nothing of it can run.
     *
     * @param path the library, the caller's path; the declaration names none
     * @param declaration what the library offers
     * @return the library; {@link Answer.Mistake}, told to no sink, for a declaration of no service, a service of no
     *     function, a function of a kind other than {@link LibraryDeclaration#BYTES}, an id or a function name listed
     *     twice, an id a registration under this host holds already (a plugin's, another live library's or the
     *     host's own), a path that names no library, or a library lacking a declared symbol; the core's refusal of a
     *     registration; {@link Answer.Destroyed} once the destroy has been called
     * @implNote An id is found registered already from the layer's own records, never by asking the core. One a
     *     plugin registers on another thread while the load runs can still be replaced by the library's registration,
     *     since the core lets {@code WEIVEL_HOST} replace one; closing that needs a register-if-absent call for the
     *     host in the core. The host's own registrations hold both ways: each claims its id before it asks the core.
     */
    public Answer<LoadedLibrary> loadLibrary(Path path, LibraryDeclaration declaration) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(declaration, "declaration");
        Optional<String> mistake = LibraryChecks.mistakeIn(declaration);
        if (mistake.isPresent()) {
            return new Answer.Mistake<>(mistake.get());
        }
        if (isDestroyCalled()) {
            return new Answer.Destroyed<>();
        }
        Optional<String> registered = registeredAlready(declaration);
        if (registered.isPresent()) {
            return new Answer.Mistake<>(takenIdText(registered.get()));
        }
        Answer<LibraryBinary> opened = LibraryBinary.open(path, declaration.release());
        if (!(opened instanceof Answer.Ok)) {
            return Answer.retyped(opened);
        }
        Answer<LoadedLibrary> built = built(((Answer.Ok<LibraryBinary>) opened).value(), declaration);
        if (!(built instanceof Answer.Ok)) {
            return built;
        }
        LoadedLibrary library = ((Answer.Ok<LoadedLibrary>) built).value();
        Answer<LoadedLibrary> kept = kept(library);
        if (!(kept instanceof Answer.Ok)) {
            library.lifetime().closeToCalls();
            return kept;
        }
        Answer<Void> registration = registerServices(library);
        if (!(registration instanceof Answer.Ok) || isDestroyCalled()) {
            undo(library);
            return registration instanceof Answer.Ok ? new Answer.Destroyed<>() : Answer.retyped(registration);
        }
        return kept;
    }

    private static String takenIdText(String id) {
        return "the service id " + id + " is registered under this host already, and a registration under "
                + "WEIVEL_HOST would replace it";
    }

    private Optional<String> registeredAlready(LibraryDeclaration declaration) {
        for (LibraryDeclaration.Service service : declaration.services()) {
            if (contexts.holdsARegistrationOf(service.id()) || isHeldByTheHost(service.id())) {
                return Optional.of(service.id());
            }
        }
        return Optional.empty();
    }

    private boolean isHeldByTheHost(String id) {
        lock.lock();
        try {
            return isTakenUnderTheLock(id);
        } finally {
            lock.unlock();
        }
    }

    /** Under the host's lock: whether a service of the host's own, or its claim, or a live library holds the id. */
    private boolean isTakenUnderTheLock(String id) {
        return hostServices.containsKey(id) || hostServiceClaims.containsKey(id) || aLiveLibraryDeclares(id);
    }

    /** Binds every declared function, closing the binary again when one is missing. */
    private Answer<LoadedLibrary> built(LibraryBinary binary, LibraryDeclaration declaration) {
        LibraryLifetime lifetime = new LibraryLifetime(() -> closeBinary(declaration.importName(), binary));
        List<LibraryService> services = new ArrayList<>();
        for (LibraryDeclaration.Service service : declaration.services()) {
            Map<String, LibraryFunction> functions = new LinkedHashMap<>();
            for (LibraryDeclaration.Function function : service.functions()) {
                Answer<LibraryFunction> bound = binary.function(function.symbol());
                if (!(bound instanceof Answer.Ok)) {
                    lifetime.closeToCalls();
                    return Answer.retyped(bound);
                }
                functions.put(function.name(), ((Answer.Ok<LibraryFunction>) bound).value());
            }
            services.add(new LibraryService(service.id(), functions, lifetime, pauses));
        }
        return new Answer.Ok<>(new LoadedLibrary(this, declaration, services, lifetime));
    }

    /**
     * Keeps the library among the host's before anything of it is registered, so every later ending of it finds it,
     * the destroy's among them, unless the destroy was called, or a live library or the host's own registration holds
     * one of its ids: the check and the keeping are one step under the host's lock.
     */
    private Answer<LoadedLibrary> kept(LoadedLibrary library) {
        lock.lock();
        try {
            if (destroyCalled) {
                return new Answer.Destroyed<>();
            }
            for (LibraryDeclaration.Service service : library.declaration().services()) {
                if (isTakenUnderTheLock(service.id())) {
                    return new Answer.Mistake<>(takenIdText(service.id()));
                }
            }
            libraries.add(library);
            return new Answer.Ok<>(library);
        } finally {
            lock.unlock();
        }
    }

    private Answer<Void> registerServices(LoadedLibrary library) {
        for (LibraryService service : library.services()) {
            String id = service.id();
            Answer<ServiceRegistration> registered = contexts.registerHostService(id, service,
                    () -> countHostService(id, 1));
            if (!(registered instanceof Answer.Ok)) {
                return Answer.retyped(registered);
            }
            HostService registration = hostService(id, ((Answer.Ok<ServiceRegistration>) registered).value());
            if (!keepRegistration(library, registration)) {
                unregister(registration);
            }
        }
        return new Answer.Ok<>(null);
    }

    private boolean keepRegistration(LoadedLibrary library, HostService registration) {
        lock.lock();
        try {
            return library.keep(registration);
        } finally {
            lock.unlock();
        }
    }

    /** Undoes a load the host kept, through the unload, unless an unload or the destroy took the library first. */
    private void undo(LoadedLibrary library) {
        List<HostService> taken = takeForUnloading(library);
        if (taken != null) {
            unloadTaken(library, taken);
        }
    }

    /**
     * Unloads a library: closes it to calls at once, so every later call through one of its services answers
     * {@link LibraryAnswer.Unloaded}, unregisters every service it registered, and closes its binary once no call
     * runs in it, at once when none does, else as the last one returns. A close the platform refuses is told to the
     * error sink, since it may come long after this answers.
     *
     * @param library a library this host loaded
     * @return true; false, doing nothing, for a library unloaded already; {@link Answer.Mistake} for another host's;
     *     {@link Answer.Destroyed} once the destroy has been called, since the destroy unloads every library itself
     */
    public Answer<Boolean> unloadLibrary(LoadedLibrary library) {
        Objects.requireNonNull(library, "library");
        if (library.host() != this) {
            return new Answer.Mistake<>("the library was loaded by another host");
        }
        List<HostService> taken;
        lock.lock();
        try {
            if (destroyCalled) {
                return new Answer.Destroyed<>();
            }
            taken = library.takeForUnloading();
        } finally {
            lock.unlock();
        }
        if (taken == null) {
            return new Answer.Ok<>(false);
        }
        unloadTaken(library, taken);
        return new Answer.Ok<>(true);
    }

    private List<HostService> takeForUnloading(LoadedLibrary library) {
        lock.lock();
        try {
            return library.takeForUnloading();
        } finally {
            lock.unlock();
        }
    }

    private void unloadTaken(LoadedLibrary library, List<HostService> registrations) {
        library.lifetime().closeToCalls();
        for (HostService registration : registrations) {
            unregister(registration);
        }
    }

    private void unregister(HostService registration) {
        Answer<Void> unregistered = registration.unregister();
        if (!(unregistered instanceof Answer.Ok) && !(unregistered instanceof Answer.Destroyed)) {
            report(FailureKind.LAYER_DEFECT, "the host did not unregister a service of a C library it unloaded: "
                    + unregistered, Optional.<Throwable>empty());
        }
    }

    /**
     * Destroy step 3: closes every live library to calls, each binary closing once no call runs in it.
     *
     * @implNote Its services are not unregistered one by one: the core is destroyed in the next step, which ends every
     *     registration, and an unregistration here would queue revocations no dispatch is left to deliver.
     */
    private void unloadEveryLibrary() {
        LoadedLibrary next;
        while ((next = nextToUnload()) != null) {
            next.lifetime().closeToCalls();
        }
    }

    private LoadedLibrary nextToUnload() {
        lock.lock();
        try {
            for (LoadedLibrary library : libraries) {
                if (library.takeForUnloading() != null) {
                    return library;
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes a library's binary once nothing holds it, telling the error sink when the platform refuses, unless the
     * host is destroyed, after which nothing more is reported.
     */
    private void closeBinary(String importName, LibraryBinary binary) {
        try {
            pauses.beforeALibraryCloses(importName);
            binary.close();
        } catch (RuntimeException refused) {
            if (!isDestroyFinished()) {
                report(FailureKind.LIBRARY_UNLOAD, "the C library " + importName + " could not be closed",
                        Optional.<Throwable>of(refused));
            }
        }
    }

    private boolean isDestroyFinished() {
        lock.lock();
        try {
            return destroyFinished;
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
            loader = Entries.jarLoader(location);
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
        if (isDestroyCalled()) {
            return new Answer.Destroyed<>();
        }
        Answer<ActivationPlan> planned = core.call(CoreCalls.planActivation(plugins, source, this::hostProvided));
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
            if (record != null) {
                record.opening = true;
            }
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
            return failedOpen(record, "the host could not build a plugin's context: " + unmade);
        }
        AtomicBoolean heldOpen = new AtomicBoolean();
        Answer<Void> answer = opening.open(contexts, context, () -> heldOpen.set(true));
        if (answer instanceof Answer.Ok) {
            return activated(record, context);
        }
        if (isTheCoresRefusal(answer)) {
            endOpen(record);
            return CompletableFuture.completedFuture(new Outcome(record.plugin, ActivationState.REFUSED,
                    ((Answer.Refused<Void>) answer).refusal()));
        }
        String detail = "a plugin's open failed for a reason other than the core's refusal: " + answer;
        return heldOpen.get() ? releasedAfterAFailedOpen(record, detail) : failedOpen(record, detail);
    }

    private static boolean isTheCoresRefusal(Answer<?> answer) {
        return answer instanceof Answer.Refused && ((Answer.Refused<?>) answer).status() == RefusalStatus.REFUSED;
    }

    private CompletableFuture<Outcome> failedOpen(long plugin, String detail) {
        report(FailureKind.LAYER_DEFECT, detail, Optional.<Throwable>empty());
        endOpen(null);
        return answered(plugin, ActivationState.FAILED);
    }

    private CompletableFuture<Outcome> failedOpen(PluginRecord record, String detail) {
        report(FailureKind.LAYER_DEFECT, detail, Optional.<Throwable>empty());
        endOpen(record);
        return answered(record.plugin, ActivationState.FAILED);
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
        CompletableFuture<Answer<Boolean>> asked;
        lock.lock();
        try {
            opensUnderWay--;
            asked = openEnded(record);
            start = beginRelease(record);
        } finally {
            lock.unlock();
        }
        CompletableFuture<Void> released = start != null ? goOnReleasing(start) : record.release;
        answerAskedRelease(asked, released);
        return released.thenApply(nothing -> new Outcome(record.plugin, ActivationState.FAILED,
                Optional.empty()));
    }

    /** Ends an open that left the core holding nothing of the plugin open, so a release asked meanwhile had nothing. */
    private void endOpen(PluginRecord record) {
        boolean ending;
        CompletableFuture<Answer<Boolean>> asked;
        lock.lock();
        try {
            opensUnderWay--;
            asked = record != null ? openEnded(record) : null;
            ending = destroyMayEnd();
        } finally {
            lock.unlock();
        }
        if (asked != null) {
            asked.complete(new Answer.Ok<>(false));
        }
        if (ending) {
            endTheDestroy();
        }
    }

    /** Under the host's lock: ends the plugin's open, answering the release asked while it was under way, if any. */
    private static CompletableFuture<Answer<Boolean>> openEnded(PluginRecord record) {
        record.opening = false;
        CompletableFuture<Answer<Boolean>> asked = record.releaseAskedWhileOpening;
        record.releaseAskedWhileOpening = null;
        return asked;
    }

    private static void answerAskedRelease(CompletableFuture<Answer<Boolean>> asked,
                                           CompletableFuture<Void> released) {
        if (asked != null) {
            released.thenRun(() -> asked.complete(new Answer.Ok<>(true)));
        }
    }

    /**
     * Takes a plugin the core opened as activating and calls its activate, unless the destroy was called or a release
     * asked for while it was being opened: it is then released at once without its activate being called, and
     * answered released.
     */
    private CompletableFuture<Outcome> activated(PluginRecord record, PluginContext context) {
        CompletableFuture<Outcome> outcome = new CompletableFuture<>();
        ReleaseStart start = null;
        CompletableFuture<Answer<Boolean>> asked;
        lock.lock();
        try {
            opensUnderWay--;
            asked = openEnded(record);
            if (destroyCalled || asked != null) {
                start = beginRelease(record);
            } else {
                opened.remove(record);
                opened.add(record);
                record.activation = outcome;
            }
        } finally {
            lock.unlock();
        }
        if (start != null) {
            answerAskedRelease(asked, goOnReleasing(start));
            outcome.complete(new Outcome(record.plugin, ActivationState.RELEASED, Optional.empty()));
            return outcome;
        }
        pauses.beforeTheActivateIsClaimed(record.plugin);
        CompletableFuture<Void> call = claimTheActivate(record, outcome);
        if (call != null) {
            pauses.afterTheActivateIsClaimed(record.plugin);
            callActivate(record, context, outcome, call);
        }
        return outcome;
    }

    /**
     * Under the host's lock, decides the activate is called, unless a release has taken the activation meanwhile:
     * the release then owes no deactivate and the activate is never called. A release taking it afterwards owes the
     * deactivate, and waits for the call to have returned before it calls it.
     *
     * @return the call to complete once the activate has returned; null when it is not to be called
     */
    private CompletableFuture<Void> claimTheActivate(PluginRecord record, CompletableFuture<Outcome> outcome) {
        lock.lock();
        try {
            if (record.activation != outcome) {
                return null;
            }
            record.activateCalled = true;
            record.activateCall = new CompletableFuture<>();
            return record.activateCall;
        } finally {
            lock.unlock();
        }
    }

    private void callActivate(PluginRecord record, PluginContext context, CompletableFuture<Outcome> outcome,
                              CompletableFuture<Void> call) {
        CompletionStage<?> activating;
        try {
            activating = record.entry.activate(context);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable thrown) {
            call.complete(null);
            settle(record, context, outcome, thrown);
            return;
        }
        call.complete(null);
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
        return ((Answer.Ok<Verification>) verification).value().verified()
                ? ActivationState.ACTIVE
                : ActivationState.QUARANTINED;
    }

    /**
     * Quarantines a plugin whose activate settled, which the host holds open while its settling runs, since a release
     * waits for the settling: the core refusing that quarantine is a defect of the layer, answered failed.
     */
    private ActivationState quarantined(PluginContext context, QuarantineReason reason) {
        Answer<Void> quarantine = contexts.quarantine(context, Optional.of(reason));
        if (quarantine instanceof Answer.Ok) {
            return ActivationState.QUARANTINED;
        }
        report(FailureKind.LAYER_DEFECT, "the core did not quarantine a plugin the host holds open: " + quarantine,
                Optional.<Throwable>empty());
        return ActivationState.FAILED;
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
     *     the host holds nothing of open, or one released meanwhile, whose quarantine the core refuses too;
     *     {@link Answer.Destroyed} once the destroy has been called
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
        pauses.beforeTheVerification(plugin);
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
     * <p>A plugin whose open is under way is released once the core has opened it, without its activate being
     * called, as one opened as the destroy began is; an open the core refuses leaves nothing to release.
     *
     * @param plugin the plugin's handle
     * @return a stage completing once the release has run: true; false, doing nothing, for a plugin the host has not
     *     opened, registered only, already forgotten, or whose open left the core holding nothing;
     *     {@link Answer.Destroyed} once the destroy has been called
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
            if (record != null && record.release == null && record.opening) {
                if (record.releaseAskedWhileOpening == null) {
                    record.releaseAskedWhileOpening = new CompletableFuture<>();
                }
                return record.releaseAskedWhileOpening;
            }
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
        ReleaseStart start = new ReleaseStart(record, inFlight, record.activateCalled,
                CompletableFuture.allOf(orDone(record.settling), orDone(record.activateCall)));
        record.activation = null;
        record.activateCalled = false;
        return start;
    }

    private static CompletableFuture<Void> orDone(CompletableFuture<Void> step) {
        return step != null ? step : CompletableFuture.<Void>completedFuture(null);
    }

    /**
     * Answers an activation in flight released, then deactivates once the call to its activate has returned and a
     * settling under way has ended.
     */
    private CompletableFuture<Void> goOnReleasing(ReleaseStart start) {
        if (start.inFlight != null) {
            start.inFlight.complete(new Outcome(start.record.plugin, ActivationState.RELEASED, Optional.empty()));
        }
        start.waitedFor.whenComplete((nothing, unused) -> deactivateThenRelease(start));
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
        if (!destroyWalked || destroyEnding || opensUnderWay > 0 || releasesUnderWay > 0) {
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
            unloadEveryLibrary();
        } finally {
            disarmAndDestroyTheCore();
        }
    }

    private void disarmAndDestroyTheCore() {
        try {
            timer.destroy();
        } finally {
            core.destroy();
            List<Long> released;
            lock.lock();
            try {
                destroyFinished = true;
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
     * <p>Close once the destroy's stage has completed and the dispatch it finished in has returned: a destroy that
     * finishes inside a dispatch completes its stage on the dispatching thread, so a close chained to that stage, as
     * {@code destroy().thenRun(host::close)}, runs while that dispatch is still under way and is refused.
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
        private final CompletableFuture<Void> waitedFor;

        private ReleaseStart(PluginRecord record, CompletableFuture<Outcome> inFlight, boolean owesDeactivate,
                             CompletableFuture<Void> waitedFor) {
            this.record = record;
            this.inFlight = inFlight;
            this.owesDeactivate = owesDeactivate;
            this.waitedFor = waitedFor;
        }
    }
}
