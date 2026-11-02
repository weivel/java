package io.github.intisy.ai.engine;

/**
 * The settings layer a host may lack, and the refusal for writing to it there (invariant 13).
 *
 * @implNote One place for the decision and its wording, because the JVM context and the TeaVM
 * context both fence a plugin's settings, and two copies of a refusal drift apart.
 */
public final class SettingsLayers {

    /** The wire name of the layer in the user's portable home. */
    public static final String PORTABLE = "portable";

    private SettingsLayers() {
    }

    /**
     * Whether a host can take one settings write, as data.
     *
     * @param pluginId the plugin writing
     * @param layer the wire name of the layer the write names, or null for the application layer
     * @param hostHasPortableLayer true when the host offers the plugin a portable path, which is
     *     where the portable layer lives
     * @return the refusal, or null when the host can take the write
     */
    public static PluginException refusalOf(String pluginId, String layer, boolean hostHasPortableLayer) {
        if (!PORTABLE.equals(layer) || hostHasPortableLayer) {
            return null;
        }
        return new PluginException(pluginId, "this host has no portable settings layer",
            "write to the \"application\" layer, or run on a host that offers paths.portable");
    }
}
