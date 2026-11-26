package com.weivel.standard;

import com.weivel.core.Manifest;
import java.util.Objects;

/**
 * A manifest the standard validated and read: the manifest value the core takes, and beside it the language the
 * manifest names, the weivel implementation that loads the plugin, which the core's manifest has no place for.
 */
public final class LoadedManifest {
    private final Manifest manifest;
    private final String language;

    /**
     * Makes the value.
     *
     * @param manifest the manifest value
     * @param language the language the manifest names
     */
    public LoadedManifest(Manifest manifest, String language) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.language = Objects.requireNonNull(language, "language");
    }

    /**
     * Answers {@code manifest}.
     *
     * @return the manifest value
     */
    public Manifest manifest() {
        return manifest;
    }

    /**
     * Answers {@code language}.
     *
     * @return the weivel implementation that loads the plugin, such as {@code java}
     */
    public String language() {
        return language;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LoadedManifest)) {
            return false;
        }
        LoadedManifest that = (LoadedManifest) other;
        return manifest.equals(that.manifest) && language.equals(that.language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(manifest, language);
    }

    @Override
    public String toString() {
        return "LoadedManifest[manifest=" + manifest + ", language=" + language + "]";
    }
}
