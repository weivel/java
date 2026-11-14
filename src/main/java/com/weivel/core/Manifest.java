package com.weivel.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A plugin's manifest as a value, mirroring the core's {@code weivel_manifest}. The core parses no file and checks
 * only its shape; the layer hands it to the core unchanged, so a shape the core refuses is its caller's mistake.
 *
 * @param importName the plugin's import name, its id
 * @param api the api level the plugin needs at least
 * @param entry the plugin's entry, opaque; empty when it declares none
 * @param targets the applications the plugin is built for; empty means any application
 * @param capabilitiesProvided the capabilities it provides
 * @param capabilitiesConsumed the capabilities it consumes
 * @param servicesProvided the services it provides
 * @param servicesConsumed the services it consumes
 * @param permissions the permissions it asks for
 * @param identity who the plugin is, supplied by whatever loaded it; empty means its import name
 */
public record Manifest(String importName, int api, Optional<String> entry, List<String> targets,
                       List<String> capabilitiesProvided, List<String> capabilitiesConsumed,
                       List<ServiceOffer> servicesProvided, List<ServiceWant> servicesConsumed,
                       List<String> permissions, Optional<String> identity) {
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
    public Manifest {
        Objects.requireNonNull(importName, "importName");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(identity, "identity");
        targets = List.copyOf(targets);
        capabilitiesProvided = List.copyOf(capabilitiesProvided);
        capabilitiesConsumed = List.copyOf(capabilitiesConsumed);
        servicesProvided = List.copyOf(servicesProvided);
        servicesConsumed = List.copyOf(servicesConsumed);
        permissions = List.copyOf(permissions);
    }

    /**
     * A manifest that names a plugin and declares nothing else.
     *
     * @param importName the plugin's import name
     * @return the manifest
     */
    public static Manifest named(String importName) {
        return new Manifest(importName, 0, Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), Optional.empty());
    }

    /**
     * A service a plugin registers, mirroring {@code weivel_service_offer}.
     *
     * @param id the service id
     * @param attributes what it advertises until the live service reports its own
     */
    public record ServiceOffer(String id, List<NamedNumber> attributes) {
        /**
         * Checks the id and copies the attributes.
         *
         * @param id the service id
         * @param attributes the advertised attributes
         */
        public ServiceOffer {
            Objects.requireNonNull(id, "id");
            attributes = List.copyOf(attributes);
        }
    }

    /**
     * A service a plugin asks for, mirroring {@code weivel_service_want}.
     *
     * @param id the service id
     * @param provider the identity of the one plugin to take it from; empty means any provider
     * @param optional whether the plugin still starts when nothing provides it; the default is yes
     * @param degradable whether it accepts a provider that never meets its constraints once the budget is spent
     * @param constraints what the plugin needs of the service
     */
    public record ServiceWant(String id, Optional<String> provider, Choice optional, Choice degradable,
                              List<NamedNumber> constraints) {
        /**
         * Checks every part and copies the constraints.
         *
         * @param id the service id
         * @param provider the bound provider, or empty
         * @param optional whether it is optional
         * @param degradable whether it degrades
         * @param constraints the constraints
         */
        public ServiceWant {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(optional, "optional");
            Objects.requireNonNull(degradable, "degradable");
            constraints = List.copyOf(constraints);
        }
    }
}
