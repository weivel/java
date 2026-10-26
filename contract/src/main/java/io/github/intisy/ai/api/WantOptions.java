package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;

/** How long a plugin is willing to wait for a service that is not registered yet. */
@TsInterface(data = true)
public interface WantOptions {
    /**
     * Milliseconds to wait before giving up. Absent means wait without bound (invariant 8).
     *
     * @return the timeout in milliseconds, or absent to wait without bound
     */
    @TsOptional
    Long timeoutMs();
}
