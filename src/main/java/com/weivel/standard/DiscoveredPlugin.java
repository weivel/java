package com.weivel.standard;

import com.weivel.core.Manifest;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One plugin the standard found laid out on disk, its {@code weivel_standard_discovered_plugin} copied: the manifest
 * value, validated and carrying the identity file's id when there is one, where each file lies, and the entry its
 * manifest names, resolved against the plugin's directory.
 *
 * <p>It loads no code: a manifest's entry is opaque, so how it becomes an {@code Entry} is the caller's, by
 * {@code Host.loadEntry} with a class name the caller gives or any way of its own.
 */
public final class DiscoveredPlugin {
    private final Manifest manifest;
    private final String manifestPath;
    private final Optional<String> identityPath;
    private final Optional<String> repositoryName;
    private final Optional<Identity> identity;
    private final Optional<Path> entryPath;
    private final boolean entryPresent;

    /**
     * Makes the value.
     *
     * @param manifest the manifest value, its identity the identity file's id when there is one
     * @param manifestPath the manifest file's path
     * @param identityPath the identity file's path, or empty when none sits beside the manifest
     * @param repositoryName the identity file's repository name, or empty
     * @param identity the identity file's id taken apart, or empty
     * @param entryPath the entry the manifest names joined to the directory, or empty when it names none
     * @param entryPresent whether the entry is a regular file reached with no link on its path
     */
    public DiscoveredPlugin(Manifest manifest, String manifestPath, Optional<String> identityPath,
                            Optional<String> repositoryName, Optional<Identity> identity, Optional<Path> entryPath,
                            boolean entryPresent) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.manifestPath = Objects.requireNonNull(manifestPath, "manifestPath");
        this.identityPath = Objects.requireNonNull(identityPath, "identityPath");
        this.repositoryName = Objects.requireNonNull(repositoryName, "repositoryName");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.entryPath = Objects.requireNonNull(entryPath, "entryPath");
        this.entryPresent = entryPresent;
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
     * Answers {@code manifestPath}.
     *
     * @return the manifest file's path
     */
    public String manifestPath() {
        return manifestPath;
    }

    /**
     * Answers {@code identityPath}.
     *
     * @return the identity file's path, or empty
     */
    public Optional<String> identityPath() {
        return identityPath;
    }

    /**
     * Answers {@code repositoryName}.
     *
     * @return the identity file's repository name, or empty
     */
    public Optional<String> repositoryName() {
        return repositoryName;
    }

    /**
     * Answers {@code identity}.
     *
     * @return the identity taken apart, or empty
     */
    public Optional<Identity> identity() {
        return identity;
    }

    /**
     * Answers {@code entryPath}.
     *
     * @return the entry's path, or empty
     */
    public Optional<Path> entryPath() {
        return entryPath;
    }

    /**
     * Answers {@code entryPresent}.
     *
     * @return whether the entry is a regular file reached with no link
     */
    public boolean entryPresent() {
        return entryPresent;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DiscoveredPlugin)) {
            return false;
        }
        DiscoveredPlugin that = (DiscoveredPlugin) other;
        return manifest.equals(that.manifest) && manifestPath.equals(that.manifestPath)
                && identityPath.equals(that.identityPath) && repositoryName.equals(that.repositoryName)
                && identity.equals(that.identity) && entryPath.equals(that.entryPath)
                && entryPresent == that.entryPresent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(manifest, manifestPath, identityPath, repositoryName, identity, entryPath, entryPresent);
    }

    @Override
    public String toString() {
        return "DiscoveredPlugin[manifest=" + manifest + ", manifestPath=" + manifestPath + ", identityPath="
                + identityPath + ", repositoryName=" + repositoryName + ", identity=" + identity + ", entryPath="
                + entryPath + ", entryPresent=" + entryPresent + "]";
    }
}
