package com.weivel.core;

import java.util.List;
import java.util.Objects;

/**
 * A provider's answer to one capacity question, mirroring {@code weivel_capacity}.
 *
 * @param request the request of the question answered
 * @param attributes for an attributes question, what the provider offers now; empty when it has nothing live to add
 * @param serves for a serve question, whether it serves; anything but {@link Choice#YES} is a no
 */
public record CapacityAnswer(long request, List<NamedNumber> attributes, Choice serves) {
    /**
     * Checks every part and copies the attributes.
     *
     * @param request the request answered
     * @param attributes the live attributes
     * @param serves whether it serves
     */
    public CapacityAnswer {
        attributes = List.copyOf(attributes);
        Objects.requireNonNull(serves, "serves");
    }
}
