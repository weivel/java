package io.github.intisy.ai.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The things a host decides with, plus the manifest it decided from.
 *
 * @implNote The payload travels through untouched and is handed back by identity, which is what lets
 * this module stay free of a manifest TYPE: a JavaScript host passes the object it parsed and gets
 * the same object on the session, and a JVM host passes its own manifest class. Adding the typed
 * manifest here would give this module a dependency and give neither caller anything.
 */
public final class ManifestFacts {

    private final String id;
    private final int api;
    private final List<String> capabilities;
    private final List<String> permissions;
    private final List<String> targets;
    private final List<String> servicesConsumed;
    private final List<String> servicesProvided;
    private final Object payload;

    /**
     * @param id the plugin's id
     * @param api the api version the plugin declares it needs
     * @param capabilities the capability ids the plugin declares, copied so later mutation of the argument has no effect
     * @param permissions the permission ids the plugin declares, copied so later mutation of the argument has no effect
     * @param payload the manifest object these facts were extracted from, kept by identity for {@link #getPayload}
     * @implNote Declaring no targeting means every application, which is what keeps the fact
     * additive: every manifest written before it existed stays loadable everywhere.
     */
    public ManifestFacts(String id, int api, List<String> capabilities, List<String> permissions, Object payload) {
        this(id, api, capabilities, permissions, null, payload);
    }

    /**
     * @param id the plugin's id
     * @param api the api version the plugin declares it needs
     * @param capabilities the capability ids the plugin declares, copied so later mutation of the argument has no effect
     * @param permissions the permission ids the plugin declares, copied so later mutation of the argument has no effect
     * @param targets the application ids this plugin is for, copied; empty or null means every application
     * @param payload the manifest object these facts were extracted from, kept by identity for {@link #getPayload}
     */
    public ManifestFacts(String id, int api, List<String> capabilities, List<String> permissions,
                         List<String> targets, Object payload) {
        this(id, api, copy(capabilities), copy(permissions), copy(targets),
                new ArrayList<String>(), new ArrayList<String>(), payload);
    }

    private ManifestFacts(String id, int api, List<String> capabilities, List<String> permissions, List<String> targets,
                          List<String> servicesConsumed, List<String> servicesProvided, Object payload) {
        this.id = id;
        this.api = api;
        this.capabilities = capabilities;
        this.permissions = permissions;
        this.targets = targets;
        this.servicesConsumed = servicesConsumed;
        this.servicesProvided = servicesProvided;
        this.payload = payload;
    }

    /**
     * These facts with the services the manifest declares, which are the only ones the plugin may reach.
     *
     * @param consumes the entries of {@code services.consumes}, each a bare id or the object form; null for none
     * @param provides the entries of {@code services.provides}, each a bare id or the object form; null for none
     * @return a copy of these facts carrying the declared service ids
     * @implNote Read defensively, because a host is handed manifests nobody validated: an entry
     * with no id is skipped, so a malformed declaration reaches nothing rather than failing the host.
     */
    public ManifestFacts withServices(List<?> consumes, List<?> provides) {
        return new ManifestFacts(id, api, capabilities, permissions, targets,
                serviceIds(consumes), serviceIds(provides), payload);
    }

    /** @return the plugin's id */
    public String getId() {
        return id;
    }

    /** @return the api version the plugin declares it needs */
    public int getApi() {
        return api;
    }

    /** @return the capability ids the plugin declares, or empty when it declares none */
    public List<String> getCapabilities() {
        return Collections.unmodifiableList(capabilities);
    }

    /** @return the permission ids the plugin declares, or empty when it declares none */
    public List<String> getPermissions() {
        return Collections.unmodifiableList(permissions);
    }

    /** @return the application ids this plugin is for, or empty when it is for every application */
    public List<String> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    /** @return the service ids the plugin declares under {@code services.consumes}, or empty when it declares none */
    public List<String> getServicesConsumed() {
        return Collections.unmodifiableList(servicesConsumed);
    }

    /** @return the service ids the plugin declares under {@code services.provides}, or empty when it declares none */
    public List<String> getServicesProvided() {
        return Collections.unmodifiableList(servicesProvided);
    }

    /** @return the manifest object this instance was built from, handed back by identity */
    public Object getPayload() {
        return payload;
    }

    private static List<String> serviceIds(List<?> declarations) {
        List<String> ids = new ArrayList<String>();
        if (declarations == null) {
            return ids;
        }
        for (Object declaration : declarations) {
            Object id = declaration instanceof Map ? ((Map<?, ?>) declaration).get("id") : declaration;
            if (id instanceof String) {
                ids.add((String) id);
            }
        }
        return ids;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<String>() : new ArrayList<String>(values);
    }
}
