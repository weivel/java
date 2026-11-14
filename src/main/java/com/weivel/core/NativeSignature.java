package com.weivel.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** What a native function answers and takes, in order, as its declaration in C says. */
final class NativeSignature {
    private final NativeType returned;
    private final List<NativeType> parameters;

    private NativeSignature(NativeType returned, List<NativeType> parameters) {
        this.returned = returned;
        this.parameters = parameters;
    }

    static NativeSignature of(NativeType returned, NativeType... parameters) {
        Objects.requireNonNull(returned, "returned");
        for (NativeType parameter : parameters) {
            if (parameter == null || parameter == NativeType.VOID) {
                throw new IllegalArgumentException("a parameter is a value, never void or null");
            }
        }
        return new NativeSignature(returned, Collections.unmodifiableList(new ArrayList<>(Arrays.asList(parameters))));
    }

    NativeType returned() {
        return returned;
    }

    List<NativeType> parameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NativeSignature)) {
            return false;
        }
        NativeSignature that = (NativeSignature) other;
        return returned == that.returned && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(returned, parameters);
    }

    @Override
    public String toString() {
        return returned + " " + parameters;
    }
}
