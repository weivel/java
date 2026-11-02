package io.github.intisy.ai.seam.jvm.context;

import io.github.intisy.ai.api.ServiceType;
import io.github.intisy.ai.api.Services;
import io.github.intisy.ai.api.WantOptions;
import io.github.intisy.ai.engine.Pending;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.Scheduler;
import io.github.intisy.ai.engine.ServiceHub;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

/**
 * The contract's typed services over a session's registry, which works in ids.
 *
 * @implNote Every cast here is the typed key's promise that its id names a {@code T}, which is the
 * contract's to make and the registry's not to know.
 */
final class SessionServices implements Services {

    private final ServiceHub.Registry registry;

    SessionServices(ServiceHub.Registry registry) {
        this.registry = registry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(ServiceType<T> type) {
        return (T) registry.get(type.id());
    }

    @Override
    public <T> CompletionStage<T> want(ServiceType<T> type) {
        return stageOf(registry.want(type.id()));
    }

    @Override
    public <T> CompletionStage<T> want(ServiceType<T> type, WantOptions options) {
        Long timeoutMs = options == null ? null : options.timeoutMs();
        Pending<Object> pending = timeoutMs == null ? registry.want(type.id()) : registry.want(type.id(), timeoutMs.longValue());
        return stageOf(pending);
    }

    /**
     * @implNote The event words are the ones TeaVM's context renders, {@code register} and
     * {@code unregister}, so a listener hears the same words in both builds.
     */
    @Override
    public <T> Runnable watch(ServiceType<T> type, final BiConsumer<T, String> listener) {
        return cancelling(registry.watch(type.id(), new ServiceHub.Listener() {
            @Override
            @SuppressWarnings("unchecked")
            public void changed(Object service, boolean registered) {
                listener.accept((T) service, registered ? "register" : "unregister");
            }
        }));
    }

    @Override
    public <T> Runnable register(ServiceType<T> type, T implementation) {
        return cancelling(registry.register(type.id(), implementation));
    }

    @Override
    public List<String> ids() {
        return registry.ids();
    }

    static Runnable cancelling(final Scheduler.Cancellable cancellable) {
        return new Runnable() {
            @Override
            public void run() {
                cancellable.cancel();
            }
        };
    }

    private static <T> CompletionStage<T> stageOf(Pending<Object> pending) {
        final CompletableFuture<T> stage = new CompletableFuture<T>();
        pending.then(new Pending.Settlement<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public void value(Object value) {
                stage.complete((T) value);
            }

            @Override
            public void failure(PluginException reason) {
                stage.completeExceptionally(reason);
            }
        });
        return stage;
    }
}
