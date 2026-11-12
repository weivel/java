package io.github.intisy.ai.seam.jvm.context;

import io.github.intisy.ai.api.Capabilities;
import io.github.intisy.ai.api.CapabilityType;
import io.github.intisy.ai.api.Events;
import io.github.intisy.ai.api.HomeDescriptor;
import io.github.intisy.ai.api.HostDescriptor;
import io.github.intisy.ai.api.PluginConfig;
import io.github.intisy.ai.api.PluginContext;
import io.github.intisy.ai.api.PluginManifest;
import io.github.intisy.ai.api.PluginPaths;
import io.github.intisy.ai.api.PluginSecrets;
import io.github.intisy.ai.api.ServiceType;
import io.github.intisy.ai.api.Services;
import io.github.intisy.ai.api.TopicType;
import io.github.intisy.ai.api.seam.Logger;
import io.github.intisy.ai.engine.PluginSession;
import java.util.List;

/**
 * The context one plugin's activation receives on the JVM, built over the session its host opened.
 *
 * @implNote The JVM counterpart of TeaVM's {@code JsPluginContext}. {@code manifest}, {@code log},
 * {@code paths} and {@code secrets} are handed over by identity (invariant 4); {@code config} is
 * fenced so a portable write on a host without the layer is refused (invariant 13), and a secret
 * store the runtime does not offer stays absent rather than arriving as a stub.
 */
public final class JvmPluginContext implements PluginContext {

    private final PluginSession session;
    private final HostDescriptor host;
    private final ContextRuntime runtime;
    private final PluginConfig config;
    private final Services services;
    private final Capabilities capabilities;
    private final Events events;

    private JvmPluginContext(PluginSession session, HostDescriptor host, ContextRuntime runtime) {
        this.session = session;
        this.host = host;
        this.runtime = runtime;
        boolean hostHasPortableLayer = runtime.paths().portable() != null;
        this.config = new FencedConfig(session.getFacts().getId(), runtime.config(), hostHasPortableLayer);
        this.services = new SessionServices(session.getServices());
        this.capabilities = new SessionCapabilities(session.getCapabilities());
        this.events = new SessionEvents(session.getEvents());
    }

    /**
     * @param session the session the host opened for this plugin, already fenced and attributed
     * @param host what this plugin may know about the host
     * @param runtime what the host supplies to this plugin
     * @return the plugin's context
     */
    public static PluginContext contextFor(PluginSession session, HostDescriptor host, ContextRuntime runtime) {
        return new JvmPluginContext(session, host, runtime);
    }

    @Override
    public <T> void provide(CapabilityType<T> type, T implementation) {
        session.provide(type.id(), implementation);
    }

    @Override
    public <T> CapabilityType<T> capability(final String id) {
        return new CapabilityType<T>() {
            @Override
            public String id() {
                return id;
            }
        };
    }

    @Override
    public <T> ServiceType<T> service(final String id) {
        return new ServiceType<T>() {
            @Override
            public String id() {
                return id;
            }
        };
    }

    @Override
    public <T> TopicType<T> topic(final String id) {
        return new TopicType<T>() {
            @Override
            public String id() {
                return id;
            }
        };
    }

    @Override
    public List<HomeDescriptor> homes() {
        return runtime.homes();
    }

    @Override
    public HostDescriptor host() {
        return host;
    }

    @Override
    public PluginConfig config() {
        return config;
    }

    @Override
    public Logger log() {
        return runtime.log();
    }

    @Override
    public PluginPaths paths() {
        return runtime.paths();
    }

    @Override
    public PluginSecrets secrets() {
        return runtime.secrets();
    }

    @Override
    public PluginManifest manifest() {
        return (PluginManifest) session.getFacts().getPayload();
    }

    @Override
    public Services services() {
        return services;
    }

    @Override
    public Capabilities capabilities() {
        return capabilities;
    }

    @Override
    public Events events() {
        return events;
    }
}
