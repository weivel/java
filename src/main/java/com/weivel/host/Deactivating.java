package com.weivel.host;

import java.util.concurrent.CompletionStage;

/** The deactivate an {@link Entry} may offer, called by a release while its plugin is still whole. */
@FunctionalInterface
public interface Deactivating {
    /**
     * Deactivates the plugin. The release waits for the stage however long it takes and sets no timeout of its own.
     *
     * @return a stage completing once the deactivation is done; one completing exceptionally, a {@code null} stage,
     *     or a throw from this method is reported to the error sink, and the release goes on
     */
    CompletionStage<?> deactivate();
}
