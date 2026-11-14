package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A provider's answer to one capacity question, mirroring {@code weivel_capacity}.
 */
public final class CapacityAnswer {
    private final long request;
    private final List<NamedNumber> attributes;
    private final Choice serves;

    /**
     * Checks every part and copies the attributes.
     *
     * @param request the request answered
     * @param attributes the live attributes
     * @param serves whether it serves
     */
    public CapacityAnswer(long request, List<NamedNumber> attributes, Choice serves) {
        attributes = Collections.unmodifiableList(new ArrayList<>(attributes));
        Objects.requireNonNull(serves, "serves");
        this.request = request;
        this.attributes = attributes;
        this.serves = serves;
    }

    /**
     * Answers {@code request}.
     *
     * @return the request of the question answered
     */
    public long request() {
        return request;
    }

    /**
     * Answers {@code attributes}.
     *
     * @return for an attributes question, what the provider offers now; empty when it has nothing live to add
     */
    public List<NamedNumber> attributes() {
        return attributes;
    }

    /**
     * Answers {@code serves}.
     *
     * @return for a serve question, whether it serves; anything but {@link Choice#YES} is a no
     */
    public Choice serves() {
        return serves;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapacityAnswer)) {
            return false;
        }
        CapacityAnswer that = (CapacityAnswer) other;
        return request == that.request
                && Objects.equals(attributes, that.attributes)
                && Objects.equals(serves, that.serves);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(request);
        hash = 31 * hash + Objects.hashCode(attributes);
        hash = 31 * hash + Objects.hashCode(serves);
        return hash;
    }

    @Override
    public String toString() {
        return "CapacityAnswer[request=" + request + ", attributes=" + attributes + ", serves=" + serves + "]";
    }
}
