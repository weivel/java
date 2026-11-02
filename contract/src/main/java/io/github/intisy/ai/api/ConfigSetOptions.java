package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;

/** Where a settings write goes. */
@TsInterface(data = true)
public interface ConfigSetOptions {
    /**
     * The layer written to. Absent means {@code "application"}, the per-application file.
     *
     * @return the layer, or null for the application layer
     */
    @TsOptional
    SettingsLayer layer();
}
