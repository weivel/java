package com.weivel.core;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * The core's calls, one typed factory per function of the core's header, each building a {@link CoreCall} that
 * {@link Core#call} makes through the one wrapper. Each call is documented by the function of the same name in the
 * core's header; this class says only how its answer reads.
 *
 * <p>A call whose parts include something its caller built (a manifest, options, an id, a time) answers a malformed
 * status as the caller's mistake, {@link Answer.Refused} with {@link RefusalStatus#MALFORMED}; any other call answers
 * it as a defect of the layer. A status of {@code WEIVEL_STATUS_NONE} is {@link Answer.Absent}.
 *
 * @implNote This class and the wrapper are the only places a downcall handle of the core is invoked.
 */
public final class CoreCalls {
    private CoreCalls() {
    }

    /**
     * One crossing into the core: exactly one call through the table, with arguments laid out in {@code arena}, its
     * result read before the arena closes.
     */
    @FunctionalInterface
    interface Crossing<T> {
        Crossed<T> cross(CoreFunctions functions, Arena arena, MemorySegment core, MemorySegment pending)
                throws Throwable;
    }

    /** What a crossing answered: the core's status, and the result it read, meaningful when the status is OK. */
    record Crossed<T>(int status, T value) {
    }

    /**
     * {@code weivel_register_manifest}, which the core copies whole before it returns.
     *
     * @param manifest the plugin's manifest
     * @return the call, answering the plugin's handle
     */
    public static CoreCall<Long> registerManifest(Manifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        return carryingCallersParts((functions, arena, core, pending) -> {
            MemorySegment plugin = arena.allocate(JAVA_LONG);
            int status = (int) functions.handle(CoreFunction.REGISTER_MANIFEST)
                    .invokeExact(core, Marshal.manifest(arena, manifest), plugin, pending);
            return new Crossed<>(status, plugin.get(JAVA_LONG, 0));
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
        return builtByTheLayer((functions, arena, core, pending) -> new Crossed<>(
                (int) functions.handle(CoreFunction.OPEN_PLUGIN)
                        .invokeExact(core, plugin, Marshal.pluginOptions(arena, payload), pending),
                null));
    }

    /**
     * {@code weivel_release_plugin}.
     *
     * @param plugin the plugin's handle
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> releasePlugin(long plugin) {
        return builtByTheLayer((functions, arena, core, pending) -> new Crossed<>(
                (int) functions.handle(CoreFunction.RELEASE_PLUGIN).invokeExact(core, plugin, pending), null));
    }

    /**
     * {@code weivel_forget_plugin}.
     *
     * @param plugin the plugin's handle
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> forgetPlugin(long plugin) {
        return builtByTheLayer((functions, arena, core, pending) -> new Crossed<>(
                (int) functions.handle(CoreFunction.FORGET_PLUGIN).invokeExact(core, plugin, pending), null));
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
        return carryingCallersParts((functions, arena, core, pending) -> {
            MemorySegment registration = arena.allocate(JAVA_LONG);
            int status = (int) functions.handle(CoreFunction.REGISTER_SERVICE)
                    .invokeExact(core, owner, arena.allocateFrom(id), registration, pending);
            return new Crossed<>(status, registration.get(JAVA_LONG, 0));
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
                (int) functions.handle(CoreFunction.UNREGISTER).invokeExact(core, handle, pending), null));
    }

    /**
     * {@code weivel_handle_state}.
     *
     * @param handle a want or watch handle
     * @return the call, answering the handle's condition, a value of the core's header
     */
    public static CoreCall<Integer> handleState(long handle) {
        return builtByTheLayer((functions, arena, core, pending) -> {
            MemorySegment condition = arena.allocate(JAVA_INT);
            int status = (int) functions.handle(CoreFunction.HANDLE_STATE).invokeExact(core, handle, condition,
                    pending);
            return new Crossed<>(status, condition.get(JAVA_INT, 0));
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
        return carryingCallersParts((functions, arena, core, pending) -> {
            MemorySegment implementation = arena.allocate(JAVA_LONG);
            int status = (int) functions.handle(CoreFunction.GET_SERVICE)
                    .invokeExact(core, plugin, arena.allocateFrom(id), implementation, pending);
            return new Crossed<>(status, implementation.get(JAVA_LONG, 0));
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
        return carryingCallersParts((functions, arena, core, pending) -> {
            MemorySegment want = arena.allocate(JAVA_LONG);
            int status = (int) functions.handle(CoreFunction.WANT_SERVICE).invokeExact(core, plugin,
                    arena.allocateFrom(id), Marshal.wantOptions(arena, options), want, pending);
            return new Crossed<>(status, want.get(JAVA_LONG, 0));
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
        return carryingCallersParts((functions, arena, core, pending) -> {
            MemorySegment watch = arena.allocate(JAVA_LONG);
            int status = (int) functions.handle(CoreFunction.WATCH_SERVICE)
                    .invokeExact(core, plugin, arena.allocateFrom(id), watch, pending);
            return new Crossed<>(status, watch.get(JAVA_LONG, 0));
        });
    }

    /**
     * {@code weivel_next_deadline}; no deadline set is {@link Answer.Absent}.
     *
     * @return the call, answering the deadline in the caller's milliseconds
     */
    public static CoreCall<Long> nextDeadline() {
        return builtByTheLayer((functions, arena, core, pending) -> {
            MemorySegment deadline = arena.allocate(JAVA_LONG);
            int status = (int) functions.handle(CoreFunction.NEXT_DEADLINE).invokeExact(core, deadline, pending);
            return new Crossed<>(status, deadline.get(JAVA_LONG, 0));
        });
    }

    /**
     * {@code weivel_advance}. It hands the core a time itself, so it is not preceded by the check for a due deadline.
     *
     * @param now the caller's time now
     * @return the call, answering nothing beyond its success
     */
    public static CoreCall<Void> advance(long now) {
        return new CoreCall<>(advanceCrossing(now), true, true);
    }

    static Crossing<Void> advanceCrossing(long now) {
        return (functions, arena, core, pending) -> new Crossed<>(
                (int) functions.handle(CoreFunction.ADVANCE).invokeExact(core, now, pending), null);
    }

    private static <T> CoreCall<T> builtByTheLayer(Crossing<T> crossing) {
        return new CoreCall<>(crossing, false, false);
    }

    private static <T> CoreCall<T> carryingCallersParts(Crossing<T> crossing) {
        return new CoreCall<>(crossing, true, false);
    }
}
