package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A plugin's manifest as a value, mirroring the core's {@code weivel_manifest}. The core parses no file and checks
 * only its shape; the layer hands it to the core unchanged, so a shape the core refuses is its caller's mistake.
 */
public final class Manifest {
    private final String importName;
    private final int api;
    private final Optional<String> entry;
    private final List<String> targets;
    private final List<String> capabilitiesProvided;
    private final List<String> capabilitiesConsumed;
    private final List<ServiceOffer> servicesProvided;
    private final List<ServiceWant> servicesConsumed;
    private final List<String> permissions;
    private final Optional<String> identity;

    /**
     * Checks every part and copies every list, so a caller's later change reaches nothing.
     *
     * @param importName the import name
     * @param api the api level needed
     * @param entry the entry, or empty
     * @param targets the applications targeted
     * @param capabilitiesProvided the capabilities provided
     * @param capabilitiesConsumed the capabilities consumed
     * @param servicesProvided the services provided
     * @param servicesConsumed the services consumed
     * @param permissions the permissions asked for
     * @param identity the identity, or empty
     */
    public Manifest(String importName, int api, Optional<String> entry, List<String> targets,
            List<String> capabilitiesProvided, List<String> capabilitiesConsumed, List<ServiceOffer> servicesProvided,
            List<ServiceWant> servicesConsumed, List<String> permissions, Optional<String> identity) {
        Objects.requireNonNull(importName, "importName");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(identity, "identity");
        targets = Collections.unmodifiableList(new ArrayList<>(targets));
        capabilitiesProvided = Collections.unmodifiableList(new ArrayList<>(capabilitiesProvided));
        capabilitiesConsumed = Collections.unmodifiableList(new ArrayList<>(capabilitiesConsumed));
        servicesProvided = Collections.unmodifiableList(new ArrayList<>(servicesProvided));
        servicesConsumed = Collections.unmodifiableList(new ArrayList<>(servicesConsumed));
        permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
        this.importName = importName;
        this.api = api;
        this.entry = entry;
        this.targets = targets;
        this.capabilitiesProvided = capabilitiesProvided;
        this.capabilitiesConsumed = capabilitiesConsumed;
        this.servicesProvided = servicesProvided;
        this.servicesConsumed = servicesConsumed;
        this.permissions = permissions;
        this.identity = identity;
    }

    /**
     * Answers {@code importName}.
     *
     * @return the plugin's import name, its id
     */
    public String importName() {
        return importName;
    }

    /**
     * Answers {@code api}.
     *
     * @return the api level the plugin needs at least
     */
    public int api() {
        return api;
    }

    /**
     * Answers {@code entry}.
     *
     * @return the plugin's entry, opaque; empty when it declares none
     */
    public Optional<String> entry() {
        return entry;
    }

    /**
     * Answers {@code targets}.
     *
     * @return the applications the plugin is built for; empty means any application
     */
    public List<String> targets() {
        return targets;
    }

    /**
     * Answers {@code capabilitiesProvided}.
     *
     * @return the capabilities it provides
     */
    public List<String> capabilitiesProvided() {
        return capabilitiesProvided;
    }

    /**
     * Answers {@code capabilitiesConsumed}.
     *
     * @return the capabilities it consumes
     */
    public List<String> capabilitiesConsumed() {
        return capabilitiesConsumed;
    }

    /**
     * Answers {@code servicesProvided}.
     *
     * @return the services it provides
     */
    public List<ServiceOffer> servicesProvided() {
        return servicesProvided;
    }

    /**
     * Answers {@code servicesConsumed}.
     *
     * @return the services it consumes
     */
    public List<ServiceWant> servicesConsumed() {
        return servicesConsumed;
    }

    /**
     * Answers {@code permissions}.
     *
     * @return the permissions it asks for
     */
    public List<String> permissions() {
        return permissions;
    }

    /**
     * Answers {@code identity}.
     *
     * @return who the plugin is, supplied by whatever loaded it; empty means its import name
     */
    public Optional<String> identity() {
        return identity;
    }

    /**
     * A manifest that names a plugin and declares nothing else.
     *
     * @param importName the plugin's import name
     * @return the manifest
     */
    public static Manifest named(String importName) {
        return new Manifest(importName, 0, Optional.empty(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Optional.empty());
    }

    /**
     * A service a plugin registers, mirroring {@code weivel_service_offer}.
     */
    public static final class ServiceOffer {
        private final String id;
        private final List<NamedNumber> attributes;

        /**
         * Checks the id and copies the attributes.
         *
         * @param id the service id
         * @param attributes the advertised attributes
         */
        public ServiceOffer(String id, List<NamedNumber> attributes) {
            Objects.requireNonNull(id, "id");
            attributes = Collections.unmodifiableList(new ArrayList<>(attributes));
            this.id = id;
            this.attributes = attributes;
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
         * Answers {@code attributes}.
         *
         * @return what it advertises until the live service reports its own
         */
        public List<NamedNumber> attributes() {
            return attributes;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ServiceOffer)) {
                return false;
            }
            ServiceOffer that = (ServiceOffer) other;
            return Objects.equals(id, that.id)
                    && Objects.equals(attributes, that.attributes);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(id);
            hash = 31 * hash + Objects.hashCode(attributes);
            return hash;
        }

        @Override
        public String toString() {
            return "ServiceOffer[id=" + id + ", attributes=" + attributes + "]";
        }
    }

    /**
     * A service a plugin asks for, mirroring {@code weivel_service_want}.
     */
    public static final class ServiceWant {
        private final String id;
        private final Optional<String> provider;
        private final Choice optional;
        private final Choice degradable;
        private final List<NamedNumber> constraints;

        /**
         * Checks every part and copies the constraints.
         *
         * @param id the service id
         * @param provider the bound provider, or empty
         * @param optional whether it is optional
         * @param degradable whether it degrades
         * @param constraints the constraints
         */
        public ServiceWant(String id, Optional<String> provider, Choice optional, Choice degradable,
                List<NamedNumber> constraints) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(optional, "optional");
            Objects.requireNonNull(degradable, "degradable");
            constraints = Collections.unmodifiableList(new ArrayList<>(constraints));
            this.id = id;
            this.provider = provider;
            this.optional = optional;
            this.degradable = degradable;
            this.constraints = constraints;
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
         * Answers {@code provider}.
         *
         * @return the identity of the one plugin to take it from; empty means any provider
         */
        public Optional<String> provider() {
            return provider;
        }

        /**
         * Answers {@code optional}.
         *
         * @return whether the plugin still starts when nothing provides it; the default is yes
         */
        public Choice optional() {
            return optional;
        }

        /**
         * Answers {@code degradable}.
         *
         * @return whether it accepts a provider that never meets its constraints once the budget is spent
         */
        public Choice degradable() {
            return degradable;
        }

        /**
         * Answers {@code constraints}.
         *
         * @return what the plugin needs of the service
         */
        public List<NamedNumber> constraints() {
            return constraints;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ServiceWant)) {
                return false;
            }
            ServiceWant that = (ServiceWant) other;
            return Objects.equals(id, that.id)
                    && Objects.equals(provider, that.provider)
                    && Objects.equals(optional, that.optional)
                    && Objects.equals(degradable, that.degradable)
                    && Objects.equals(constraints, that.constraints);
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(id);
            hash = 31 * hash + Objects.hashCode(provider);
            hash = 31 * hash + Objects.hashCode(optional);
            hash = 31 * hash + Objects.hashCode(degradable);
            hash = 31 * hash + Objects.hashCode(constraints);
            return hash;
        }

        @Override
        public String toString() {
            return "ServiceWant[id=" + id + ", provider=" + provider + ", optional=" + optional + ", degradable="
                    + degradable + ", constraints=" + constraints + "]";
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Manifest)) {
            return false;
        }
        Manifest that = (Manifest) other;
        return Objects.equals(importName, that.importName)
                && api == that.api
                && Objects.equals(entry, that.entry)
                && Objects.equals(targets, that.targets)
                && Objects.equals(capabilitiesProvided, that.capabilitiesProvided)
                && Objects.equals(capabilitiesConsumed, that.capabilitiesConsumed)
                && Objects.equals(servicesProvided, that.servicesProvided)
                && Objects.equals(servicesConsumed, that.servicesConsumed)
                && Objects.equals(permissions, that.permissions)
                && Objects.equals(identity, that.identity);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(importName);
        hash = 31 * hash + Integer.hashCode(api);
        hash = 31 * hash + Objects.hashCode(entry);
        hash = 31 * hash + Objects.hashCode(targets);
        hash = 31 * hash + Objects.hashCode(capabilitiesProvided);
        hash = 31 * hash + Objects.hashCode(capabilitiesConsumed);
        hash = 31 * hash + Objects.hashCode(servicesProvided);
        hash = 31 * hash + Objects.hashCode(servicesConsumed);
        hash = 31 * hash + Objects.hashCode(permissions);
        hash = 31 * hash + Objects.hashCode(identity);
        return hash;
    }

    @Override
    public String toString() {
        return "Manifest[importName=" + importName + ", api=" + api + ", entry=" + entry + ", targets=" + targets
                + ", capabilitiesProvided=" + capabilitiesProvided + ", capabilitiesConsumed=" + capabilitiesConsumed
                + ", servicesProvided=" + servicesProvided + ", servicesConsumed=" + servicesConsumed
                + ", permissions=" + permissions + ", identity=" + identity + "]";
    }
}
