package com.weivel.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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

    /** What a crossing answered: the core's status, and the result it read, meaningful when the status is OK. */
    static final class Crossed<T> {
        private final int status;
        private final T value;

        Crossed(int status, T value) {
            this.status = status;
            this.value = value;
        }

        public int status() {
            return status;
        }

        public T value() {
            return value;
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
                    && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Integer.hashCode(status);
            hash = 31 * hash + Objects.hashCode(value);
            return hash;
        }

        @Override
        public String toString() {
            return "Crossed[status=" + status + ", value=" + value + "]";
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
    public static CoreCall<Long> pluginPayload(long plugin) {
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
    public static CoreCall<Integer> handleState(long handle) {
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
     * {@code weivel_service_ids}, its list copied and released before the call answers.
     *
     * @param plugin the plugin asking
     * @return the call, answering the ids in the core's order
     */
    public static CoreCall<List<String>> serviceIds(long plugin) {
        return builtByTheLayer((functions, arena, core, pending) -> {
            NativeRegion list = Marshal.emptyAnswer(arena, CoreLayouts.SERVICE_LIST);
            int status = functions.status(CoreFunction.SERVICE_IDS, core, plugin, list.address(), pending);
            return new Crossed<>(status, status == STATUS_OK
                    ? copiedThenReleased(functions, CoreFunction.SERVICE_LIST_RELEASE, list, AnswerCopy::serviceIds)
                    : Collections.<String>emptyList());
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
            return new Crossed<>(status, status == STATUS_OK
                    ? copiedThenReleased(functions, CoreFunction.CAPABILITY_LIST_RELEASE, list,
                            AnswerCopy::capabilities)
                    : Collections.<CapabilityImplementation>emptyList());
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
    public static CoreCall<Long> nextDeadline() {
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

    /** Copies an answer the core filled, then releases it whatever the copy did. */
    private static <V> V copiedThenReleased(CoreFunctions functions, CoreFunction release, NativeRegion answer,
                                            Function<NativeRegion, V> copy) {
        try {
            return copy.apply(answer);
        } finally {
            functions.status(release, answer.address());
        }
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
