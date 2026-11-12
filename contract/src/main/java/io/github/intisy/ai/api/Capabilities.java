package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * How a plugin sees the implementations of a capability it consumes.
 *
 * @implNote An implementation is answered from the moment its plugin's activation is verified, never
 * from the moment it is provided, so a consumer is never handed one the host is about to quarantine
 * for a declaration mismatch. Only the ids the manifest lists under {@code capabilities.consumes} or
 * {@code capabilities.provides} are answered; any other id answers nothing and is reported.
 */
@TsInterface
public interface Capabilities {
    /**
     * Every verified implementation of this capability right now, in the order their plugins'
     * activations were verified. Empty means none right now.
     *
     * @param <T> the capability's implementation type
     * @param type the capability key, from {@link PluginContext#capability(String)}
     * @return the verified implementations, or empty when there are none
     */
    <T> List<T> all(CapabilityType<T> type);

    /**
     * Reports every implementation of this capability that joins or leaves from now on, until the
     * returned function is called. The listener is handed the implementation and either "provide" or
     * "withdraw"; on the latter it is the implementation that left.
     *
     * @implNote Future changes only, as {@link Services#watch} reports them, so a consumer pairs it
     * with {@code all} to learn what is already there.
     * @param <T> the capability's implementation type
     * @param type the capability key, from {@link PluginContext#capability(String)}
     * @param listener called with the implementation that joined or left and the event kind
     * @return a function that stops the watch when called
     */
    <T> Runnable watch(CapabilityType<T> type, BiConsumer<T, String> listener);
}
