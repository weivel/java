package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.FailureKind;
import com.weivel.core.Manifest;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What a plugin sees of the core, and the only way it reaches any of it: services to get, want, watch, list and
 * register, capabilities to provide, list and watch, what the core reports about it, its log and its manifest.
 *
 * <p>Every call that reaches the core answers an {@link Answer}: the core's own answer, read as it gave it, and
 * {@link Answer.Detached} once the core has forgotten the plugin, without reaching the core, since the core may by
 * then have issued the plugin's handle to another plugin. Whether a call is detached is decided under the layer lock
 * as the call is made. A resolved service or implementation is the provider's own object, never a copy or a wrapper,
 * and calling it crosses into nothing. A context caches no answer, and decides nothing the core decides.
 *
 * <p>Any thread may call. A plugin's listeners, watchers and answerers are called by the one thread dispatching,
 * outside every lock of the layer's, and may call in; what they throw goes to the error sink.
 */
public final class PluginContext {
    private final ContextState state;
    private final long plugin;
    private final Manifest manifest;
    private final long madeAfter;
    private final List<PluginEventListener> listeners = new CopyOnWriteArrayList<>();
    private final PluginLog log;
    private volatile long token;
    private volatile boolean detached;
    private volatile boolean silenced;

    PluginContext(ContextState state, long plugin, Manifest manifest, long madeAfter) {
        this.state = state;
        this.plugin = plugin;
        this.manifest = manifest;
        this.madeAfter = madeAfter;
        this.log = new PluginLog(state, manifest.importName());
    }

    /**
     * The provider's own object registered under {@code id} now, if this plugin may reach it.
     *
     * @param id the service id
     * @return the object; {@link Answer.Absent} when nothing is registered under the id, which is no refusal; the
     *     core's refusal when it refuses the get
     */
    public Answer<Object> get(String id) {
        return ServiceCalls.get(state, this, Objects.requireNonNull(id, "id"));
    }

    /**
     * The ids this plugin may reach that are registered now, in the order the core answers them.
     *
     * @return the ids; the core's refusal when it refuses the listing, never an empty list in its place
     */
    public Answer<List<String>> ids() {
        return ServiceCalls.ids(state, this);
    }

    /**
     * Wants the service registered under {@code id}, now or whenever it arrives. It settles once, when the core
     * settles it, from a dispatch step and never inside this call: with the provider's own object, or with the core's
     * refusal, its code, subject and fix intact.
     *
     * @param id the service id
     * @param limits how long it may wait, each limit handed to the core unchanged
     * @return the want, whose settlement is whatever this call answered in place of a want when the core made none,
     *     and is never completed once the plugin withdrew it before it settled
     */
    public PendingWant want(String id, WantLimits limits) {
        return ServiceCalls.want(state, this, Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(limits, "limits"));
    }

    /**
     * Watches {@code id}: every later registration the core reports for it is heard with the provider's own object,
     * and every unregistration with nothing, until the watch stops.
     *
     * @param id the service id
     * @param watcher what hears the changes
     * @return the watch; the core's refusal when it refuses it, never a watch that hears nothing
     */
    public Answer<Watching> watch(String id, ServiceWatcher watcher) {
        return ServiceCalls.watch(state, this, Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(watcher, "watcher"));
    }

    /**
     * Registers {@code service} under {@code id}, kept as the provider's own object until the plugin unregisters it
     * or stops. It offers no capacity answerer, so a constrained want's questions about it are answered with no live
     * attributes, its manifest's advertised attributes standing, and that it serves every consumer.
     *
     * @param id the service id
     * @param service the provider's own object
     * @return the registration, or the core's refusal
     */
    public Answer<ServiceRegistration> register(String id, Object service) {
        return ServiceCalls.register(state, this, Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(service, "service"), Optional.empty());
    }

    /**
     * Registers {@code service} under {@code id}, with what answers a constrained want's questions about it.
     *
     * @param id the service id
     * @param service the provider's own object
     * @param answerer what answers the capacity questions
     * @return the registration, or the core's refusal
     */
    public Answer<ServiceRegistration> register(String id, Object service, CapacityAnswerer answerer) {
        return ServiceCalls.register(state, this, Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(service, "service"), Optional.of(Objects.requireNonNull(answerer, "answerer")));
    }

    /**
     * Registers {@code service} under {@code id} as {@link #register(String, Object)} does, awaiting releases: once it
     * is unregistered, by the plugin or by its stop, each holder keeps its holding until it lets go of the object, and
     * once the last has, this plugin hears {@link PluginEvent.HoldingsReleased}, so whatever stood behind the object
     * may be freed. A plugin released or forgotten since hears nothing.
     *
     * @param id the service id
     * @param service the provider's own object
     * @return the registration, or the core's refusal
     */
    public Answer<ServiceRegistration> registerAwaitingReleases(String id, Object service) {
        return ServiceCalls.register(state, this, Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(service, "service"), Optional.<CapacityAnswerer>empty(),
                () -> Optional.<Answer<Long>>empty(), () -> { }, Optional.<Runnable>of(() -> { }));
    }

    /**
     * Lets go of a service revoked from this plugin whose provider awaits each holder's release, as a
     * {@link PluginEvent.Revoked} that {@linkplain PluginEvent.Revoked#awaitsRelease() awaits it} says: the plugin no
     * longer uses the object nor anything it answered, and the core ends the holding. However the plugin came to hold
     * it, by a get, a want or a watch, it lets go once.
     *
     * @param service the object it had been handed, matched by reference
     * @return true; false, calling nothing, when the plugin keeps no revoked holding of that very object, as before
     *     its revocation or once it let go; the core's refusal
     */
    public Answer<Boolean> releaseHolding(Object service) {
        return ServiceCalls.releaseHolding(state, this, Objects.requireNonNull(service, "service"));
    }

    /**
     * Provides {@code implementation} of the capability {@code id}; it reaches consumers once the plugin's activation
     * is verified.
     *
     * @param id the capability id
     * @param implementation the provider's own object
     * @return nothing beyond its success, or the core's refusal
     */
    public Answer<Void> provide(String id, Object implementation) {
        return CapabilityCalls.provide(state, this, Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(implementation, "implementation"));
    }

    /**
     * The verified implementations of the capability {@code id}, each the provider's own object, in the core's order.
     * An implementation the layer holds no object for is left out and the error sink told.
     *
     * @param id the capability id
     * @return the implementations; the core's refusal when it refuses the listing, never an empty list in its place
     */
    public Answer<List<Object>> implementations(String id) {
        return CapabilityCalls.implementations(state, this, Objects.requireNonNull(id, "id"));
    }

    /**
     * Watches the capability {@code id}: every implementation that joins and the very one that leaves, from now on.
     *
     * @param id the capability id
     * @param watcher what hears the changes
     * @return the watch; the core's refusal when it refuses it
     */
    public Answer<Watching> watchCapability(String id, CapabilityWatcher watcher) {
        return CapabilityCalls.watch(state, this, Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(watcher, "watcher"));
    }

    /**
     * Listens to what the core reports about this plugin, behind the listeners added before.
     *
     * @param listener the listener
     * @return what stops it
     */
    public Listening listen(PluginEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Listening(() -> listeners.remove(listener));
    }

    /**
     * The plugin's log, which the host directs.
     *
     * @return the log
     */
    public PluginLog log() {
        return log;
    }

    /**
     * The plugin's manifest value, as the host registered it.
     *
     * @return the manifest
     */
    public Manifest manifest() {
        return manifest;
    }

    long plugin() {
        return plugin;
    }

    long token() {
        return token;
    }

    void token(long issued) {
        token = issued;
    }

    boolean isDetached() {
        return detached;
    }

    /** Called under the layer lock, in the hold of the forget. */
    void detach() {
        detached = true;
    }

    /** The guard every call through this context gives the wrapper, read under the layer lock. */
    <T> Optional<Answer<T>> guarded() {
        return detached ? Optional.of(new Answer.Detached<>()) : Optional.empty();
    }

    /**
     * Whether an event naming this plugin may reach it: one drained after this context was made, and dispatched
     * while the plugin is not silenced by its release or quarantine.
     */
    boolean hears(long sequence) {
        return sequence > madeAfter && !silenced;
    }

    /** Called from the dispatch, once the events of the plugin's release or quarantine call are dispatched. */
    void silence() {
        silenced = true;
    }

    /** Called from the dispatch, once the events of the call opening the plugin again are dispatched. */
    void unsilence() {
        silenced = false;
    }

    void tell(PluginEvent event) {
        for (PluginEventListener listener : listeners) {
            state.callBack(() -> listener.hear(event), FailureKind.PLUGIN_CALLBACK,
                    "a plugin's listener threw", Optional.empty());
        }
    }

    void dropListeners() {
        listeners.clear();
    }
}
