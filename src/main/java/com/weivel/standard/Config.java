package com.weivel.standard;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * The settings a host reads, served under the standard's config service id, translated from {@code
 * weivel_standard_config}. A provider implements it and registers itself as the service object; a consumer calls it.
 * A provider is safe for concurrent calls from any thread, and every call completes before it returns.
 *
 * @implNote The header's {@code size} and {@code context} have no Java counterpart: the object is its own context,
 * and an operation appended later arrives as a default method that refuses with {@link ServiceRefusal#isNotOffered}
 * true, the test C makes with {@code WEIVEL_STANDARD_OFFERS}. A NULL required pointer or a value of no kind is a
 * {@link NullPointerException} or never constructed, where C answers {@code WEIVEL_STATUS_MALFORMED}.
 */
public interface Config {
    /**
     * Every setting: the provider's defaults merged with what the host stores, a stored value winning.
     *
     * @return each key once, in no promised order; empty when there are none
     * @throws ServiceRefusal when the settings cannot be read
     */
    Map<String, SettingValue> all() throws ServiceRefusal;

    /**
     * One setting.
     *
     * @param key the key
     * @return its value; empty when the key is neither stored nor defaulted
     * @throws ServiceRefusal when the settings cannot be read
     */
    Optional<SettingValue> get(String key) throws ServiceRefusal;

    /**
     * Stores one setting, replacing any value it had, so {@link #get} and {@link #all} answer it from then on.
     *
     * @param key the key
     * @param value the value
     * @throws ServiceRefusal when the provider will not store the key or the value, or cannot write
     */
    void set(String key, SettingValue value) throws ServiceRefusal;

    /**
     * One setting whole, so a setting that is a list or a record, which {@link #get} cannot answer, is read as the host
     * stores it; a text, number or boolean setting is answered as that one value.
     *
     * @param key the key
     * @return the setting as a value tree arrives in Java: a {@link String}, a {@link Double}, a {@link Boolean}, a
     *     {@code List} or a {@code Map} keyed by text, in the provider's order; empty when the key is neither stored
     *     nor defaulted
     * @throws ServiceRefusal when the settings cannot be read, or the provider does not offer it, which
     *     {@link ServiceRefusal#isNotOffered} tells apart
     */
    default Optional<Object> getTree(String key) throws ServiceRefusal {
        throw ServiceRefusal.notOffered("config", "get_tree");
    }

    /**
     * The settings file {@link #set} writes, the one a user edits by hand, so a consumer can name it in what it tells
     * the user.
     *
     * @return the file, absolute, which need not exist yet; empty when the provider keeps the settings in no file
     * @throws ServiceRefusal when the file cannot be named, or the provider does not offer it, which
     *     {@link ServiceRefusal#isNotOffered} tells apart
     */
    default Optional<Path> settingsFile() throws ServiceRefusal {
        throw ServiceRefusal.notOffered("config", "settings_file");
    }
}
