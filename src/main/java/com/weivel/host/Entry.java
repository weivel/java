package com.weivel.host;

import com.weivel.context.PluginContext;
import java.util.concurrent.CompletionStage;

/**
 * A plugin's entry in Java: what the host calls to activate it. An entry that also implements {@link Deactivating}
 * offers a deactivate.
 *
 * <p>A class loaded as an entry by name has a public constructor taking nothing.
 */
@FunctionalInterface
public interface Entry {
    /**
     * Activates the plugin. The host calls it once per open, in plan order, and never waits for it before starting the
     * next plugin.
     *
     * @param context the plugin's context, the only way it reaches the core
     * @return a stage completing once the activation is done; one completing exceptionally, a {@code null} stage, or
     *     a throw from this method is a failed activation, which the host quarantines
     */
    CompletionStage<?> activate(PluginContext context);
}
