package com.weivel.context;

import com.weivel.core.NamedNumber;
import java.util.List;

/**
 * What answers a constrained want's questions about one registration, offered when the service is registered. It is
 * called outside every lock of the layer's, by the one thread dispatching; what it throws goes to the error sink and
 * leaves the question unanswered, which the core reads as not served for now.
 */
public interface CapacityAnswerer {
    /**
     * What the provider offers now, laid by the core over what its manifest advertises, name by name.
     *
     * @return the live attributes; empty for nothing live to add
     */
    List<NamedNumber> attributes();

    /**
     * Whether the provider serves a consumer under its constraints.
     *
     * @param constraints the consumer's constraints, empty for an unconstrained ask
     * @return whether it serves
     */
    boolean serves(List<NamedNumber> constraints);
}
