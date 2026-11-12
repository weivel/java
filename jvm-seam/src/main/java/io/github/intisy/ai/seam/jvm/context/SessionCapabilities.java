package io.github.intisy.ai.seam.jvm.context;

import io.github.intisy.ai.api.Capabilities;
import io.github.intisy.ai.api.CapabilityType;
import io.github.intisy.ai.engine.ConsumedCapabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The contract's typed capabilities over a session's consumed view, which works in ids.
 *
 * @implNote Every cast here is the typed key's promise that its id names a {@code T}, as in
 * {@link SessionServices}.
 */
final class SessionCapabilities implements Capabilities {

    private final ConsumedCapabilities consumed;

    SessionCapabilities(ConsumedCapabilities consumed) {
        this.consumed = consumed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> all(CapabilityType<T> type) {
        List<T> implementations = new ArrayList<T>();
        for (Object implementation : consumed.all(type.id())) {
            implementations.add((T) implementation);
        }
        return implementations;
    }

    @Override
    public <T> Runnable watch(CapabilityType<T> type, final BiConsumer<T, String> listener) {
        return SessionServices.cancelling(consumed.watch(type.id(), new ConsumedCapabilities.Listener() {
            @Override
            @SuppressWarnings("unchecked")
            public void changed(Object implementation, boolean provided) {
                listener.accept((T) implementation, provided ? "provide" : "withdraw");
            }
        }));
    }
}
