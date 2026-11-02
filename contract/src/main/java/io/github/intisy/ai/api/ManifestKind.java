package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsLiteral;

/** What a manifest describes. */
public enum ManifestKind {
    /** A plugin a host loads. */
    @TsLiteral("plugin")
    PLUGIN,

    /** An application that loads plugins, or any other program a marketplace installs. */
    @TsLiteral("host")
    HOST
}
