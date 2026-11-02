package io.github.intisy.ai.seam.jvm.context;

import io.github.intisy.ai.api.ConfigSetOptions;
import io.github.intisy.ai.api.PluginConfig;
import io.github.intisy.ai.api.SettingsLayer;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.SettingsLayers;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** A host's settings, with a portable write refused where the host has no portable layer. */
final class FencedConfig implements PluginConfig {

    private final String pluginId;
    private final PluginConfig settings;
    private final boolean hostHasPortableLayer;

    FencedConfig(String pluginId, PluginConfig settings, boolean hostHasPortableLayer) {
        this.pluginId = pluginId;
        this.settings = settings;
        this.hostHasPortableLayer = hostHasPortableLayer;
    }

    @Override
    public Map<String, Object> all() {
        return settings.all();
    }

    @Override
    public <T> T get(String key) {
        return settings.<T>get(key);
    }

    @Override
    public CompletionStage<Void> set(String key, Object value) {
        return settings.set(key, value);
    }

    @Override
    public CompletionStage<Void> set(String key, Object value, ConfigSetOptions options) {
        PluginException refusal = SettingsLayers.refusalOf(pluginId, wireNameOf(options), hostHasPortableLayer);
        if (refusal == null) {
            return settings.set(key, value, options);
        }
        CompletableFuture<Void> refused = new CompletableFuture<Void>();
        refused.completeExceptionally(refusal);
        return refused;
    }

    private static String wireNameOf(ConfigSetOptions options) {
        return options != null && options.layer() == SettingsLayer.PORTABLE ? SettingsLayers.PORTABLE : null;
    }
}
