package io.github.intisy.ai.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The host side of the plugin system: it opens sessions, collects what plugins provide, and keeps
 * the ledger.
 *
 * @implNote Nothing here branches on a plugin id, and nothing can: a capability id and a service id
 * are the only dispatch keys a host has, which is what keeps a host ignorant of every specific
 * plugin no matter how many exist. The capability vocabulary is DECLARED by the caller rather than
 * minted here, so a host that renders no screens can warn about a plugin providing them and a host
 * that declares nothing reports nothing rather than calling every id unknown.
 */
public final class PluginHost {

    private final String app;
    private final List<String> applications;
    private final int api;
    private final List<String> surfaces;
    private final PluginLedger ledger = new PluginLedger();
    private final ServiceHub hub;
    private final Map<String, List<CapabilityRecord>> capabilities = new LinkedHashMap<String, List<CapabilityRecord>>();
    private final Map<String, List<CapabilityRecord>> verified = new LinkedHashMap<String, List<CapabilityRecord>>();
    private final Map<String, List<CapabilityWatcher>> capabilityWatchers = new LinkedHashMap<String, List<CapabilityWatcher>>();
    private final Map<String, List<Scheduler.Cancellable>> disposers = new LinkedHashMap<String, List<Scheduler.Cancellable>>();
    private final Set<String> revoked = new HashSet<String>();
    private final Map<String, String> importNames = new LinkedHashMap<String, String>();
    private List<String> known = new ArrayList<String>();

    /**
     * Creates a host that hosts the one application it is named for, whose {@link ServiceHub} never expires a pending want.
     *
     * @param app the HOME this host is loaded into, which is also taken as the one application it hosts
     * @param api the api version this host implements
     * @param surfaces the UI surface ids this host renders
     */
    public PluginHost(String app, int api, List<String> surfaces) {
        this(app, null, api, surfaces, Scheduler.NEVER);
    }

    /**
     * Creates a host that hosts the one application it is named for.
     *
     * @param app the HOME this host is loaded into, which is also taken as the one application it hosts
     * @param api the api version this host implements
     * @param surfaces the UI surface ids this host renders
     * @param scheduler the timer source used to expire an unmet service want
     */
    public PluginHost(String app, int api, List<String> surfaces, Scheduler scheduler) {
        this(app, null, api, surfaces, scheduler);
    }

    /**
     * Creates a host whose {@link ServiceHub} never expires a pending want; see the five-argument constructor.
     *
     * @param app the HOME this host is loaded into
     * @param applications the spisor applications this host hosts; null or empty means every application
     * @param api the api version this host implements
     * @param surfaces the UI surface ids this host renders
     */
    public PluginHost(String app, List<String> applications, int api, List<String> surfaces) {
        this(app, applications, api, surfaces, Scheduler.NEVER);
    }

    /**
     * @param app the HOME this host is loaded into
     * @param applications the spisor applications this host hosts; null or empty means every application
     * @param api the api version this host implements
     * @param surfaces the UI surface ids this host renders
     * @param scheduler the timer source used to expire an unmet service want
     * @implNote **{@code app} and {@code applications} are different things and conflating them is
     * the trap this signature exists to prevent.** {@code app} is the home a host is loaded into,
     * {@code claude} or {@code opencode} or {@code forebay}, and it is what {@link #getApp()}
     * publishes on the descriptor for every plugin to read. {@code applications} is the PRODUCT, so
     * one product loaded into four homes declares the same single application in all four, and it is
     * the only thing an artifact's targeting is matched against.
     * <p>The application side is a SET because a composite hosting several products has no single id.
     * An empty set means every application. When {@code applications} is null the app is taken as
     * the one application hosted, which is what every caller written before the distinction existed
     * meant; a blank app then yields an EMPTY set rather than a set holding {@code ""}, because a
     * blank id matches no target and would otherwise refuse every targeted artifact.
     */
    public PluginHost(String app, List<String> applications, int api, List<String> surfaces, Scheduler scheduler) {
        this.app = app;
        this.applications = applicationsOf(app, applications);
        this.api = api;
        this.surfaces = surfaces == null ? new ArrayList<String>() : new ArrayList<String>(surfaces);
        this.hub = new ServiceHub(scheduler, recorder());
        this.hub.namespaceOf(new Function<String, String>() {
            @Override
            public String apply(String pluginId) {
                String importName = importNames.get(pluginId);
                return importName == null ? pluginId : importName;
            }
        });
        this.hub.mismatch(new ServiceHub.Mismatch() {
            @Override
            public void withheld(String consumerId, String serviceId, String provider, String owner) {
                refuseWrongProvider(consumerId, serviceId, provider, owner);
            }
        });
    }

    private ServiceHub.Recorder recorder() {
        return new ServiceHub.Recorder() {
            @Override
            public void provided(String pluginId, String serviceId) {
                ledger.recordServiceProvided(pluginId, serviceId);
            }

            @Override
            public void consumed(String pluginId, String serviceId) {
                ledger.recordServiceConsumed(pluginId, serviceId);
            }
        };
    }

    private static List<String> applicationsOf(String app, List<String> declared) {
        if (declared != null) {
            return new ArrayList<String>(declared);
        }
        return app == null || app.isEmpty() ? new ArrayList<String>() : Collections.singletonList(app);
    }

    /** @return the home this host is loaded into, as the descriptor publishes it */
    public String getApp() {
        return app;
    }

    /** @return the spisor applications this host hosts, or empty when it hosts every application */
    public List<String> getApplications() {
        return Collections.unmodifiableList(applications);
    }

    /** @return the api version this host implements */
    public int getApi() {
        return api;
    }

    /** @return the UI surface ids this host renders */
    public List<String> getSurfaces() {
        return Collections.unmodifiableList(surfaces);
    }

    /** @return this host's ledger of plugin activity */
    public PluginLedger getLedger() {
        return ledger;
    }

    /**
     * The capability ids this host understands. Told none, it verifies none and reports none.
     *
     * @param ids the capability ids this host recognises, replacing any set previously; null or empty means none
     */
    public void knownCapabilities(List<String> ids) {
        this.known = ids == null ? new ArrayList<String>() : new ArrayList<String>(ids);
    }

    /**
     * The bare service ids any plugin may register, forwarded to the registry.
     *
     * @param ids the bare service ids any plugin may register
     */
    public void wellKnownServices(List<String> ids) {
        hub.wellKnown(ids);
    }

    /**
     * Null when this host can load the artifact, or the failure explaining why it cannot.
     *
     * @param facts the artifact's manifest facts
     * @return null when this host can load it, otherwise the failure explaining the mismatch
     * @implNote The application is checked before the api floor, which is the order of usefulness
     * to whoever reads the error: being built for another product is a more useful sentence than
     * being built for a newer api of a product it was never for. Both sides are sets and the test
     * is symmetric overlap, so a composite hosting several applications loads a plugin targeting
     * any of them; an empty set on EITHER side means "any", which is what keeps the field additive
     * for every artifact and every host that predates it.
     * Language is deliberately not an axis here. A host of another language does not refuse a
     * plugin, it reaches it through the compatibility layer.
     */
    public PluginException supports(ManifestFacts facts) {
        List<String> targets = facts.getTargets();
        if (!targets.isEmpty() && !applications.isEmpty() && Collections.disjoint(targets, applications)) {
            return new PluginException(facts.getId(),
                    "is built for " + join(targets) + ", and this host hosts " + join(applications),
                    "install it in " + targets.get(0) + ", or add \"" + applications.get(0)
                            + "\" to \"targets\" in plugin.json");
        }
        return supports(facts.getId(), facts.getApi());
    }

    /**
     * Null when this host can load the plugin, or the failure explaining why it cannot.
     *
     * @param pluginId the plugin's id, used only to build the failure message
     * @param pluginApi the api version the plugin declares it needs
     * @return null when {@code pluginApi} is at or below this host's api, otherwise the failure explaining the mismatch
     * @deprecated checks the api floor alone and cannot see which applications an artifact is for.
     *     Call {@link #supports(ManifestFacts)}, which checks both.
     */
    @Deprecated
    public PluginException supports(String pluginId, int pluginApi) {
        if (pluginApi <= api) {
            return null;
        }
        return new PluginException(pluginId,
                "needs api " + pluginApi + ", this host has api " + api,
                "update the app to a version that implements api " + pluginApi + " or later");
    }

    /**
     * Opens the session one plugin's activation runs against, lifting any fence it was under.
     *
     * @param facts the plugin's manifest facts, whose id identifies the session
     * @param bus the host's event bus, wrapped so subscriptions made through it are tracked and released with the plugin
     * @return a new session scoped to this plugin
     */
    public PluginSession sessionFor(ManifestFacts facts, EventBus bus) {
        String pluginId = facts.getId();
        revoked.remove(pluginId);
        importNames.put(pluginId, facts.getImportName());
        ledger.recordDeclared(pluginId, facts.getCapabilitiesProvided(), facts.getPermissions());
        final Map<String, String> bound = facts.getBoundProviders();
        ServiceHub.Registry registry = hub.forPlugin(pluginId, new Function<String, String>() {
            @Override
            public String apply(String serviceId) {
                return bound.get(serviceId);
            }
        });
        return new PluginSession(this, facts, fenced(pluginId, declaredServices(facts), bound, registry),
                recording(pluginId, bus), consumed(pluginId, declaredCapabilities(facts)));
    }

    /**
     * Checks a finished activation against what its manifest declared, marking the plugin active
     * when the two agree and returning the failure to quarantine it with when they do not.
     *
     * @param facts the plugin's manifest facts, whose declared capabilities are checked
     * @return null when every declared capability was provided and nothing extra was, otherwise the mismatch to quarantine the plugin with
     * @implNote A plugin's implementations reach its consumers here and not at {@code provide}: until
     * this check passes the host may still quarantine the plugin, and a consumer must never be handed
     * an implementation about to be withdrawn for a declaration mismatch (invariant 14). They join in
     * the order activations are verified, which is the order a consumer's answer keeps.
     */
    public PluginException verifyActivation(ManifestFacts facts) {
        String pluginId = facts.getId();
        List<String> declared = facts.getCapabilitiesProvided();
        LedgerEntry entry = ledger.entry(pluginId);
        List<String> provided = entry == null ? new ArrayList<String>() : entry.getCapabilitiesProvided();

        List<String> missing = missingFrom(declared, provided);
        if (!missing.isEmpty()) {
            return new PluginException(pluginId,
                    "capabilities declared but never provided: " + join(missing),
                    "call ctx.provide(\"" + missing.get(0) + "\", ...) in activate, or remove it from capabilities.provides in plugin.json");
        }
        List<String> extra = missingFrom(provided, declared);
        if (!extra.isEmpty()) {
            return new PluginException(pluginId,
                    "capabilities provided but never declared: " + join(extra),
                    "add \"" + extra.get(0) + "\" to capabilities.provides in plugin.json");
        }
        ledger.recordStatus(pluginId, PluginStatus.ACTIVE, null, null);
        admitVerified(pluginId);
        return null;
    }

    /**
     * Every implementation of a capability, in activation order.
     *
     * @param id the capability id to look up
     * @return the plugins providing it, in activation order, or empty when none provide it
     */
    public List<CapabilityRecord> capability(String id) {
        List<CapabilityRecord> records = capabilities.get(id);
        return records == null ? new ArrayList<CapabilityRecord>() : new ArrayList<CapabilityRecord>(records);
    }

    /**
     * One service, or null when nothing provides it.
     *
     * @param id the service id to look up
     * @return the registered service, or null when nothing provides it
     */
    public Object service(String id) {
        return hub.get(id);
    }

    /**
     * Offers a service the host itself implements, for every plugin to reach.
     *
     * @param id the service id to register it under
     * @param service the host's own service implementation
     * @implNote How a plugin gets behaviour that belongs to a library it may not link: the host
     * links it once and hands a typed handle over, rather than every plugin carrying its own copy.
     * Owned by no plugin, so no quarantine takes it away.
     */
    public void provideService(String id, Object service) {
        hub.hostService(id, service);
    }

    /**
     * Quarantines a plugin: its capabilities, services and subscriptions go, the host stays up.
     *
     * @param pluginId the plugin to quarantine
     * @param error the failure recorded as the reason, whose detail and fix the ledger stores
     * @implNote Its session is fenced too, so an activation that finishes after the host stopped
     * waiting cannot register itself back in. Opening a session again lifts the fence.
     */
    public void markBroken(String pluginId, PluginException error) {
        strip(pluginId);
        ledger.recordStatus(pluginId, PluginStatus.BROKEN, error.getDetail(), error.getFix());
    }

    /**
     * Releases everything a plugin provided and every subscription it holds, and fences its session.
     *
     * @param pluginId the plugin to release
     */
    public void release(String pluginId) {
        strip(pluginId);
        ledger.recordStatus(pluginId, PluginStatus.STOPPED, null, null);
    }

    void provide(String pluginId, String id, Object implementation) {
        if (refuseLate(pluginId, "a late provision of capability", id)) {
            return;
        }
        if (!known.isEmpty() && !known.contains(id)) {
            Diagnostics.ignoreUnknown("capability", id, pluginId);
        }
        List<CapabilityRecord> records = capabilities.get(id);
        if (records == null) {
            records = new ArrayList<CapabilityRecord>();
            capabilities.put(id, records);
        }
        for (CapabilityRecord record : records) {
            if (record.getPluginId().equals(pluginId)) {
                throw new PluginException(pluginId,
                        "provided capability \"" + id + "\" twice",
                        "call ctx.provide once per capability in activate");
            }
        }
        records.add(new CapabilityRecord(pluginId, implementation));
        ledger.recordCapabilityProvided(pluginId, id);
    }

    private void strip(String pluginId) {
        revoked.add(pluginId);
        withdrawVerified(pluginId);
        dropCapabilities(pluginId);
        detach(pluginId);
        hub.releasePlugin(pluginId);
    }

    /**
     * Whether a call from this plugin arrives after it was quarantined or released.
     *
     * @implNote Quarantine stops the host waiting for a plugin, it does not stop the plugin: an
     * activation abandoned at its deadline can still finish and register. A late call is reported
     * rather than thrown, because the caller is work nobody is waiting on.
     */
    private boolean refuseLate(String pluginId, String what, String id) {
        if (!revoked.contains(pluginId)) {
            return false;
        }
        Diagnostics.report("ignored " + what + " \"" + id + "\" from " + pluginId + ", which is no longer running");
        return true;
    }

    /**
     * @implNote The declaration is checked before lateness, so an undeclared call is refused as
     * undeclared whether or not the plugin is still running, and only a declared call takes the late
     * path. A late get is let through: it installs nothing, and its honest answer to a revoked plugin
     * is the same null every other caller gets for a service nobody registered. {@code ids} filters
     * by the declaration without refusing or reporting, because listing is not an attempt, and it
     * ignores lateness the same way a late get does.
     */
    private ServiceHub.Registry fenced(final String pluginId, final Set<String> declared,
                                       final Map<String, String> bound, final ServiceHub.Registry registry) {
        return new ServiceHub.Registry() {
            @Override
            public Object get(String id) {
                if (!declared.contains(id)) {
                    refuseUndeclared(pluginId, id);
                    return null;
                }
                return refuseOwnedElsewhere(pluginId, id, bound.get(id)) != null ? null : registry.get(id);
            }

            @Override
            public Pending<Object> want(String id) {
                PluginException refusal = refusal(pluginId, declared, bound, "want", id);
                return refusal != null ? rejected(refusal) : registry.want(id);
            }

            @Override
            public Pending<Object> want(String id, long timeoutMillis) {
                PluginException refusal = refusal(pluginId, declared, bound, "want", id);
                return refusal != null ? rejected(refusal) : registry.want(id, timeoutMillis);
            }

            @Override
            public Scheduler.Cancellable watch(String id, ServiceHub.Listener listener) {
                return refusal(pluginId, declared, bound, "watch", id) != null ? inert() : registry.watch(id, listener);
            }

            @Override
            public Scheduler.Cancellable register(String id, Object service) {
                return refuseLate(pluginId, "a late registration of service", id) ? inert() : registry.register(id, service);
            }

            @Override
            public Scheduler.Cancellable register(String id, ServiceProvider provider, Map<String, Double> advertised) {
                return refuseLate(pluginId, "a late registration of service", id)
                        ? inert()
                        : registry.register(id, provider, advertised);
            }

            @Override
            public Pending<Object> want(ServiceRequirement requirement, Budget budget) {
                PluginException refusal = refusal(pluginId, declared, bound, "want", requirement.getId());
                return refusal != null ? rejected(refusal) : registry.want(requirement, budget);
            }

            @Override
            public List<String> ids() {
                List<String> reachable = new ArrayList<String>();
                for (String id : registry.ids()) {
                    if (declared.contains(id)) {
                        reachable.add(id);
                    }
                }
                return reachable;
            }
        };
    }

    /** Why a want or a watch is refused, having reported it, or null to let it through. */
    private PluginException refusal(String pluginId, Set<String> declared, Map<String, String> bound, String attempt, String id) {
        if (!declared.contains(id)) {
            return refuseUndeclared(pluginId, id);
        }
        PluginException mismatch = refuseOwnedElsewhere(pluginId, id, bound.get(id));
        if (mismatch != null) {
            return mismatch;
        }
        return refuseLate(pluginId, "a late " + attempt + " of service", id) ? ServiceHub.stoppedWaiting(pluginId, id) : null;
    }

    /**
     * Refuses a service owned by someone other than the provider the consumer is bound to.
     *
     * @implNote A bound service nobody provides is absence, never a mismatch, exactly as for an
     * unbound consumer.
     */
    private PluginException refuseOwnedElsewhere(String pluginId, String serviceId, String provider) {
        String owner = hub.ownerOf(serviceId);
        if (provider == null || owner == null || owner.equals(provider)) {
            return null;
        }
        return refuseWrongProvider(pluginId, serviceId, provider, owner);
    }

    /** Reports it once per plugin and id, the way an undeclared reach is. */
    private PluginException refuseWrongProvider(String pluginId, String serviceId, String provider, String owner) {
        PluginException refusal = new PluginException(pluginId,
                "reached for service \"" + serviceId + "\", which it binds to " + provider + " but " + owner + " provides",
                "install " + provider + ", or remove \"provider\" from \"" + serviceId
                        + "\" in services.consumes of its plugin.json");
        if (ledger.recordServiceRefused(pluginId, serviceId)) {
            Diagnostics.report("refused service \"" + serviceId + "\" to " + pluginId + ", which binds it to " + provider
                    + " while " + owner + " provides it: " + refusal.getFix());
        }
        return refusal;
    }

    /**
     * Refuses a service the plugin did not declare, reporting it once per plugin and id (invariant 12).
     *
     * @implNote Every attempt is refused and only the first is reported, so a plugin polling an id
     * it never declared does not flood the sink.
     */
    private PluginException refuseUndeclared(String pluginId, String serviceId) {
        PluginException refusal = new PluginException(pluginId,
                "reached for service \"" + serviceId + "\", which it does not declare",
                "add \"" + serviceId + "\" to services.consumes in its plugin.json");
        if (ledger.recordServiceRefused(pluginId, serviceId)) {
            Diagnostics.report("refused service \"" + serviceId + "\" to " + pluginId
                    + ", which does not declare it: " + refusal.getFix());
        }
        return refusal;
    }

    /**
     * @implNote The declaration is checked before lateness, as for services: an undeclared call is
     * refused and reported as undeclared whether or not the plugin still runs. A late {@code all} is
     * let through, since it installs nothing, while a late {@code watch} is inert and goes to the
     * diagnostics only, the way a late service call does.
     */
    private ConsumedCapabilities consumed(final String pluginId, final Set<String> declared) {
        return new ConsumedCapabilities() {
            @Override
            public List<Object> all(String id) {
                if (!declared.contains(id)) {
                    refuseUndeclaredCapability(pluginId, id);
                    return new ArrayList<Object>();
                }
                List<Object> implementations = new ArrayList<Object>();
                List<CapabilityRecord> records = verified.get(id);
                if (records != null) {
                    for (CapabilityRecord record : records) {
                        implementations.add(record.getImplementation());
                    }
                }
                return implementations;
            }

            @Override
            public Scheduler.Cancellable watch(String id, ConsumedCapabilities.Listener listener) {
                if (!declared.contains(id)) {
                    refuseUndeclaredCapability(pluginId, id);
                    return inert();
                }
                if (refuseLate(pluginId, "a late watch of capability", id)) {
                    return inert();
                }
                return tracked(pluginId, installCapabilityWatcher(id, listener));
            }
        };
    }

    private Scheduler.Cancellable installCapabilityWatcher(final String id, ConsumedCapabilities.Listener listener) {
        final CapabilityWatcher watcher = new CapabilityWatcher(listener);
        List<CapabilityWatcher> watchers = capabilityWatchers.get(id);
        if (watchers == null) {
            watchers = new ArrayList<CapabilityWatcher>();
            capabilityWatchers.put(id, watchers);
        }
        watchers.add(watcher);
        return new Scheduler.Cancellable() {
            @Override
            public void cancel() {
                List<CapabilityWatcher> installed = capabilityWatchers.get(id);
                if (installed == null) {
                    return;
                }
                installed.remove(watcher);
                if (installed.isEmpty()) {
                    capabilityWatchers.remove(id);
                }
            }
        };
    }

    private void admitVerified(String pluginId) {
        for (Map.Entry<String, List<CapabilityRecord>> provided : capabilities.entrySet()) {
            for (CapabilityRecord record : provided.getValue()) {
                if (record.getPluginId().equals(pluginId) && admit(provided.getKey(), record)) {
                    notifyCapabilityWatchers(provided.getKey(), record.getImplementation(), true);
                }
            }
        }
    }

    private boolean admit(String id, CapabilityRecord record) {
        List<CapabilityRecord> records = verified.get(id);
        if (records == null) {
            records = new ArrayList<CapabilityRecord>();
            verified.put(id, records);
        }
        if (records.contains(record)) {
            return false;
        }
        records.add(record);
        return true;
    }

    private void withdrawVerified(String pluginId) {
        for (String id : new ArrayList<String>(verified.keySet())) {
            List<CapabilityRecord> records = verified.get(id);
            for (CapabilityRecord record : new ArrayList<CapabilityRecord>(records)) {
                if (!record.getPluginId().equals(pluginId)) {
                    continue;
                }
                records.remove(record);
                if (records.isEmpty()) {
                    verified.remove(id);
                }
                notifyCapabilityWatchers(id, record.getImplementation(), false);
            }
        }
    }

    /**
     * @implNote A watcher that throws is reported and the rest still hear, as for a service watcher;
     * a watcher removed while the others are being told is skipped rather than called.
     */
    private void notifyCapabilityWatchers(String id, Object implementation, boolean provided) {
        List<CapabilityWatcher> watchers = capabilityWatchers.get(id);
        if (watchers == null) {
            return;
        }
        for (CapabilityWatcher watcher : new ArrayList<CapabilityWatcher>(watchers)) {
            if (!watchers.contains(watcher)) {
                continue;
            }
            try {
                watcher.listener.changed(implementation, provided);
            } catch (RuntimeException failure) {
                Diagnostics.report("a watcher of capability \"" + id + "\" threw while handling "
                        + (provided ? "provide" : "withdraw") + ": " + failure);
            }
        }
    }

    /**
     * Refuses a capability the plugin did not declare, reporting it once per plugin and id (invariant 14).
     *
     * @implNote Every attempt is refused and only the first is reported, as for an undeclared service.
     */
    private void refuseUndeclaredCapability(String pluginId, String capabilityId) {
        String fix = "add \"" + capabilityId + "\" to capabilities.consumes in its plugin.json";
        if (ledger.recordCapabilityRefused(pluginId, capabilityId)) {
            Diagnostics.report("refused capability \"" + capabilityId + "\" to " + pluginId
                    + ", which does not declare it: " + fix);
        }
    }

    private static Set<String> declaredCapabilities(ManifestFacts facts) {
        Set<String> declared = new HashSet<String>(facts.getCapabilitiesConsumed());
        declared.addAll(facts.getCapabilitiesProvided());
        return declared;
    }

    private static Set<String> declaredServices(ManifestFacts facts) {
        Set<String> declared = new HashSet<String>(facts.getServicesConsumed());
        declared.addAll(facts.getServicesProvided());
        return declared;
    }

    private EventBus recording(final String pluginId, final EventBus bus) {
        return new EventBus() {
            @Override
            public void publish(String topic, Object payload) {
                bus.publish(topic, payload);
            }

            @Override
            public Scheduler.Cancellable subscribe(String topic, EventBus.Listener listener) {
                if (refuseLate(pluginId, "a late subscription to topic", topic)) {
                    return inert();
                }
                ledger.recordTopic(pluginId, topic);
                return tracked(pluginId, bus.subscribe(topic, listener));
            }
        };
    }

    private Scheduler.Cancellable tracked(final String pluginId, final Scheduler.Cancellable dispose) {
        final boolean[] done = new boolean[1];
        final Scheduler.Cancellable[] self = new Scheduler.Cancellable[1];
        self[0] = new Scheduler.Cancellable() {
            @Override
            public void cancel() {
                if (done[0]) {
                    return;
                }
                done[0] = true;
                dispose.cancel();
                List<Scheduler.Cancellable> owned = disposers.get(pluginId);
                if (owned == null) {
                    return;
                }
                owned.remove(self[0]);
                if (owned.isEmpty()) {
                    disposers.remove(pluginId);
                }
            }
        };
        List<Scheduler.Cancellable> owned = disposers.get(pluginId);
        if (owned == null) {
            owned = new ArrayList<Scheduler.Cancellable>();
            disposers.put(pluginId, owned);
        }
        owned.add(self[0]);
        return self[0];
    }

    private void detach(String pluginId) {
        List<Scheduler.Cancellable> owned = disposers.get(pluginId);
        if (owned != null) {
            for (Scheduler.Cancellable dispose : new ArrayList<Scheduler.Cancellable>(owned)) {
                dispose.cancel();
            }
        }
        disposers.remove(pluginId);
    }

    private void dropCapabilities(String pluginId) {
        List<String> emptied = new ArrayList<String>();
        for (Map.Entry<String, List<CapabilityRecord>> held : capabilities.entrySet()) {
            List<CapabilityRecord> kept = new ArrayList<CapabilityRecord>();
            for (CapabilityRecord record : held.getValue()) {
                if (!record.getPluginId().equals(pluginId)) {
                    kept.add(record);
                }
            }
            if (kept.isEmpty()) {
                emptied.add(held.getKey());
            } else {
                held.setValue(kept);
            }
        }
        for (String id : emptied) {
            capabilities.remove(id);
        }
    }

    /** One installed capability listener, an identity of its own so the same listener can be installed twice. */
    private static final class CapabilityWatcher {
        private final ConsumedCapabilities.Listener listener;

        CapabilityWatcher(ConsumedCapabilities.Listener listener) {
            this.listener = listener;
        }
    }

    private static Pending<Object> rejected(PluginException reason) {
        Pending<Object> refused = new Pending<Object>();
        refused.reject(reason);
        return refused;
    }

    private static Scheduler.Cancellable inert() {
        return new Scheduler.Cancellable() {
            @Override
            public void cancel() {
            }
        };
    }

    private static List<String> missingFrom(List<String> wanted, List<String> present) {
        List<String> absent = new ArrayList<String>();
        for (String id : wanted) {
            if (!present.contains(id)) {
                absent.add(id);
            }
        }
        return absent;
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                out.append(", ");
            }
            out.append(values.get(index));
        }
        return out.toString();
    }
}
