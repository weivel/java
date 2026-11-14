package com.weivel.core;

import java.util.Objects;

/**
 * One named number: an attribute a service advertises, or a constraint a consumer sets on it.
 *
 * @param name the attribute's or the constraint's name
 * @param value its value
 */
public record NamedNumber(String name, double value) {
    /**
     * Checks that the name is present.
     *
     * @param name the name
     * @param value the value
     */
    public NamedNumber {
        Objects.requireNonNull(name, "name");
    }
}
