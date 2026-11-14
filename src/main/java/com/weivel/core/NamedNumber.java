package com.weivel.core;

import java.util.Objects;

/**
 * One named number: an attribute a service advertises, or a constraint a consumer sets on it.
 */
public final class NamedNumber {
    private final String name;
    private final double value;

    /**
     * Checks that the name is present.
     *
     * @param name the name
     * @param value the value
     */
    public NamedNumber(String name, double value) {
        Objects.requireNonNull(name, "name");
        this.name = name;
        this.value = value;
    }

    /**
     * Answers {@code name}.
     *
     * @return the attribute's or the constraint's name
     */
    public String name() {
        return name;
    }

    /**
     * Answers {@code value}.
     *
     * @return its value
     */
    public double value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NamedNumber)) {
            return false;
        }
        NamedNumber that = (NamedNumber) other;
        return Objects.equals(name, that.name)
                && Double.compare(value, that.value) == 0;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(name);
        hash = 31 * hash + Double.hashCode(value);
        return hash;
    }

    @Override
    public String toString() {
        return "NamedNumber[name=" + name + ", value=" + value + "]";
    }
}
