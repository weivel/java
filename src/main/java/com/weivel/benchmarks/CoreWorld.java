package com.weivel.benchmarks;

import com.weivel.context.PluginContext;
import com.weivel.context.PluginContexts;
import com.weivel.core.Answer;
import com.weivel.core.Choice;
import com.weivel.core.Core;
import com.weivel.core.CoreBinary;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreOptions;
import com.weivel.core.Manifest;
import com.weivel.core.NamedNumber;
import com.weivel.handles.Handles;
import com.weivel.timer.CoreTimer;
import com.weivel.timer.Schedulers;
import com.weivel.timer.SystemScheduler;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.LongBinaryOperator;

/**
 * A core, its contexts and its timer on a scheduler of its own, composed as a host composes them, with a provider of
 * {@link #ARITHMETIC} and a consumer of it registered and opened.
 */
final class CoreWorld implements AutoCloseable {
    static final String ARITHMETIC = "provider:arithmetic";

    private final SystemScheduler scheduler;
    private final Core core;
    private final CoreTimer timer;
    private final PluginContexts contexts;
    private final PluginContext provider;
    private final PluginContext consumer;
    private final long consumerPlugin;

    private CoreWorld(SystemScheduler scheduler, Core core, CoreTimer timer, PluginContexts contexts) {
        this.scheduler = scheduler;
        this.core = core;
        this.timer = timer;
        this.contexts = contexts;
        Manifest providing = providerManifest();
        Manifest consuming = consumerManifest();
        this.provider = contexts.make(registered(providing), providing);
        this.consumerPlugin = registered(consuming);
        this.consumer = contexts.make(consumerPlugin, consuming);
        ok(contexts.open(provider), "the provider's open");
        ok(contexts.open(consumer), "the consumer's open");
    }

    static CoreWorld open(CoreBinary binary) {
        SystemScheduler scheduler = Schedulers.system();
        Core core = ok(Core.create(binary, CoreOptions.defaults()), "the core's creation");
        CoreTimer timer = ok(CoreTimer.start(core, scheduler), "the timer's start");
        PluginContexts contexts = PluginContexts.create(core, new Handles(), timer, record -> { });
        return new CoreWorld(scheduler, core, timer, contexts);
    }

    Core core() {
        return core;
    }

    PluginContext provider() {
        return provider;
    }

    PluginContext consumer() {
        return consumer;
    }

    long consumerPlugin() {
        return consumerPlugin;
    }

    /** Registers {@code arithmetic} as the provider's {@link #ARITHMETIC}. */
    void provide(LongBinaryOperator arithmetic) {
        ok(provider.register(ARITHMETIC, arithmetic), "the provider's registration");
    }

    private long registered(Manifest manifest) {
        return ok(core.call(CoreCalls.registerManifest(manifest)), "the registration of " + manifest.importName());
    }

    private static Manifest providerManifest() {
        return manifest("provider", Collections.singletonList(ARITHMETIC), Collections.<String>emptyList());
    }

    private static Manifest consumerManifest() {
        return manifest("consumer", Collections.<String>emptyList(), Collections.singletonList(ARITHMETIC));
    }

    static Manifest manifest(String importName, List<String> provides, List<String> consumes) {
        List<Manifest.ServiceOffer> offers = provides.isEmpty() ? Collections.<Manifest.ServiceOffer>emptyList()
                : Collections.singletonList(new Manifest.ServiceOffer(provides.get(0),
                        Collections.<NamedNumber>emptyList()));
        List<Manifest.ServiceWant> wants = consumes.isEmpty() ? Collections.<Manifest.ServiceWant>emptyList()
                : Collections.singletonList(new Manifest.ServiceWant(consumes.get(0), Optional.<String>empty(),
                        Choice.NO, Choice.NO, Collections.<NamedNumber>emptyList()));
        return new Manifest(importName, 0, Optional.<String>empty(), Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(), offers, wants,
                Collections.<String>emptyList(), Optional.<String>empty());
    }

    static <T> T ok(Answer<T> answer, String what) {
        if (!(answer instanceof Answer.Ok)) {
            throw new IllegalStateException(what + " answered " + answer);
        }
        return ((Answer.Ok<T>) answer).value();
    }

    @Override
    public void close() {
        timer.destroy();
        core.destroy();
        contexts.close();
        core.close();
        scheduler.close();
    }
}
