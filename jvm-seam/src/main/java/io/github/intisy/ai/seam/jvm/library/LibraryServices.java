package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.Gson;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginHost;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Offers each service an opened library declares to the plugins of a host.
 *
 * @implNote No per-function code exists: each service is a {@link Proxy} of the interface the
 * embedder names for it, and every method forwards to {@link LibraryBridge#call} by the symbol its
 * name is declared under, serialising the argument and deserialising the result with Gson.
 */
public final class LibraryServices {

    private static final String EMBEDDING_HOST = "the embedding host";
    private static final Gson GSON = new Gson();

    /**
     * Which library put each service id on each host, so a later collision can name it.
     *
     * @implNote Weak in the host, so a stopped host and its record go together; the host's own
     * registry cannot say which provider registered an id, only that one did.
     */
    private static final Map<PluginHost, Map<String, String>> PROVIDERS = new WeakHashMap<PluginHost, Map<String, String>>();

    private LibraryServices() {
    }

    /**
     * Registers each declared service through {@link PluginHost#provideService}, or none of them.
     *
     * @param host the host whose plugins the services are offered to
     * @param bridge the opened library the services call into
     * @param declaration the library's verified declaration
     * @param interfacesByServiceId the interface each declared service is offered as, keyed by service id
     * @throws PluginException naming the library, before anything registers, when a service has no
     * interface, an interface asks for a function the library does not declare, or a service id is
     * already provided on the host, naming both providers
     * @implNote A service id the host already offers is refused rather than replaced, because the
     * host registry replaces a service registered twice and the consumers holding the first would
     * lose it. A provider this class did not record is taken to be the embedding host, since a
     * host offers its libraries' services before any plugin activates.
     */
    public static void provide(PluginHost host, LibraryBridge bridge, LibraryDeclaration declaration,
                               Map<String, Class<?>> interfacesByServiceId) {
        String provider = "library " + declaration.id() + " " + declaration.version();
        synchronized (PROVIDERS) {
            Map<String, String> recorded = recordedOn(host);
            Map<String, Object> services = new LinkedHashMap<String, Object>();
            for (LibraryDeclaration.ServiceDeclaration service : declaration.services()) {
                refuseCollision(host, recorded, declaration, provider, service.id());
                services.put(service.id(), proxyOf(bridge, declaration, service, interfaceFor(declaration, service, interfacesByServiceId)));
            }
            for (Map.Entry<String, Object> service : services.entrySet()) {
                host.provideService(service.getKey(), service.getValue());
                recorded.put(service.getKey(), provider);
            }
        }
    }

    private static Map<String, String> recordedOn(PluginHost host) {
        Map<String, String> recorded = PROVIDERS.get(host);
        if (recorded == null) {
            recorded = new HashMap<String, String>();
            PROVIDERS.put(host, recorded);
        }
        return recorded;
    }

    private static void refuseCollision(PluginHost host, Map<String, String> recorded, LibraryDeclaration declaration,
                                        String provider, String serviceId) {
        if (host.service(serviceId) == null) {
            return;
        }
        String existing = recorded.containsKey(serviceId) ? recorded.get(serviceId) : EMBEDDING_HOST;
        throw new PluginException(declaration.id(),
                "service \"" + serviceId + "\" is offered by both " + existing + " and " + provider + ", so the library's copy is refused",
                "rename the service in one library's library.json, or stop " + existing + " offering \"" + serviceId + "\"");
    }

    private static Class<?> interfaceFor(LibraryDeclaration declaration, LibraryDeclaration.ServiceDeclaration service,
                                         Map<String, Class<?>> interfacesByServiceId) {
        Class<?> serviceInterface = interfacesByServiceId.get(service.id());
        if (serviceInterface == null || !serviceInterface.isInterface()) {
            throw new PluginException(declaration.id(),
                    "no interface was given for service \"" + service.id() + "\", so it cannot be offered",
                    "pass the service's generated interface under \"" + service.id() + "\"");
        }
        return serviceInterface;
    }

    private static Object proxyOf(LibraryBridge bridge, LibraryDeclaration declaration,
                                  LibraryDeclaration.ServiceDeclaration service, Class<?> serviceInterface) {
        Map<String, String> symbolsByName = new HashMap<String, String>();
        for (LibraryDeclaration.FunctionDeclaration function : service.functions()) {
            symbolsByName.put(function.name(), function.symbol());
        }
        refuseUndeclaredMethods(declaration, service, serviceInterface, symbolsByName);
        return Proxy.newProxyInstance(serviceInterface.getClassLoader(), new Class<?>[] {serviceInterface},
                new ServiceHandler(bridge, declaration.id() + " service " + service.id(), symbolsByName));
    }

    private static void refuseUndeclaredMethods(LibraryDeclaration declaration, LibraryDeclaration.ServiceDeclaration service,
                                                Class<?> serviceInterface, Map<String, String> symbolsByName) {
        List<String> undeclared = new ArrayList<String>();
        for (Method method : serviceInterface.getMethods()) {
            boolean forwarded = !method.isDefault() && !Modifier.isStatic(method.getModifiers());
            if (forwarded && (!symbolsByName.containsKey(method.getName()) || method.getParameterTypes().length > 1)) {
                undeclared.add(method.getName());
            }
        }
        if (!undeclared.isEmpty()) {
            Collections.sort(undeclared);
            throw new PluginException(declaration.id(),
                    serviceInterface.getName() + " asks service \"" + service.id() + "\" for " + undeclared
                            + ", which library.json does not declare as functions of one argument",
                    "regenerate the interface from this release's library.json, or pin the release it was generated from");
        }
    }

    /** Forwards each method to the library function declared under its name. */
    private static final class ServiceHandler implements InvocationHandler {
        private final LibraryBridge bridge;
        private final String description;
        private final Map<String, String> symbolsByName;

        ServiceHandler(LibraryBridge bridge, String description, Map<String, String> symbolsByName) {
            this.bridge = bridge;
            this.description = description;
            this.symbolsByName = symbolsByName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            Object argument = arguments == null || arguments.length == 0 ? null : arguments[0];
            String result = bridge.call(symbolsByName.get(method.getName()), GSON.toJson(argument));
            return GSON.fromJson(result, method.getGenericReturnType());
        }

        private Object objectMethod(Object proxy, Method method, Object[] arguments) {
            if (method.getName().equals("equals")) {
                return proxy == arguments[0];
            }
            if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            return "library " + description;
        }
    }
}
