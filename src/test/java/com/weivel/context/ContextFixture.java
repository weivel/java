package com.weivel.context;

import static com.weivel.core.CoreProbes.ok;

import com.weivel.core.Answer;
import com.weivel.core.CallSteps;
import com.weivel.core.Choice;
import com.weivel.core.Core;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreEvent;
import com.weivel.core.CoreInternals;
import com.weivel.core.CoreOptions;
import com.weivel.core.CoreProbes;
import com.weivel.core.DeferredStep;
import com.weivel.core.ErrorSink;
import com.weivel.core.Failure;
import com.weivel.core.FailureKind;
import com.weivel.core.Manifest;
import com.weivel.core.NamedNumber;
import com.weivel.core.QuarantineReason;
import com.weivel.handles.Handles;
import com.weivel.timer.CoreTimer;
import com.weivel.timer.Scheduler;
import com.weivel.timer.TestScheduler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** A real core with its handles, its timer on a scheduler the test owns, and the contexts made over them. */
final class ContextFixture implements AutoCloseable {
    static final CoreOptions LATENCY_VOCABULARY = new CoreOptions(0, 0, 0, Collections.emptyList(),
            Collections.emptyList(),
            Collections.singletonList(new CoreOptions.ConstraintEntry("latency", "latency", 2)));

    final List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
    final List<LogRecord> logs = Collections.synchronizedList(new ArrayList<>());
    final List<CoreEvent> heard = Collections.synchronizedList(new ArrayList<>());
    volatile Runnable onReport = () -> { };
    volatile Runnable onLog = () -> { };
    final CoreProbes.Crossings crossings = new CoreProbes.Crossings();
    final TestScheduler scheduler = new TestScheduler();
    final Core core;
    final Handles handles = new Handles();
    final CoreTimer timer;
    final PluginContexts contexts;
    private final Map<PluginContext, Long> plugins = Collections.synchronizedMap(new HashMap<>());

    ContextFixture() {
        this(CoreOptions.defaults(), false, null);
    }

    ContextFixture(CoreOptions options) {
        this(options, false, null);
    }

    ContextFixture(CoreOptions options, boolean incompleteQuestions, Scheduler chosen) {
        ErrorSink sink = failure -> {
            onReport.run();
            failures.add(failure);
        };
        core = incompleteQuestions
                ? CoreProbes.withIncompleteCapacityQuestions(crossings, options, heard::add, sink)
                : CoreProbes.counted(crossings, options, heard::add, sink);
        timer = ok(CoreTimer.start(core, chosen == null ? scheduler : chosen));
        contexts = PluginContexts.create(core, handles, timer, record -> {
            onLog.run();
            logs.add(record);
        });
    }

    /** Registers the manifest and makes the plugin's context, without opening it. */
    PluginContext registered(Manifest manifest) {
        long plugin = ok(core.call(CoreCalls.registerManifest(manifest)));
        PluginContext context = contexts.make(plugin, manifest);
        plugins.put(context, plugin);
        return context;
    }

    /** Registers the manifest, makes the plugin's context and opens it with the context's payload. */
    PluginContext opened(Manifest manifest) {
        PluginContext context = registered(manifest);
        open(context);
        return context;
    }

    void open(PluginContext context) {
        ok(contexts.open(context));
    }

    long plugin(PluginContext context) {
        return plugins.get(context);
    }

    void verify(PluginContext context) {
        ok(core.call(CoreCalls.verifyActivation(plugin(context))));
    }

    /** Releases the plugin in the core and ends its entries once the release's own events are dispatched. */
    void release(PluginContext context) {
        ok(CoreInternals.callThenDefer(core, CoreCalls.releasePlugin(plugin(context)), CallSteps.none(),
                endingEntriesOf(context)));
    }

    /** Quarantines the plugin in the core and ends its entries once the quarantine's own events are dispatched. */
    void quarantine(PluginContext context) {
        ok(CoreInternals.callThenDefer(core, CoreCalls.quarantinePlugin(plugin(context),
                Optional.of(new QuarantineReason(0, "quarantined on purpose", "open it again"))), CallSteps.none(),
                endingEntriesOf(context)));
    }

    private DeferredStep endingEntriesOf(PluginContext context) {
        return () -> {
            contexts.endEntries(context);
            return DeferredStep.Status.DONE;
        };
    }

    List<Failure> failuresOf(FailureKind kind) {
        synchronized (failures) {
            return failures.stream().filter(failure -> failure.kind() == kind).collect(Collectors.toList());
        }
    }

    @Override
    public void close() {
        timer.destroy();
        core.destroy();
        contexts.close();
        core.close();
    }

    static Manifest providing(String importName, String... serviceIds) {
        return new Manifest(importName, 0, Optional.empty(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(),
                java.util.Arrays.stream(serviceIds).map(id -> new Manifest.ServiceOffer(id,
                        Collections.emptyList())).collect(Collectors.toList()),
                Collections.emptyList(), Collections.emptyList(), Optional.empty());
    }

    static Manifest consuming(String importName, String... serviceIds) {
        return new Manifest(importName, 0, Optional.empty(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                java.util.Arrays.stream(serviceIds).map(ContextFixture::wanted).collect(Collectors.toList()),
                        Collections.emptyList(),
                Optional.empty());
    }

    static Manifest constrained(String importName, String serviceId, double latency, Choice degradable) {
        Manifest.ServiceWant want = new Manifest.ServiceWant(serviceId, Optional.empty(), Choice.YES, degradable,
                Collections.singletonList(new NamedNumber("latency", latency)));
        return new Manifest(importName, 0, Optional.empty(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(want), Collections.emptyList(), Optional.empty());
    }

    static Manifest capabilities(String importName, List<String> provides, List<String> consumes) {
        return new Manifest(importName, 0, Optional.empty(), Collections.emptyList(), provides, consumes,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Optional.empty());
    }

    static Manifest.ServiceWant wanted(String id) {
        return new Manifest.ServiceWant(id, Optional.empty(), Choice.YES, Choice.NO, Collections.emptyList());
    }

    static <T> T value(Answer<T> answer) {
        return ok(answer);
    }
}
