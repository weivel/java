package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;

/** One plugin another plugin needs, located by a repository id or by an owner/name, never both. */
@TsInterface(data = true)
public interface ManifestDependency {
    /**
     * The name the dependent plugin imports the dependency by.
     *
     * @return the import name
     */
    String importName();

    /**
     * The repository identity of the dependency, which survives a rename.
     *
     * @return the repository id, or absent when the dependency is named by {@link #name()}
     */
    @TsOptional
    String id();

    /**
     * The owner and name of the dependency's repository, for one whose id is not known.
     *
     * @return the owner/name, or absent when the dependency is named by {@link #id()}
     */
    @TsOptional
    String name();

    /**
     * The commit the dependency is pinned to.
     *
     * @return the commit, or absent when the dependency is not pinned
     */
    @TsOptional
    String commit();

    /**
     * The SHA-256 digest the dependency's artifact must match.
     *
     * @return the digest, or absent when none is required
     */
    @TsOptional
    String sha256();
}
