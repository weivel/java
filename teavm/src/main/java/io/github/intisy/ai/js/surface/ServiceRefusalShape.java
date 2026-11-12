package io.github.intisy.ai.js.surface;

import io.github.intisy.ai.tsemit.TsInterface;

/** One plugin was refused a service or a capability it did not declare. */
@TsInterface(data = true)
public interface ServiceRefusalShape {
    /**
     * What the refused id names: service or capability.
     *
     * @return the kind string
     */
    String kind();

    /**
     * The plugin that reached for the id.
     *
     * @return the plugin's id
     */
    String pluginId();

    /**
     * The service or capability it reached for without declaring it.
     *
     * @return the service or capability id
     */
    String serviceId();
}
