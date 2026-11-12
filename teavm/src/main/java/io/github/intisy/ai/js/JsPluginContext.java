package io.github.intisy.ai.js;

import io.github.intisy.ai.engine.ConsumedCapabilities;
import io.github.intisy.ai.engine.EventBus;
import io.github.intisy.ai.engine.Pending;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginSession;
import io.github.intisy.ai.engine.Scheduler;
import io.github.intisy.ai.engine.ServiceHub;
import io.github.intisy.ai.engine.SettingsLayers;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;

/**
 * @implNote Shape only, exactly like {@link JsPluginHost}: it converts a Java list to a JSArray (via
 * {@code services.ids()}), a Pending to a Promise, and a PluginException to a marked JavaScript Error
 * for {@code provide} and for {@code register}, reusing {@link JsPluginHost#serviceEvent} for the
 * watch listener's event string and {@link JsPluginHost#orUndefined} for a missing {@code get}. Every
 * other branch here is the engine's, reached through the session it built.
 *
 * <p>{@code manifest}, {@code log}, {@code paths} and {@code secrets} cross untouched and by identity,
 * because {@code context.manifest} must be the very object a plugin's manifest was parsed from, not a
 * rebuilt copy that merely looks the same. {@code config} is fenced, so a portable write on a host
 * without the layer is refused by {@link SettingsLayers} (invariant 13), and a secret store the
 * runtime does not offer stays absent rather than arriving as undefined.
 */
final class JsPluginContext implements JSObject {

    private JsPluginContext() {
    }

    @JSFunctor
    interface IdFn extends JSObject {
        JSObject call(String id);
    }

    @JSFunctor
    interface IdsFn extends JSObject {
        JSObject call();
    }

    @JSFunctor
    interface WantFn extends JSObject {
        JSObject call(String id, JSObject options);
    }

    @JSFunctor
    interface ServiceListenerFn extends JSObject {
        void call(JSObject service, String event);
    }

    @JSFunctor
    interface WatchFn extends JSObject {
        JsRuntime.Disposer call(String id, ServiceListenerFn listener);
    }

    @JSFunctor
    interface CapabilityListenerFn extends JSObject {
        void call(JSObject implementation, String event);
    }

    @JSFunctor
    interface CapabilityWatchFn extends JSObject {
        JsRuntime.Disposer call(JSObject key, CapabilityListenerFn listener);
    }

    @JSFunctor
    interface KeyFn extends JSObject {
        JSObject call(JSObject key);
    }

    @JSFunctor
    interface RegisterFn extends JSObject {
        JsRuntime.Disposer call(String id, JSObject service);
    }

    @JSFunctor
    interface PayloadListenerFn extends JSObject {
        void call(JSObject payload);
    }

    @JSFunctor
    interface PublishFn extends JSObject {
        void call(String topic, JSObject payload);
    }

    @JSFunctor
    interface SubscribeFn extends JSObject {
        JsRuntime.Disposer call(String topic, PayloadListenerFn listener);
    }

    @JSFunctor
    interface SetFn extends JSObject {
        JSObject call(String key, JSObject value, JSObject options);
    }

    @JSFunctor
    interface ProvideFn extends JSObject {
        void call(JSObject key, JSObject implementation);
    }

    /** Builds the context one plugin's activate receives, from the session {@link JsPluginHost} opened. */
    static JsPluginContext build(final PluginSession session, JsRuntime runtime, JSObject hostDescriptor) {
        final ServiceHub.Registry services = session.getServices();
        final EventBus events = session.getEvents();
        JSObject manifest = (JSObject) session.getFacts().getPayload();

        IdFn get = new IdFn() {
            @Override
            public JSObject call(String id) {
                return JsPluginHost.orUndefined((JSObject) services.get(id));
            }
        };
        WantFn want = new WantFn() {
            @Override
            public JSObject call(String id, JSObject options) {
                Long timeoutMs = timeoutOf(options);
                Pending<Object> pending = timeoutMs == null ? services.want(id) : services.want(id, timeoutMs.longValue());
                return JsPluginHost.promise(pending);
            }
        };
        WatchFn watch = new WatchFn() {
            @Override
            public JsRuntime.Disposer call(String id, final ServiceListenerFn listener) {
                Scheduler.Cancellable cancellable = services.watch(id, new ServiceHub.Listener() {
                    @Override
                    public void changed(Object service, boolean registered) {
                        listener.call(JsPluginHost.orUndefined((JSObject) service), JsPluginHost.serviceEvent(registered));
                    }
                });
                return disposerOf(cancellable);
            }
        };
        RegisterFn register = new RegisterFn() {
            @Override
            public JsRuntime.Disposer call(String id, JSObject service) {
                try {
                    Scheduler.Cancellable cancellable = services.register(id, service);
                    return disposerOf(cancellable);
                } catch (PluginException failure) {
                    JsErrors.raise(JsErrors.of(failure));
                    return null;
                }
            }
        };
        IdsFn ids = new IdsFn() {
            @Override
            public JSObject call() {
                return JsJson.fromStrings(services.ids());
            }
        };
        JSObject servicesObj = servicesObject(get, want, watch, register, ids);

        PublishFn publish = new PublishFn() {
            @Override
            public void call(String topic, JSObject payload) {
                events.publish(topic, payload);
            }
        };
        SubscribeFn subscribe = new SubscribeFn() {
            @Override
            public JsRuntime.Disposer call(String topic, final PayloadListenerFn listener) {
                Scheduler.Cancellable cancellable = events.subscribe(topic, new EventBus.Listener() {
                    @Override
                    public void received(Object payload) {
                        listener.call((JSObject) payload);
                    }
                });
                return disposerOf(cancellable);
            }
        };
        JSObject eventsObj = eventsObject(publish, subscribe);

        ProvideFn provide = new ProvideFn() {
            @Override
            public void call(JSObject key, JSObject implementation) {
                try {
                    session.provide(capabilityId(key), implementation);
                } catch (PluginException failure) {
                    JsErrors.raise(JsErrors.of(failure));
                }
            }
        };

        IdFn capability = new IdFn() {
            @Override
            public JSObject call(String id) {
                return typedKey(id);
            }
        };
        IdFn service = new IdFn() {
            @Override
            public JSObject call(String id) {
                return typedKey(id);
            }
        };
        IdFn topic = new IdFn() {
            @Override
            public JSObject call(String id) {
                return typedKey(id);
            }
        };
        final JsRuntime.Homes registry = runtime.getHomes();
        IdsFn homes = new IdsFn() {
            @Override
            public JSObject call() {
                return homesOf(registry);
            }
        };

        JSObject config = fencedConfig(session.getFacts().getId(), runtime);
        return assemble(manifest, hostDescriptor, config, runtime.getLog(), runtime.getPaths(), runtime.getSecrets(),
                servicesObj, capabilitiesOf(session.getCapabilities()), eventsObj, provide, capability, service, topic,
                homes);
    }

    private static JSObject capabilitiesOf(final ConsumedCapabilities consumed) {
        KeyFn all = new KeyFn() {
            @Override
            public JSObject call(JSObject key) {
                JSArray<JSObject> out = JSArray.create();
                List<Object> implementations = consumed.all(capabilityId(key));
                for (int index = 0; index < implementations.size(); index++) {
                    out.set(index, (JSObject) implementations.get(index));
                }
                return out;
            }
        };
        CapabilityWatchFn watch = new CapabilityWatchFn() {
            @Override
            public JsRuntime.Disposer call(JSObject key, final CapabilityListenerFn listener) {
                return disposerOf(consumed.watch(capabilityId(key), new ConsumedCapabilities.Listener() {
                    @Override
                    public void changed(Object implementation, boolean provided) {
                        listener.call((JSObject) implementation, JsPluginHost.capabilityEvent(provided));
                    }
                }));
            }
        };
        return capabilitiesObject(all, watch);
    }

    private static JSObject fencedConfig(final String pluginId, JsRuntime runtime) {
        final JSObject settings = runtime.getConfig();
        final boolean hostHasPortableLayer = hasPortablePath(runtime.getPaths());
        SetFn set = new SetFn() {
            @Override
            public JSObject call(String key, JSObject value, JSObject options) {
                String layer = isPortableWrite(options) ? SettingsLayers.PORTABLE : null;
                PluginException refusal = SettingsLayers.refusalOf(pluginId, layer, hostHasPortableLayer);
                if (refusal != null) {
                    return JsPluginHost.promise(Pending.<Object>failed(refusal));
                }
                return setThrough(settings, key, value, options);
            }
        };
        return configObject(settings, set);
    }

    @JSBody(params = "paths", script = "return paths !== null && paths !== undefined && paths.portable !== undefined;")
    private static native boolean hasPortablePath(JSObject paths);

    @JSBody(params = "options", script =
            "return options !== null && options !== undefined && options.layer === 'portable';")
    private static native boolean isPortableWrite(JSObject options);

    /** Calls the host's {@code set} with the arity the plugin used, so a two-argument write stays one. */
    @JSBody(params = {"settings", "key", "value", "options"}, script =
            "return options === null || options === undefined ? settings.set(key, value) : settings.set(key, value, options);")
    private static native JSObject setThrough(JSObject settings, String key, JSObject value, JSObject options);

    @JSBody(params = {"settings", "set"}, script =
            "return { all: function () { return settings.all(); }, get: function (key) { return settings.get(key); }, "
            + "set: set };")
    private static native JSObject configObject(JSObject settings, SetFn set);

    /**
     * The typed key for a capability, service or topic id.
     *
     * @implNote The id alone is the whole key at run time; the phantom type the contract declares
     * exists only in the type system, so there is nothing else to carry, and the three key types are
     * therefore one shape rather than three.
     */
    @JSBody(params = "id", script = "return { id: String(id) };")
    private static native JSObject typedKey(String id);

    /**
     * Every home the host's registry knows, or none where it supplied no registry.
     *
     * @implNote The absence check is JavaScript's rather than Java's, because a host that omits the
     * property hands over {@code undefined}, which a Java {@code null} comparison does not catch.
     */
    @JSBody(params = "registry", script =
            "return registry === null || registry === undefined ? [] : registry.all();")
    private static native JSObject homesOf(JsRuntime.Homes registry);

    /**
     * The capability id behind whatever {@code provide} was handed: a typed key's {@code id}, or the
     * value itself when a host passed a bare id.
     */
    @JSBody(params = "key", script =
            "return key !== null && typeof key === 'object' ? String(key.id) : String(key);")
    private static native String capabilityId(JSObject key);

    private static JsRuntime.Disposer disposerOf(final Scheduler.Cancellable cancellable) {
        return new JsRuntime.Disposer() {
            @Override
            public void dispose() {
                cancellable.cancel();
            }
        };
    }

    /** Null when the plugin called {@code want(id)} with no options, or the {@code timeoutMs} it gave. */
    private static Long timeoutOf(JSObject options) {
        if (options == null) {
            return null;
        }
        Object tree = JsJson.toTree(options);
        Object value = tree instanceof Map ? ((Map<?, ?>) tree).get("timeoutMs") : null;
        return value instanceof Number ? Long.valueOf(((Number) value).longValue()) : null;
    }

    @JSBody(params = {"get", "want", "watch", "register", "ids"}, script =
            "return { get: get, want: want, watch: watch, register: register, ids: ids };")
    private static native JSObject servicesObject(IdFn get, WantFn want, WatchFn watch, RegisterFn register, IdsFn ids);

    @JSBody(params = {"all", "watch"}, script = "return { all: all, watch: watch };")
    private static native JSObject capabilitiesObject(KeyFn all, CapabilityWatchFn watch);

    @JSBody(params = {"publish", "subscribe"}, script = "return { publish: publish, subscribe: subscribe };")
    private static native JSObject eventsObject(PublishFn publish, SubscribeFn subscribe);

    @JSBody(params = {"manifest", "host", "config", "log", "paths", "secrets", "services", "capabilities", "events",
            "provide", "capability", "service", "topic", "homes"}, script =
            "var context = { manifest: manifest, host: host, config: config, log: log, paths: paths, "
            + "services: services, capabilities: capabilities, events: events, provide: provide, "
            + "capability: capability, service: service, "
            + "topic: topic, homes: homes };"
            + "if (secrets !== null && secrets !== undefined) { context.secrets = secrets; }"
            + "return context;")
    private static native JsPluginContext assemble(JSObject manifest, JSObject host, JSObject config, JSObject log,
            JSObject paths, JSObject secrets, JSObject services, JSObject capabilities, JSObject events,
            ProvideFn provide, IdFn capability,
            IdFn service, IdFn topic, IdsFn homes);
}
