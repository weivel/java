package io.github.intisy.ai.engine;

/** One plugin was refused a service it did not declare. */
public final class ServiceRefusal {

    private final String pluginId;
    private final String serviceId;

    /**
     * @param pluginId the plugin that reached for the service
     * @param serviceId the service id it reached for without declaring it
     */
    public ServiceRefusal(String pluginId, String serviceId) {
        this.pluginId = pluginId;
        this.serviceId = serviceId;
    }

    /** @return the plugin that reached for the service */
    public String getPluginId() {
        return pluginId;
    }

    /** @return the service id it reached for without declaring it */
    public String getServiceId() {
        return serviceId;
    }

    boolean concerns(String candidatePlugin, String candidateService) {
        return pluginId.equals(candidatePlugin) && serviceId.equals(candidateService);
    }
}
