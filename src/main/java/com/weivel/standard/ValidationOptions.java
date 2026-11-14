package com.weivel.standard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Options for validating a manifest, the standard's {@code weivel_standard_validation_options} as a value. */
public final class ValidationOptions {
    private final Optional<List<String>> wellKnownServices;

    private ValidationOptions(Optional<List<String>> wellKnownServices) {
        this.wellKnownServices = wellKnownServices;
    }

    /**
     * Every default: rule V2 is not applied.
     *
     * @return the options
     */
    public static ValidationOptions defaults() {
        return new ValidationOptions(Optional.<List<String>>empty());
    }

    /**
     * Applies rule V2 with the host's well-known service ids: a provided id is legal when it is one of them or is
     * namespaced; no ids refuses every bare id.
     *
     * @param ids the well-known ids
     * @return the options
     */
    public static ValidationOptions wellKnown(List<String> ids) {
        List<String> copy = new ArrayList<>(Objects.requireNonNull(ids, "ids"));
        for (String id : copy) {
            Objects.requireNonNull(id, "a well-known id");
        }
        return new ValidationOptions(Optional.of(Collections.unmodifiableList(copy)));
    }

    /**
     * Answers {@code wellKnownServices}.
     *
     * @return the well-known ids, or empty when V2 is not applied
     */
    public Optional<List<String>> wellKnownServices() {
        return wellKnownServices;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ValidationOptions
                && wellKnownServices.equals(((ValidationOptions) other).wellKnownServices);
    }

    @Override
    public int hashCode() {
        return wellKnownServices.hashCode();
    }

    @Override
    public String toString() {
        return "ValidationOptions[wellKnownServices=" + wellKnownServices + "]";
    }
}
