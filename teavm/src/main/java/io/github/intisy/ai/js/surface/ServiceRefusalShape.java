package io.github.intisy.ai.js.surface;

import io.github.intisy.ai.tsemit.TsInterface;

/** One plugin was refused a service it did not declare. */
@TsInterface(data = true)
public interface ServiceRefusalShape {
    /**
     * The plugin that reached for the service.
     *
     * @return the plugin's id
     */
    String pluginId();

    /**
     * The service it reached for without declaring it.
     *
     * @return the service id
     */
    String serviceId();
}
