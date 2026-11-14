package com.weivel.host;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * What of a library declaration is the caller's mistake, found before anything is opened: a function of a kind other
 * than bytes, an id listed twice, a function name a service lists twice, or a declaration of no service or a service
 * of no function, which the standard's reader refuses too, so a declaration the caller builds cannot load what a read
 * one never could.
 */
final class LibraryChecks {
    private LibraryChecks() {
    }

    static Optional<String> mistakeIn(LibraryDeclaration declaration) {
        if (declaration.services().isEmpty()) {
            return Optional.of("the library " + declaration.importName() + " declares no service");
        }
        Set<String> ids = new HashSet<>();
        for (LibraryDeclaration.Service service : declaration.services()) {
            if (!ids.add(service.id())) {
                return Optional.of("the declaration lists the service id " + service.id() + " twice");
            }
            Optional<String> mistake = mistakeIn(service);
            if (mistake.isPresent()) {
                return mistake;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> mistakeIn(LibraryDeclaration.Service service) {
        if (service.functions().isEmpty()) {
            return Optional.of("the service " + service.id() + " declares no function");
        }
        Set<String> names = new HashSet<>();
        for (LibraryDeclaration.Function function : service.functions()) {
            if (function.kind() != LibraryDeclaration.BYTES) {
                return Optional.of("the function " + function.name() + " of the service " + service.id()
                        + " is of the kind " + function.kind() + "; the bytes kind, "
                        + LibraryDeclaration.BYTES + ", is the only one");
            }
            if (!names.add(function.name())) {
                return Optional.of("the service " + service.id() + " lists the function " + function.name()
                        + " twice");
            }
        }
        return Optional.empty();
    }
}
