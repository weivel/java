package com.weivel.conformance;

import com.weivel.context.LogRecord;
import com.weivel.context.PluginContext;
import com.weivel.core.Answer;
import com.weivel.core.CoreBinary;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreOptions;
import com.weivel.core.DescriptionCheck;
import com.weivel.core.EdgeSource;
import com.weivel.core.Failure;
import com.weivel.core.Manifest;
import com.weivel.host.Activation;
import com.weivel.host.Entry;
import com.weivel.host.Host;
import com.weivel.host.HostOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** A host on the real core and the suite's own time, over plugins the driver builds as values, every log kept. */
final class DriverWorld implements AutoCloseable {
    private static final long DESTROY_SECONDS = 10;

    final VirtualTime time = new VirtualTime();
    final Host host;
    private final List<LogRecord> logs = Collections.synchronizedList(new ArrayList<LogRecord>());
    private final List<Failure> failures = Collections.synchronizedList(new ArrayList<Failure>());
    private final Map<String, Long> plugins = new LinkedHashMap<>();
    private final List<Object> timerMoves = new ArrayList<>();
    private int failuresBeforeTheSteps;
    private int logsBeforeTheSteps;
    private Optional<String> loadMistake = Optional.empty();

    DriverWorld(CoreBinary binary, CoreOptions core, DescriptionCheck descriptionCheck) {
        Answer<Host> created = Host.create(binary, HostOptions.defaults().withCore(core).withScheduler(time)
                .withLog(logs::add).withErrorSink(failures::add).withTimerObserver(this::timerMoved)
                .withDescriptionCheck(descriptionCheck));
        if (!(created instanceof Answer.Ok)) {
            throw new IllegalStateException("the driver's host could not be created: " + created);
        }
        host = ((Answer.Ok<Host>) created).value();
    }

    /** An entry whose activate never finishes. */
    static Entry inFlight() {
        return context -> new CompletableFuture<Void>();
    }

    /** Registers a plugin through the host, failing unless the core registered it. */
    void register(Manifest manifest, Entry entry) {
        Answer<Long> registered = host.register(manifest, entry);
        if (!(registered instanceof Answer.Ok)) {
            throw new IllegalStateException("the host did not register " + manifest.importName() + ": " + registered);
        }
        plugins.put(manifest.importName(), ((Answer.Ok<Long>) registered).value());
    }

    /**
     * Opens registered plugins through the host's activation, each entry's activate left in flight, so the steps
     * that follow drive each plugin through its context and nothing settles an activate behind them.
     *
     * @implNote The plan is drawn from services, which a vector that opens plugins this way declares only where the
     * plan may place every plugin, so every one is opened and none is refused for a need.
     */
    void openInFlight(List<String> importNames) {
        openInFlight(importNames, EdgeSource.SERVICES);
    }

    /**
     * Opens registered plugins as {@link #openInFlight(List)} does, the plan drawn from {@code source}: from
     * capabilities, a plugin consuming a service nothing provides yet is opened all the same.
     */
    void openInFlight(List<String> importNames, EdgeSource source) {
        List<Long> handles = new ArrayList<>();
        for (String importName : importNames) {
            handles.add(plugin(importName));
        }
        Answer<Activation> activated = host.activate(handles, source);
        if (!(activated instanceof Answer.Ok)
                || !((Answer.Ok<Activation>) activated).value().plan().order().containsAll(handles)) {
            throw new IllegalStateException("the host did not open every plugin of " + importNames + ": " + activated);
        }
    }

    PluginContext context(String importName) {
        Optional<PluginContext> context = host.context(plugin(importName));
        if (!context.isPresent()) {
            throw new IllegalStateException("the host holds no context for " + importName);
        }
        return context.get();
    }

    long plugin(String importName) {
        Long plugin = plugins.get(importName);
        if (plugin == null) {
            throw new IllegalArgumentException("the vector names a plugin it does not give: " + importName);
        }
        return plugin;
    }

    String nameOf(long plugin) {
        for (Map.Entry<String, Long> registered : plugins.entrySet()) {
            if (registered.getValue() == plugin) {
                return registered.getKey();
            }
        }
        throw new IllegalStateException("the layer named a plugin " + plugin + " the driver never registered");
    }

    List<String> names() {
        return new ArrayList<>(plugins.keySet());
    }

    /** Records one move of the host's timer as {@code [the clock's time, the deadline or null]}. */
    private void timerMoved(OptionalLong deadline) {
        synchronized (timerMoves) {
            timerMoves.add(Arrays.<Object>asList(time.now(),
                    deadline.isPresent() ? (Object) deadline.getAsLong() : StandardJson.NULL));
        }
    }

    void runUntil(long until) {
        time.runUntil(until);
    }

    List<Object> timerMoves() {
        synchronized (timerMoves) {
            return Collections.unmodifiableList(new ArrayList<>(timerMoves));
        }
    }

    List<LogRecord> logs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    List<Failure> failures() {
        synchronized (failures) {
            return new ArrayList<>(failures);
        }
    }

    /** Marks where a vector's steps start: what the setup of its plugins wrote is not reported. */
    void startTheSteps() {
        failuresBeforeTheSteps = failures().size();
        logsBeforeTheSteps = logs().size();
    }

    /** What the error sink and the log received from the first step on, the sink's first. */
    List<Object> reported() {
        List<Object> written = new ArrayList<>();
        List<?> sunk = failures();
        List<?> logged = logs();
        for (Object record : sunk.subList(failuresBeforeTheSteps, sunk.size())) {
            written.add(String.valueOf(record));
        }
        for (Object record : logged.subList(logsBeforeTheSteps, logged.size())) {
            written.add(String.valueOf(record));
        }
        return written;
    }

    /** Records why the host refused a vector's load as the caller's mistake. */
    void loadRefused(String detail) {
        loadMistake = Optional.of(detail);
    }

    Optional<String> loadMistake() {
        return loadMistake;
    }

    /** How many records the log and the error sink hold now, counting only the log's errors when asked. */
    int written(boolean errorsOnly) {
        int count = failures().size();
        for (LogRecord record : logs()) {
            if (!errorsOnly || record.level() == CoreConstants.LOG_ERROR) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void close() throws Exception {
        try {
            host.destroy().toCompletableFuture().get(DESTROY_SECONDS, TimeUnit.SECONDS);
        } finally {
            host.close();
        }
    }
}
