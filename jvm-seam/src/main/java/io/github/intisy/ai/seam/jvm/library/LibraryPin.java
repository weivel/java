package io.github.intisy.ai.seam.jvm.library;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which release of which library a host loads, and the digest every file of it must have.
 *
 * @implNote {@code sha256} is keyed by {@code library.json} and by platform key
 * ({@code win32-x64}, {@code linux-x64}), each value the hex SHA-256 of that asset as the release's
 * {@code SHA256SUMS} lists it. The embedding host holds the pins, so a release rebuilt under the
 * same tag is refused rather than loaded.
 */
public final class LibraryPin {

    private final String repository;
    private final String tag;
    private final Map<String, String> sha256;

    /**
     * @param repository the GitHub repository publishing the release, as {@code owner/name}
     * @param tag the release tag, for example {@code v0.1.1}
     * @param sha256 the hex SHA-256 of each asset, keyed by {@code library.json} and by platform key; copied
     */
    public LibraryPin(String repository, String tag, Map<String, String> sha256) {
        this.repository = repository;
        this.tag = tag;
        this.sha256 = Collections.unmodifiableMap(new LinkedHashMap<String, String>(sha256));
    }

    /** @return the GitHub repository publishing the release, as {@code owner/name} */
    public String repository() {
        return repository;
    }

    /** @return the release tag */
    public String tag() {
        return tag;
    }

    /** @return the hex SHA-256 of each asset, keyed by {@code library.json} and by platform key */
    public Map<String, String> sha256() {
        return sha256;
    }

    /** @return the release as {@code owner/name@tag}, the name a refusal about it carries */
    public String label() {
        return repository + "@" + tag;
    }

    @Override
    public String toString() {
        return label();
    }
}
