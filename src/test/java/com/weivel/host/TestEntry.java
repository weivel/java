package com.weivel.host;

import com.weivel.context.PluginContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An entry whose activate and deactivate the test chooses, each recorded in the trace the moment the host calls it,
 * with the thread that called its activate kept.
 */
final class TestEntry implements Entry, Deactivating {
    private final String name;
    private final List<String> trace;
    volatile Function<PluginContext, CompletionStage<?>> onActivate =
            context -> CompletableFuture.completedFuture(null);
    volatile Supplier<CompletionStage<?>> onDeactivate = () -> CompletableFuture.completedFuture(null);
    volatile PluginContext context;
    volatile Thread activatedOn;

    TestEntry(String name, List<String> trace) {
        this.name = name;
        this.trace = trace;
    }

    @Override
    public CompletionStage<?> activate(PluginContext given) {
        trace.add("activate " + name);
        context = given;
        activatedOn = Thread.currentThread();
        return onActivate.apply(given);
    }

    @Override
    public CompletionStage<?> deactivate() {
        trace.add("deactivate " + name);
        return onDeactivate.get();
    }

    /** An entry offering no deactivate. */
    static Entry activateOnly(String name, List<String> trace) {
        return context -> {
            trace.add("activate " + name);
            return CompletableFuture.completedFuture(null);
        };
    }
}
