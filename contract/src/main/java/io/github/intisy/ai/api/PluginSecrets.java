package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsUnion;
import java.util.concurrent.CompletionStage;

/**
 * A plugin's secrets, scoped to its own id, on a host that has a secret store.
 *
 * @implNote A value is a UTF-8 string of any length, and a secret is shared across applications. A
 * plugin keeps a credential here and never in a file of its own.
 */
@TsInterface
public interface PluginSecrets {
    /**
     * One secret, absent when it was never set.
     *
     * @param name the secret's name within this plugin
     * @return a stage that completes with the value, or with nothing when it was never set
     */
    @TsUnion({"string", "undefined"})
    CompletionStage<String> get(String name);

    /**
     * Stores one secret, replacing any value it had.
     *
     * @param name the secret's name within this plugin
     * @param value the value to store
     * @return a stage that completes once the secret is stored
     */
    CompletionStage<Void> set(String name, String value);

    /**
     * Removes one secret; removing one that is not there succeeds.
     *
     * @param name the secret's name within this plugin
     * @return a stage that completes once the secret is gone
     */
    CompletionStage<Void> delete(String name);
}
