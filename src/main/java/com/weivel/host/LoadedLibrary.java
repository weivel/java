package com.weivel.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A C library the host loaded as host services: the services it registered and the hold its binary is closed through.
 * The host keeps it until it is unloaded, by {@link Host#unloadLibrary} or by the destroy; an object of it a consumer
 * still holds then answers {@link LibraryAnswer.Unloaded}.
 */
public final class LoadedLibrary {
    private final Host host;
    private final LibraryDeclaration declaration;
    private final List<LibraryService> services;
    private final LibraryLifetime lifetime;
    private final List<HostService> registrations = new ArrayList<>();
    private boolean unloaded;
    private boolean idsReleased;

    LoadedLibrary(Host host, LibraryDeclaration declaration, List<LibraryService> services, LibraryLifetime lifetime) {
        this.host = host;
        this.declaration = declaration;
        this.services = Collections.unmodifiableList(new ArrayList<>(services));
        this.lifetime = lifetime;
    }

    /**
     * Answers {@code declaration}.
     *
     * @return the declaration the library was loaded from
     */
    public LibraryDeclaration declaration() {
        return declaration;
    }

    /**
     * Answers {@code services}.
     *
     * @return the object each declared service is registered with, in the declaration's order
     */
    public List<LibraryService> services() {
        return services;
    }

    Host host() {
        return host;
    }

    LibraryLifetime lifetime() {
        return lifetime;
    }

    /**
     * Under the host's lock: whether the library still holds its ids, which it does from its keeping until its unload
     * has unregistered every service, so no registration of the host's own can replace one of them meanwhile.
     */
    boolean holdsItsIds() {
        return !idsReleased;
    }

    /** Under the host's lock: its unload has unregistered every service, so its ids are free again. */
    void releaseIds() {
        idsReleased = true;
    }

    /** Under the host's lock: whether it declares {@code id}. */
    boolean declares(String id) {
        for (LibraryDeclaration.Service service : declaration.services()) {
            if (service.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Under the host's lock: takes the library for its unload, which only one caller is given.
     *
     * @return the registrations to unregister; null when it was taken already
     */
    List<HostService> takeForUnloading() {
        if (unloaded) {
            return null;
        }
        unloaded = true;
        List<HostService> taken = new ArrayList<>(registrations);
        registrations.clear();
        return taken;
    }

    /** Under the host's lock: records a registration the load made, unless the library is unloaded meanwhile. */
    boolean keep(HostService registration) {
        if (unloaded) {
            return false;
        }
        registrations.add(registration);
        return true;
    }

    @Override
    public String toString() {
        return "LoadedLibrary[" + declaration.importName() + " " + declaration.version() + "]";
    }
}
