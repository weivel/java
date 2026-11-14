package com.weivel.host;

import com.weivel.context.PluginContext;
import com.weivel.context.PluginContexts;
import com.weivel.core.Manifest;
import java.util.concurrent.CompletableFuture;

/**
 * What the host keeps of one plugin it registered, from its registration until it is forgotten.
 *
 * @implNote Every field but the context is read and written under the host's lock alone; the context is made once,
 * under this record's own monitor, and never replaced.
 */
final class PluginRecord {
    final long plugin;
    final Manifest manifest;
    final Entry entry;
    private volatile PluginContext context;
    CompletableFuture<Outcome> activation;
    boolean activateCalled;
    CompletableFuture<Void> settling;
    CompletableFuture<Void> release;

    PluginRecord(long plugin, Manifest manifest, Entry entry) {
        this.plugin = plugin;
        this.manifest = manifest;
        this.entry = entry;
    }

    /** The plugin's context, built the first time it is asked for and kept until the plugin is forgotten. */
    PluginContext contextIn(PluginContexts contexts) {
        PluginContext made = context;
        if (made != null) {
            return made;
        }
        synchronized (this) {
            if (context == null) {
                context = contexts.make(plugin, manifest);
            }
            return context;
        }
    }

    PluginContext context() {
        return context;
    }
}
