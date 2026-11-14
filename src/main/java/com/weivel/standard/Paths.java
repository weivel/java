package com.weivel.standard;

import java.nio.file.Path;

/**
 * The absolute directories a host stores files in, served under the standard's paths service id, translated from
 * {@code weivel_standard_paths}. Each operation answers the same absolute path on every call; whether the directory
 * exists yet is not promised.
 *
 * @implNote As for {@link Config}: no {@code size} and no {@code context}; the header's answers need no release, so
 * neither do these.
 */
public interface Paths {
    /**
     * Where files that can be rebuilt are kept.
     *
     * @return the directory
     */
    Path cache();

    /**
     * Where settings files are kept.
     *
     * @return the directory
     */
    Path config();

    /**
     * The directory the host offers as the plugin's own.
     *
     * @return the directory
     */
    Path plugin();
}
