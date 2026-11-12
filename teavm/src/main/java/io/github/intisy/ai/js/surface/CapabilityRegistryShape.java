package io.github.intisy.ai.js.surface;

import io.github.intisy.ai.tsemit.TsInterface;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * One plugin's view of the capabilities it consumes, as reached through {@link ContextSurface}.
 *
 * @implNote The key is untyped for the reason {@link ContextSurface#provide} gives: a typed key
 * object or a bare id, read at the boundary.
 */
@TsInterface
public interface CapabilityRegistryShape {
    /**
     * Every verified implementation of a capability right now, in the order their activations were verified.
     *
     * @param key the typed capability key, or a bare id
     * @return the verified implementations, or empty when there are none or the id is not declared
     */
    List<Object> all(Object key);

    /**
     * Watches one capability for implementations joining or leaving. Call the result to stop.
     *
     * @param key the typed capability key, or a bare id
     * @param listener called with the implementation and the event on each change
     * @return a disposer that cancels the watch when called
     */
    Runnable watch(Object key, BiConsumer<Object, CapabilityEvent> listener);
}
