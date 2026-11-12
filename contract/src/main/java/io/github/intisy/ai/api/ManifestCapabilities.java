package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;
import java.util.List;

/**
 * The host-facing contract: the abilities this plugin provides at activation, and the ones it asks
 * to see the implementations of.
 *
 * @implNote Consuming is ids only, with no object form beside it: an empty answer already means
 * "none right now", so there is nothing an {@code optional} or a {@code provider} would add until a
 * consumer needs one.
 */
@TsInterface(data = true)
public interface ManifestCapabilities {
    /**
     * Capability ids this plugin provides at activation.
     *
     * @return the capability ids this plugin provides, or absent when it provides none
     */
    @TsOptional
    List<String> provides();

    /**
     * Capability ids this plugin asks to see the verified implementations of.
     *
     * @return the capability ids this plugin consumes, or absent when it consumes none
     */
    @TsOptional
    List<String> consumes();
}
