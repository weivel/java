package com.weivel.host;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * What of a library declaration is the caller's mistake, found before anything is opened: a function of a kind other
 * than bytes or definition, a definition beside another function of its service or reading context facts, a
 * description beside bytes functions or naming no symbol, an id listed twice, a function name a service lists twice,
 * a bytes function in a library with no release symbol, or a declaration of neither a service nor a contract or a
 * service of no function, which the standard's reader refuses too, so a declaration the caller builds cannot load
 * what a read one never could.
 */
final class LibraryChecks {
    private LibraryChecks() {
    }

    static Optional<String> mistakeIn(LibraryDeclaration declaration) {
        if (declaration.services().isEmpty() && declaration.contracts().isEmpty()) {
            return Optional.of("the library " + declaration.importName()
                    + " declares neither a service nor a contract");
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
            if (!declaration.release().isPresent() && !isADefinition(service)) {
                return Optional.of("the service " + service.id() + " declares a bytes function, and the library "
                        + declaration.importName() + " declares no release symbol to free what it answers");
            }
        }
        return Optional.empty();
    }

    static boolean isADefinition(LibraryDeclaration.Service service) {
        return service.functions().get(0).kind() == LibraryDeclaration.DEFINITION;
    }

    private static Optional<String> mistakeIn(LibraryDeclaration.Service service) {
        if (service.functions().isEmpty()) {
            return Optional.of("the service " + service.id() + " declares no function");
        }
        if (service.description().isPresent() && service.description().get().isEmpty()) {
            return Optional.of("the service " + service.id() + " declares an empty description symbol");
        }
        if (service.description().isPresent() && !isADefinition(service)) {
            return Optional.of("the service " + service.id() + " declares a description beside bytes functions; only "
                    + "a definition is described");
        }
        Set<String> names = new HashSet<>();
        for (LibraryDeclaration.Function function : service.functions()) {
            if (function.kind() != LibraryDeclaration.BYTES && function.kind() != LibraryDeclaration.DEFINITION) {
                return Optional.of("the function " + function.name() + " of the service " + service.id()
                        + " is of the kind " + function.kind() + "; the kinds are bytes, "
                        + LibraryDeclaration.BYTES + ", and definition, " + LibraryDeclaration.DEFINITION);
            }
            if (function.kind() == LibraryDeclaration.DEFINITION && service.functions().size() > 1) {
                return Optional.of("the service " + service.id() + " declares the definition " + function.name()
                        + " beside another function; a definition is its service's one function");
            }
            if (function.kind() == LibraryDeclaration.DEFINITION && function.context() != 0) {
                return Optional.of("the definition " + function.name() + " of the service " + service.id()
                        + " reads context facts; a definition's struct carries its own context");
            }
            if (!names.add(function.name())) {
                return Optional.of("the service " + service.id() + " lists the function " + function.name()
                        + " twice");
            }
        }
        return Optional.empty();
    }
}
