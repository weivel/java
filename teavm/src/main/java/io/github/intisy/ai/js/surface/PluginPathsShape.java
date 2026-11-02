package io.github.intisy.ai.js.surface;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;

/** The storage directories of the home a plugin runs in. */
@TsInterface(data = true)
public interface PluginPathsShape {
    /**
     * The app home directory.
     *
     * @return the home directory path
     */
    String home();

    /**
     * Where plugin checkouts live.
     *
     * @return the repos directory path
     */
    String repos();

    /**
     * Where deployed bundles and their manifest sidecars live.
     *
     * @return the plugin directory path
     */
    String plugin();

    /**
     * Where cached downloads live.
     *
     * @return the cache directory path
     */
    String cache();

    /**
     * Where configuration files live.
     *
     * @return the config directory path
     */
    String config();

    /**
     * The plugin's own folder in the user's portable home, present only on a host with a portable
     * settings layer.
     *
     * @return the portable folder path, or absent on a host without a portable layer
     */
    @TsOptional
    String portable();
}
