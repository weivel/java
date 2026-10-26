package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginHost;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offers each service an opened library declares to the plugins of a host.
 *
 * @implNote No per-function code exists: each service is a {@link Proxy} of the interface the
 * embedder names for it, and every method forwards to {@link LibraryBridge#call} by the symbol its
 * name is declared under, serialising the argument and deserialising the result with Gson.
 */
public final class LibraryServices {

    private static final Gson GSON = new Gson();
    private static final String EMBEDDING_HOST = "the embedding host";

    private final PluginHost host;
    private final Map<String, String> librariesByServiceId = new HashMap<String, String>();

    /** @param host the host whose plugins this instance offers library services to; hold one instance per host */
    public LibraryServices(PluginHost host) {
        this.host = host;
    }

    /**
     * What an embedder does with one pin: acquires the release, opens it, and offers every service
     * it declares, or nothing.
     *
     * @param pin the release to load and the digest of each of its files
     * @param cacheDirectory where verified releases are kept between runs
     * @param interfacesByServiceId the interface each declared service is offered as, keyed by service id
     * @return the opened library, which the embedder closes when the host stops
     * @throws PluginException naming the release or the library when acquiring, opening or offering
     * it is refused; nothing is then left open or registered
     */
    public synchronized LibraryBridge load(LibraryPin pin, Path cacheDirectory, Map<String, Class<?>> interfacesByServiceId) {
        return load(new LibraryAcquirer(), pin, cacheDirectory, interfacesByServiceId);
    }

    synchronized LibraryBridge load(LibraryAcquirer acquirer, LibraryPin pin, Path cacheDirectory,
                                    Map<String, Class<?>> interfacesByServiceId) {
        LibraryAcquirer.AcquiredLibrary acquired = acquirer.acquire(pin, cacheDirectory);
        LibraryBridge bridge = LibraryBridge.open(acquired.binaryPath(), acquired.declaration());
        try {
            register(bridge, acquired.declaration(), interfacesByServiceId, providerOf(acquired.declaration(), pin));
        } catch (RuntimeException refused) {
            bridge.close();
            throw refused;
        }
        return bridge;
    }

    /**
     * Registers each declared service through {@link PluginHost#provideService}, or none of them.
     *
     * @param bridge the opened library the services call into
     * @param declaration the library's verified declaration
     * @param interfacesByServiceId the interface each declared service is offered as, keyed by service id
     * @throws PluginException naming the library, before anything registers, when a service has no
     * interface, an interface asks for a function the library does not declare, or a service id is
     * already provided on the host
     */
    public synchronized void provide(LibraryBridge bridge, LibraryDeclaration declaration,
                                     Map<String, Class<?>> interfacesByServiceId) {
        register(bridge, declaration, interfacesByServiceId, "library " + declaration.id() + " " + declaration.version());
    }

    private static String providerOf(LibraryDeclaration declaration, LibraryPin pin) {
        return "library " + declaration.id() + " (" + pin.label() + ")";
    }

    /**
     * @implNote A service id the host already offers is refused rather than replaced, because the
     * host registry replaces a service registered twice and the consumers holding the first would
     * lose it. One colliding service refuses the whole library, since a library is one release unit
     * and a partial offer would split it silently. Only a library this instance registered can be
     * named as the earlier provider: the host registry says that an id is taken, not by whom.
     */
    private void register(LibraryBridge bridge, LibraryDeclaration declaration,
                          Map<String, Class<?>> interfacesByServiceId, String provider) {
        Map<String, Object> services = new LinkedHashMap<String, Object>();
        for (LibraryDeclaration.ServiceDeclaration service : declaration.services()) {
            refuseCollision(declaration, provider, service.id());
            services.put(service.id(), proxyOf(bridge, declaration, service, interfaceFor(declaration, service, interfacesByServiceId)));
        }
        for (Map.Entry<String, Object> service : services.entrySet()) {
            host.provideService(service.getKey(), service.getValue());
            librariesByServiceId.put(service.getKey(), provider);
        }
    }

    private void refuseCollision(LibraryDeclaration declaration, String provider, String serviceId) {
        if (host.service(serviceId) == null) {
            return;
        }
        String existing = librariesByServiceId.containsKey(serviceId) ? librariesByServiceId.get(serviceId) : EMBEDDING_HOST;
        throw new PluginException(declaration.id(),
                "service \"" + serviceId + "\" is offered by both " + existing + " and " + provider
                        + ", so the library is refused with every service it offers",
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
                new ServiceHandler(bridge, declaration.id(), service.id(), symbolsByName));
    }

    /**
     * @implNote A default method is refused like an undeclared one: a JDK proxy hands it to the
     * invocation handler too, so it would reach the library rather than run its own body.
     */
    private static void refuseUndeclaredMethods(LibraryDeclaration declaration, LibraryDeclaration.ServiceDeclaration service,
                                                Class<?> serviceInterface, Map<String, String> symbolsByName) {
        List<String> undeclared = new ArrayList<String>();
        for (Method method : serviceInterface.getMethods()) {
            boolean declared = symbolsByName.containsKey(method.getName()) && method.getParameterTypes().length <= 1;
            if (!Modifier.isStatic(method.getModifiers()) && (method.isDefault() || !declared)) {
                undeclared.add(method.getName());
            }
        }
        if (!undeclared.isEmpty()) {
            Collections.sort(undeclared);
            throw new PluginException(declaration.id(),
                    serviceInterface.getName() + " asks service \"" + service.id() + "\" for " + undeclared
                            + ", which library.json does not declare as functions of one argument, or which are default methods a proxy cannot run",
                    "regenerate the interface from this release's library.json, or pin the release it was generated from");
        }
    }

    /** Forwards each method to the library function declared under its name. */
    private static final class ServiceHandler implements InvocationHandler {
        private final LibraryBridge bridge;
        private final String libraryId;
        private final String serviceId;
        private final Map<String, String> symbolsByName;

        ServiceHandler(LibraryBridge bridge, String libraryId, String serviceId, Map<String, String> symbolsByName) {
            this.bridge = bridge;
            this.libraryId = libraryId;
            this.serviceId = serviceId;
            this.symbolsByName = symbolsByName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            Object argument = arguments == null || arguments.length == 0 ? null : arguments[0];
            String result = bridge.call(symbolsByName.get(method.getName()), GSON.toJson(argument));
            return resultAs(method, result);
        }

        /**
         * @implNote A JSON {@code null} reads as Java null for any type, and the proxy then fails
         * unboxing it into a primitive with a bare {@code NullPointerException}, so that case is
         * refused here alongside a result Gson cannot fit.
         */
        private Object resultAs(Method method, String result) {
            Type returnType = method.getGenericReturnType();
            Object converted;
            try {
                converted = GSON.fromJson(result, returnType);
            } catch (JsonParseException unfitting) {
                throw unfitting(method, returnType, unfitting.getMessage());
            }
            Class<?> returnClass = method.getReturnType();
            if (converted == null && returnClass.isPrimitive() && returnClass != void.class) {
                throw unfitting(method, returnType, "it answered null");
            }
            return converted;
        }

        private PluginException unfitting(Method method, Type returnType, String reason) {
            return new PluginException(libraryId,
                    serviceId + "." + method.getName() + " answered a result that does not fit " + returnType.getTypeName() + ": " + reason,
                    "regenerate the interface from this release's library.json, or report it to the maintainers of " + libraryId
                            + " if its answer breaks its declared result schema");
        }

        private Object objectMethod(Object proxy, Method method, Object[] arguments) {
            if (method.getName().equals("equals")) {
                return proxy == arguments[0];
            }
            if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            return "library " + libraryId + " service " + serviceId;
        }
    }
}
