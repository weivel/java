package io.github.intisy.ai.seam.jvm.context;

import io.github.intisy.ai.api.HomeDescriptor;
import io.github.intisy.ai.api.PluginConfig;
import io.github.intisy.ai.api.PluginPaths;
import io.github.intisy.ai.api.PluginSecrets;
import io.github.intisy.ai.api.seam.Logger;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * What a JVM host supplies per plugin, from which {@link JvmPluginContext} builds its context.
 *
 * @implNote The JVM counterpart of the TypeScript engine's {@code PluginRuntime}. The optional
 * members are added with {@code with} rather than passed as nulls, so a host that offers no secret
 * store says nothing about one, and the context then carries none.
 */
public final class ContextRuntime {

    private static final Supplier<List<HomeDescriptor>> NO_HOMES = new Supplier<List<HomeDescriptor>>() {
        @Override
        public List<HomeDescriptor> get() {
            return Collections.emptyList();
        }
    };

    private final PluginConfig config;
    private final Logger log;
    private final PluginPaths paths;
    private final PluginSecrets secrets;
    private final Supplier<List<HomeDescriptor>> homes;

    private ContextRuntime(PluginConfig config, Logger log, PluginPaths paths, PluginSecrets secrets,
            Supplier<List<HomeDescriptor>> homes) {
        this.config = config;
        this.log = log;
        this.paths = paths;
        this.secrets = secrets;
        this.homes = homes;
    }

    /**
     * A runtime offering no secret store and knowing of no home but the plugin's own.
     *
     * @param config the plugin's settings, already resolved by the host
     * @param log the plugin's logger
     * @param paths the plugin's storage paths; a portable path among them is the portable layer
     * @return the runtime
     */
    public static ContextRuntime of(PluginConfig config, Logger log, PluginPaths paths) {
        return new ContextRuntime(config, log, paths, null, NO_HOMES);
    }

    /**
     * @param store the plugin's secrets, already scoped to its id
     * @return a copy of this runtime offering that secret store
     */
    public ContextRuntime withSecrets(PluginSecrets store) {
        return new ContextRuntime(config, log, paths, store, homes);
    }

    /**
     * @param registry every app home the host knows about, asked on each call
     * @return a copy of this runtime answering homes from that registry
     */
    public ContextRuntime withHomes(Supplier<List<HomeDescriptor>> registry) {
        return new ContextRuntime(config, log, paths, secrets, registry);
    }

    PluginConfig config() {
        return config;
    }

    Logger log() {
        return log;
    }

    PluginPaths paths() {
        return paths;
    }

    PluginSecrets secrets() {
        return secrets;
    }

    List<HomeDescriptor> homes() {
        return homes.get();
    }
}
