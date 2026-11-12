package io.github.intisy.ai.engine;

/**
 * One plugin was refused a service or a capability it did not declare.
 *
 * @implNote Both kinds share one record so a reader of the refusals sees every reach that was
 * refused in the order reported; the kind keeps a service and a capability that share an id apart.
 */
public final class ServiceRefusal {

    private final DeclarationKind kind;
    private final String pluginId;
    private final String serviceId;

    /**
     * @param kind whether the id names a service or a capability
     * @param pluginId the plugin that reached for the id
     * @param serviceId the service or capability id it reached for without declaring it
     */
    public ServiceRefusal(DeclarationKind kind, String pluginId, String serviceId) {
        this.kind = kind;
        this.pluginId = pluginId;
        this.serviceId = serviceId;
    }

    /** @return whether the refused id names a service or a capability */
    public DeclarationKind getKind() {
        return kind;
    }

    /** @return the plugin that reached for the id */
    public String getPluginId() {
        return pluginId;
    }

    /** @return the service or capability id it reached for without declaring it */
    public String getServiceId() {
        return serviceId;
    }

    boolean concerns(DeclarationKind candidateKind, String candidatePlugin, String candidateId) {
        return kind == candidateKind && pluginId.equals(candidatePlugin) && serviceId.equals(candidateId);
    }
}
