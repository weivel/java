package com.weivel.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A C library's declaration, the standard's {@code weivel_standard_library} as a Java value: the name plugins import
 * it by, its version, the symbol that frees what its bytes functions answer, and the services its functions offer.
 * The standard's reader fills one, or the caller builds it; the declaration names no path, which the caller hands the
 * host beside it.
 */
public final class LibraryDeclaration {
    /** {@code WEIVEL_STANDARD_FUNCTION_BYTES}: opaque bytes in, opaque bytes out. */
    public static final int BYTES = 2;
    /**
     * {@code WEIVEL_STANDARD_FUNCTION_DEFINITION}: one service's whole definition, {@code const void *f(void)}
     * answering a static struct of function pointers that begins with its {@code uint32_t size}.
     */
    public static final int DEFINITION = 3;

    private final String importName;
    private final String version;
    private final Optional<String> release;
    private final List<Service> services;

    /**
     * Checks that every part is present, and copies the list.
     *
     * @param importName the name plugins import the library by
     * @param version its version
     * @param release the symbol that frees every body its bytes functions answer; empty for a library whose every
     *     service is a definition
     * @param services the services its functions offer, in the declaration's order
     */
    public LibraryDeclaration(String importName, String version, Optional<String> release, List<Service> services) {
        this.importName = Objects.requireNonNull(importName, "importName");
        this.version = Objects.requireNonNull(version, "version");
        this.release = Objects.requireNonNull(release, "release");
        this.services = copied(services, "services");
    }

    /**
     * Answers {@code importName}.
     *
     * @return the name plugins import the library by
     */
    public String importName() {
        return importName;
    }

    /**
     * Answers {@code version}.
     *
     * @return the library's version
     */
    public String version() {
        return version;
    }

    /**
     * Answers {@code release}.
     *
     * @return the symbol that frees every body the library's bytes functions answer; empty when it declares none
     */
    public Optional<String> release() {
        return release;
    }

    /**
     * Answers {@code services}.
     *
     * @return the services, unmodifiable
     */
    public List<Service> services() {
        return services;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LibraryDeclaration)) {
            return false;
        }
        LibraryDeclaration that = (LibraryDeclaration) other;
        return importName.equals(that.importName) && version.equals(that.version) && release.equals(that.release)
                && services.equals(that.services);
    }

    @Override
    public int hashCode() {
        return Objects.hash(importName, version, release, services);
    }

    @Override
    public String toString() {
        return "LibraryDeclaration[importName=" + importName + ", version=" + version + ", release=" + release
                + ", services=" + services + "]";
    }

    private static <T> List<T> copied(List<T> items, String name) {
        Objects.requireNonNull(items, name);
        List<T> copy = new ArrayList<>(items);
        for (T item : copy) {
            Objects.requireNonNull(item, "an element of " + name);
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * One service a library declares: the id plugins request, the functions its object offers and, for a definition's
     * service, the symbol answering its description, when it declares one.
     */
    public static final class Service {
        private final String id;
        private final List<Function> functions;
        private final Optional<String> description;

        /**
         * A service that declares no description.
         *
         * @param id the service id plugins request
         * @param functions its functions, in the declaration's order
         */
        public Service(String id, List<Function> functions) {
            this(id, functions, Optional.<String>empty());
        }

        /**
         * Checks that every part is present, and copies the list.
         *
         * @param id the service id plugins request
         * @param functions its functions, in the declaration's order
         * @param description the symbol answering the definition's {@code weivel_standard_description}, so the host
         *     binds the definition from it; empty for a service that declares none
         */
        public Service(String id, List<Function> functions, Optional<String> description) {
            this.id = Objects.requireNonNull(id, "id");
            this.functions = copied(functions, "functions");
            this.description = Objects.requireNonNull(description, "description");
        }

        /**
         * Answers {@code description}.
         *
         * @return the symbol answering the definition's description; empty when the service declares none
         */
        public Optional<String> description() {
            return description;
        }

        /**
         * Answers {@code id}.
         *
         * @return the service id
         */
        public String id() {
            return id;
        }

        /**
         * Answers {@code functions}.
         *
         * @return the functions, unmodifiable
         */
        public List<Function> functions() {
            return functions;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Service && id.equals(((Service) other).id)
                    && functions.equals(((Service) other).functions)
                    && description.equals(((Service) other).description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, functions, description);
        }

        @Override
        public String toString() {
            return "Service[id=" + id + ", functions=" + functions + ", description=" + description + "]";
        }
    }

    /** One function a service offers: the name a plugin calls it by, its C symbol, its kind and the facts it reads. */
    public static final class Function {
        private final String name;
        private final String symbol;
        private final int kind;
        private final int context;

        /**
         * Checks that the texts are present.
         *
         * @param name the name a plugin calls the function by
         * @param symbol the exported C symbol that implements it
         * @param kind how it is called: {@link #BYTES} or {@link #DEFINITION}
         * @param context the facts of the calling process it reads, as the standard's context bits; 0 for none
         */
        public Function(String name, String symbol, int kind, int context) {
            this.name = Objects.requireNonNull(name, "name");
            this.symbol = Objects.requireNonNull(symbol, "symbol");
            this.kind = kind;
            this.context = context;
        }

        /**
         * A function of the bytes kind that reads no fact of the calling process.
         *
         * @param name the name a plugin calls it by
         * @param symbol its C symbol
         * @return the function
         */
        public static Function bytes(String name, String symbol) {
            return new Function(name, symbol, BYTES, 0);
        }

        /**
         * A function of the definition kind, which reads no fact of the calling process, since its struct carries
         * its own context.
         *
         * @param name the name a plugin calls it by
         * @param symbol its C symbol
         * @return the function
         */
        public static Function definition(String name, String symbol) {
            return new Function(name, symbol, DEFINITION, 0);
        }

        /**
         * Answers {@code name}.
         *
         * @return the name a plugin calls the function by
         */
        public String name() {
            return name;
        }

        /**
         * Answers {@code symbol}.
         *
         * @return the exported C symbol
         */
        public String symbol() {
            return symbol;
        }

        /**
         * Answers {@code kind}.
         *
         * @return how the function is called
         */
        public int kind() {
            return kind;
        }

        /**
         * Answers {@code context}.
         *
         * @return the facts of the calling process it reads, as bits
         */
        public int context() {
            return context;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Function)) {
                return false;
            }
            Function that = (Function) other;
            return name.equals(that.name) && symbol.equals(that.symbol) && kind == that.kind
                    && context == that.context;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, symbol, kind, context);
        }

        @Override
        public String toString() {
            return "Function[name=" + name + ", symbol=" + symbol + ", kind=" + kind + ", context=" + context + "]";
        }
    }
}
