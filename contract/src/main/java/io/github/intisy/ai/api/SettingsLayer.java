package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsLiteral;

/** Which settings file a write goes to. */
public enum SettingsLayer {
    /** The per-application settings file, and the default. */
    @TsLiteral("application")
    APPLICATION,

    /** The settings file in the user's portable home, on a host that offers {@code paths.portable}. */
    @TsLiteral("portable")
    PORTABLE
}
