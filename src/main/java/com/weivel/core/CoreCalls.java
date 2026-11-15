package com.weivel.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The core's calls, one typed factory per function of the core's header, each building a {@link CoreCall} that
 * {@link Core#call} makes through the one wrapper. Each call is documented by the function of the same name in the
 * core's header; this class says only how its answer reads.
 *
 * <p>A call whose parts include something its caller built (a manifest, an id, want options, an open's payload)
 * answers a malformed status as the caller's mistake, {@link Answer.Refused} with {@link RefusalStatus#MALFORMED};
 * any other call answers it as a defect of the layer, an advance among them, whose time the core never calls
 * malformed. A status of {@code WEIVEL_STATUS_NONE} is {@link Answer.Absent}. A text of the caller's holding a NUL
 * character, which C would read cut short, is answered {@link Answer.Mistake} without anything reaching the core.
 *
 * @implNote This class and the wrapper are the only places a function of the core is called.
 */
public final class CoreCalls {
    private static final int STATUS_OK = 0;
    private static final int STATUS_REFUSED = 3;

    private CoreCalls() {
    }

    /**
     * One crossing into the core: exactly one call through the table, with arguments laid out in {@code arena}, its
     * result read before the arena closes.
     */
    @FunctionalInterface
    interface Crossing<T> {
        Crossed<T> cross(CoreFunctions functions, NativeArena arena, long core, long pending) throws Throwable;
    }

    /**
     * What a crossing answered: the core's status, the result it read, meaningful when the status is OK, and the
     * release of a copied answer the core refused, if any.
     */
    static final class Crossed<T> {
        private final int status;
        private final T value;
        private final Optional<CoreFunction> refusedRelease;

        Crossed(int status, T value) {
            this(status, value, Optional.<CoreFunction>empty());
        }

        Crossed(int status, T value, Optional<CoreFunction> refusedRelease) {
            this.status = status;
            this.value = value;
            this.refusedRelease = refusedRelease;
        }

        public int status() {
            return status;
        }

        public T value() {
            return value;
        }

        /** The release function the core refused once the answer was copied: the copy still answers the call. */
        Optional<CoreFunction> refusedRelease() {
            return refusedRelease;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Crossed)) {
                return false;
            }
            Crossed<?> that = (Crossed<?>) other;
            return status == that.status
                    && Objects.equals(value, that.value)
                    && refusedRelease.equals(that.refusedRelease);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Integer.hashCode(status);
            hash = 31 * hash + Objects.hashCode(value);
            hash = 31 * hash + refusedRelease.hashCode();
            return hash;
        }

        @Override
        public String toString() {
            return "Crossed[status=" + status + ", value=" + value + ", refusedRelease=" + refusedRelease + "]";
        }
    }

    /**
     * {@code weivel_register_manifest}, which the core copies whole before it returns.
     *
     * @param manifest the plugin's manifest
     * @return the call, answering the plugin's handle
     */
    public static CoreCall<Long> registerManifest(Manifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        return unlessMistaken(CallerTexts.inManifest(manifest), (functions, arena, core, pending) -> {
            NativeRegion plugin = arena.allocate(Layout.LONG);
            int status = functions.status(CoreFunction.REGISTER_MANIFEST, core,
                    Marshal.manifest(arena, manifest).address(), plugin.address(), pending);
            return new Crossed<>(status, plugin.getLong(0));
        });
    }

    /**
     * {@code weivel_open_plugin}.
     *
     * @param plugin the plugin's handle
     * @param payload the caller's own token for the plugin, answered back by the core uninspected
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> openPlugin(long plugin, long payload) {
        return carryingCallersParts((functions, arena, core, pending) -> new Crossed<>(
                functions.status(CoreFunction.OPEN_PLUGIN, core, plugin,
                        Marshal.pluginOptions(arena, payload).address(), pending),
                null));
    }

    /**
     * {@code weivel_plugin_payload}.
     *
     * @param plugin the plugin's handle
     * @return the call, answering the payload the plugin was last opened with, 0 for one never opened
     */
    static CoreCall<Long> pluginPayload(long plugin) {
        return builtByTheLayer((functions, arena, core, pending) -> {
            NativeRegion payload = arena.allocate(Layout.LONG);
            int status = functions.status(CoreFunction.PLUGIN_PAYLOAD, core, plugin, payload.address(), pending);
            return new Crossed<>(status, payload.getLong(0));
        });
    }

    /**
     * {@code weivel_release_plugin}.
     *
     * @param plugin the plugin's handle
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> releasePlugin(long plugin) {
        return builtByTheLayer((functions, arena, core, pending) -> new Crossed<>(
                functions.status(CoreFunction.RELEASE_PLUGIN, core, plugin, pending), null));
    }

    /**
     * {@code weivel_forget_plugin}.
     *
     * @param plugin the plugin's handle
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> forgetPlugin(long plugin) {
        return builtByTheLayer((functions, arena, core, pending) -> new Crossed<>(
                functions.status(CoreFunction.FORGET_PLUGIN, core, plugin, pending), null));
    }

    /**
     * {@code weivel_register_service}.
     *
     * @param owner a plugin's handle, or {@link CoreConstants#HOST}
     * @param id the service id
     * @return the call, answering the registration handle
     */
    public static CoreCall<Long> registerService(long owner, String id) {
        Objects.requireNonNull(id, "id");
        return unlessMistaken(CallerTexts.inText("the id", id), (functions, arena, core, pending) -> {
            NativeRegion registration = arena.allocate(Layout.LONG);
            int status = functions.status(CoreFunction.REGISTER_SERVICE, core, owner, arena.text(id).address(),
                    registration.address(), pending);
            return new Crossed<>(status, registration.getLong(0));
        });
    }

    /**
     * {@code weivel_unregister}.
     *
     * @param handle a registration, watch or want handle
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> unregister(long handle) {
        return builtByTheLayer((functions, arena, core, pending) -> new Crossed<>(
                functions.status(CoreFunction.UNREGISTER, core, handle, pending), null));
    }

    /**
     * {@code weivel_handle_state}.
     *
     * @param handle a want or watch handle
     * @return the call, answering the handle's condition, a value of the core's header
     */
    static CoreCall<Integer> handleState(long handle) {
        return builtByTheLayer((functions, arena, core, pending) -> {
            NativeRegion condition = arena.allocate(Layout.INT);
            int status = functions.status(CoreFunction.HANDLE_STATE, core, handle, condition.address(), pending);
            return new Crossed<>(status, condition.getInt(0));
        });
    }

    /**
     * {@code weivel_get_service}; nothing registered under the id is {@link Answer.Absent}.
     *
     * @param plugin the plugin asking, or {@link CoreConstants#HOST}
     * @param id the service id
     * @return the call, answering the registration handle
     */
    public static CoreCall<Long> getService(long plugin, String id) {
        Objects.requireNonNull(id, "id");
        return unlessMistaken(CallerTexts.inText("the id", id), (functions, arena, core, pending) -> {
            NativeRegion implementation = arena.allocate(Layout.LONG);
            int status = functions.status(CoreFunction.GET_SERVICE, core, plugin, arena.text(id).address(),
                    implementation.address(), pending);
            return new Crossed<>(status, implementation.getLong(0));
        });
    }

    /**
     * {@code weivel_want_service}.
     *
     * @param plugin the plugin asking, or {@link CoreConstants#HOST}
     * @param id the service id
     * @param options how long the want may wait
     * @return the call, answering the want handle
     */
    public static CoreCall<Long> wantService(long plugin, String id, WantOptions options) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(options, "options");
        return unlessMistaken(CallerTexts.inText("the id", id), (functions, arena, core, pending) -> {
            NativeRegion want = arena.allocate(Layout.LONG);
            int status = functions.status(CoreFunction.WANT_SERVICE, core, plugin, arena.text(id).address(),
                    Marshal.wantOptions(arena, options).address(), want.address(), pending);
            return new Crossed<>(status, want.getLong(0));
        });
    }

    /**
     * {@code weivel_watch_service}.
     *
     * @param plugin the plugin watching, or {@link CoreConstants#HOST}
     * @param id the service id
     * @return the call, answering the watch handle
     */
    public static CoreCall<Long> watchService(long plugin, String id) {
        Objects.requireNonNull(id, "id");
        return unlessMistaken(CallerTexts.inText("the id", id), (functions, arena, core, pending) -> {
            NativeRegion watch = arena.allocate(Layout.LONG);
            int status = functions.status(CoreFunction.WATCH_SERVICE, core, plugin, arena.text(id).address(),
                    watch.address(), pending);
            return new Crossed<>(status, watch.getLong(0));
        });
    }

    /**
     * {@code weivel_provide_capability}.
     *
     * @param plugin the providing plugin's handle
     * @param id the capability id
     * @return the call, answering the implementation handle
     */
    public static CoreCall<Long> provideCapability(long plugin, String id) {
        Objects.requireNonNull(id, "id");
        return unlessMistaken(CallerTexts.inText("the id", id), (functions, arena, core, pending) -> {
            NativeRegion implementation = arena.allocate(Layout.LONG);
            int status = functions.status(CoreFunction.PROVIDE_CAPABILITY, core, plugin, arena.text(id).address(),
                    implementation.address(), pending);
            return new Crossed<>(status, implementation.getLong(0));
        });
    }

    /**
     * {@code weivel_quarantine_plugin}.
     *
     * @param plugin the plugin's handle
     * @param reason why, as the caller knows it; empty for none, so the plugin's ledger row shows the core's refusal
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> quarantinePlugin(long plugin, Optional<QuarantineReason> reason) {
        Objects.requireNonNull(reason, "reason");
        return unlessMistaken(reason.flatMap(CallerTexts::inReason), (functions, arena, core, pending) ->
                new Crossed<>(functions.status(CoreFunction.QUARANTINE_PLUGIN, core, plugin,
                        Marshal.quarantineReason(arena, reason), pending), null));
    }

    /**
     * {@code weivel_verify_activation}.
     *
     * @param plugin the plugin's handle
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> verifyActivation(long plugin) {
        return builtByTheLayer((functions, arena, core, pending) -> new Crossed<>(
                functions.status(CoreFunction.VERIFY_ACTIVATION, core, plugin, pending), null));
    }

    /**
     * {@code weivel_verify_activation} and, when the core refuses the activation, {@code weivel_quarantine_plugin}
     * with no reason in the same crossing, so the plugin is quarantined before any other call reaches the core and
     * before any event of the verification is dispatched, and its ledger row shows the core's refusal.
     *
     * @param plugin the plugin's handle
     * @return the call, answering whether it was verified or quarantined; a refused verification whose quarantine
     *     the core refuses too, as for a plugin released meanwhile, is answered as the core answered the verification,
     *     with its refusal
     * @implNote The core's {@code pending_events} counts every event waiting, so the quarantine's answer covers the
     * verification's events too and the wrapper drains both in the order they were queued.
     */
    public static CoreCall<Verification> verifyOrQuarantine(long plugin) {
        return builtByTheLayer((functions, arena, core, pending) -> {
            int verified = functions.status(CoreFunction.VERIFY_ACTIVATION, core, plugin, pending);
            if (verified != STATUS_REFUSED) {
                return new Crossed<>(verified, Verification.passed());
            }
            int quarantined = functions.status(CoreFunction.QUARANTINE_PLUGIN, core, plugin, 0L, pending);
            return quarantined == STATUS_OK
                    ? new Crossed<>(STATUS_OK, Verification.quarantined())
                    : new Crossed<>(verified, Verification.passed());
        });
    }

    /**
     * {@code weivel_plan_activation}, its plan copied and released before the call answers.
     *
     * @param plugins the plugins to plan, as the core registered them
     * @param source which declarations draw the plan's edges
     * @param hostProvided the service ids the host supplies itself
     * @return the call, answering the plan
     */
    public static CoreCall<ActivationPlan> planActivation(List<Long> plugins, EdgeSource source,
                                                          List<String> hostProvided) {
        Objects.requireNonNull(hostProvided, "hostProvided");
        Optional<String> mistake = CallerTexts.inTexts("a service id the host provides", hostProvided);
        return mistake.isPresent()
                ? CoreCall.<ActivationPlan>mistaken(mistake.get())
                : planActivation(plugins, source, () -> hostProvided);
    }

    /**
     * {@code weivel_plan_activation}, with the ids the host provides read under the layer lock as the call is made,
     * so a registration under {@code WEIVEL_HOST} lands either wholly before the plan or wholly after it.
     *
     * @param plugins the plugins to plan, as the core registered them
     * @param source which declarations draw the plan's edges
     * @param hostProvided reads the service ids the host supplies itself, each one the core registered, so none holds
     *     a NUL character; it runs under the layer lock and may not call into the core
     * @return the call, answering the plan
     */
    public static CoreCall<ActivationPlan> planActivation(List<Long> plugins, EdgeSource source,
                                                          Supplier<List<String>> hostProvided) {
        Objects.requireNonNull(plugins, "plugins");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(hostProvided, "hostProvided");
        return carryingCallersParts((functions, arena, core, pending) -> {
                    NativeRegion plan = Marshal.emptyAnswer(arena, CoreLayouts.ACTIVATION_PLAN);
                    int status = functions.status(CoreFunction.PLAN_ACTIVATION, core,
                            plugins.isEmpty() ? 0L : Marshal.handles(arena, plugins).address(), plugins.size(),
                            source.value(), Marshal.strings(arena, hostProvided.get()).address(), plan.address(),
                            pending);
                    return copiedThenReleased(status, functions, CoreFunction.ACTIVATION_PLAN_RELEASE, plan,
                            AnswerCopy::plan, null);
                });
    }

    /**
     * {@code weivel_ledger}, its snapshot copied at the stride its first row declares and released before the call
     * answers.
     *
     * @return the call, answering one row per plugin registered and not forgotten, in the order they were registered
     */
    public static CoreCall<List<LedgerRow>> ledger() {
        return builtByTheLayer((functions, arena, core, pending) -> {
            NativeRegion snapshot = Marshal.emptyAnswer(arena, CoreLayouts.LEDGER_SNAPSHOT);
            int status = functions.status(CoreFunction.LEDGER, core, snapshot.address(), pending);
            return copiedThenReleased(status, functions, CoreFunction.LEDGER_SNAPSHOT_RELEASE, snapshot,
                    AnswerCopy::ledger, Collections.<LedgerRow>emptyList());
        });
    }

    /**
     * {@code weivel_service_ids}, its list copied and released before the call answers.
     *
     * @param plugin the plugin asking
     * @return the call, answering the ids in the core's order
     */
    public static CoreCall<List<String>> serviceIds(long plugin) {
        return builtByTheLayer((functions, arena, core, pending) -> {
            NativeRegion list = Marshal.emptyAnswer(arena, CoreLayouts.SERVICE_LIST);
            int status = functions.status(CoreFunction.SERVICE_IDS, core, plugin, list.address(), pending);
            return copiedThenReleased(status, functions, CoreFunction.SERVICE_LIST_RELEASE, list,
                    AnswerCopy::serviceIds, Collections.<String>emptyList());
        });
    }

    /**
     * {@code weivel_all_capabilities}, its list copied at the stride its first element declares and released before
     * the call answers.
     *
     * @param plugin the plugin asking, or {@link CoreConstants#HOST}
     * @param id the capability id
     * @return the call, answering the verified implementations in the core's order
     */
    public static CoreCall<List<CapabilityImplementation>> allCapabilities(long plugin, String id) {
        Objects.requireNonNull(id, "id");
        return unlessMistaken(CallerTexts.inText("the id", id), (functions, arena, core, pending) -> {
            NativeRegion list = Marshal.emptyAnswer(arena, CoreLayouts.CAPABILITY_LIST);
            int status = functions.status(CoreFunction.ALL_CAPABILITIES, core, plugin, arena.text(id).address(),
                    list.address(), pending);
            return copiedThenReleased(status, functions, CoreFunction.CAPABILITY_LIST_RELEASE, list,
                    AnswerCopy::capabilities, Collections.<CapabilityImplementation>emptyList());
        });
    }

    /**
     * {@code weivel_watch_capability}.
     *
     * @param plugin the plugin watching, or {@link CoreConstants#HOST}
     * @param id the capability id
     * @return the call, answering the watch handle
     */
    public static CoreCall<Long> watchCapability(long plugin, String id) {
        Objects.requireNonNull(id, "id");
        return unlessMistaken(CallerTexts.inText("the id", id), (functions, arena, core, pending) -> {
            NativeRegion watch = arena.allocate(Layout.LONG);
            int status = functions.status(CoreFunction.WATCH_CAPABILITY, core, plugin, arena.text(id).address(),
                    watch.address(), pending);
            return new Crossed<>(status, watch.getLong(0));
        });
    }

    /**
     * {@code weivel_report_capacity}; an answer to no open question is {@link Answer.Absent}.
     *
     * @param want the want the question named
     * @param registration the registration the question named
     * @param answer the provider's answer
     * @param now the caller's time now
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> reportCapacity(long want, long registration, CapacityAnswer answer, long now) {
        Objects.requireNonNull(answer, "answer");
        return unlessMistaken(CallerTexts.inNumbers("an attribute name of the answer", answer.attributes()),
                (functions, arena, core, pending) -> new Crossed<>(functions.status(CoreFunction.REPORT_CAPACITY,
                        core, want, registration, Marshal.capacity(arena, answer).address(), now, pending), null));
    }

    /**
     * {@code weivel_next_deadline}; no deadline set is {@link Answer.Absent}.
     *
     * @return the call, answering the deadline in the caller's milliseconds
     */
    static CoreCall<Long> nextDeadline() {
        return builtByTheLayer((functions, arena, core, pending) -> {
            NativeRegion deadline = arena.allocate(Layout.LONG);
            int status = functions.status(CoreFunction.NEXT_DEADLINE, core, deadline.address(), pending);
            return new Crossed<>(status, deadline.getLong(0));
        });
    }

    /**
     * {@code weivel_advance}. It hands the core a time itself, so it is not preceded by the check for a due deadline.
     *
     * @param now the caller's time now
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> advance(long now) {
        return new CoreCall<>(advanceCrossing(now), false, true);
    }

    static Crossing<Void> advanceCrossing(long now) {
        return (functions, arena, core, pending) -> new Crossed<>(
                functions.status(CoreFunction.ADVANCE, core, now, pending), null);
    }

    /**
     * Copies an answer the core filled when it answered OK, then releases it whatever the copy did, keeping a refused
     * release for the wrapper to report; any other status answers {@code none} and releases nothing.
     */
    private static <V> Crossed<V> copiedThenReleased(int status, CoreFunctions functions, CoreFunction release,
                                                     NativeRegion answer, Function<NativeRegion, V> copy, V none) {
        if (status != STATUS_OK) {
            return new Crossed<>(status, none);
        }
        V copied;
        try {
            copied = copy.apply(answer);
        } catch (RuntimeException | Error notCopied) {
            functions.status(release, answer.address());
            throw notCopied;
        }
        boolean released = functions.status(release, answer.address()) == STATUS_OK;
        return new Crossed<>(status, copied, released ? Optional.<CoreFunction>empty() : Optional.of(release));
    }

    private static <T> CoreCall<T> builtByTheLayer(Crossing<T> crossing) {
        return new CoreCall<>(crossing, false, false);
    }

    private static <T> CoreCall<T> carryingCallersParts(Crossing<T> crossing) {
        return new CoreCall<>(crossing, true, false);
    }

    private static <T> CoreCall<T> unlessMistaken(Optional<String> mistake, Crossing<T> crossing) {
        return mistake.isPresent() ? CoreCall.mistaken(mistake.get()) : carryingCallersParts(crossing);
    }
}
