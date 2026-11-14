package com.weivel.standard;

import java.util.Map;
import java.util.Optional;

/**
 * The settings a host reads, served under the standard's config service id, translated from {@code
 * weivel_standard_config}. A provider implements it and registers itself as the service object; a consumer calls it.
 * A provider is safe for concurrent calls from any thread, and every call completes before it returns.
 *
 * @implNote The header's {@code size} and {@code context} have no Java counterpart: the object is its own context,
 * and an operation appended later arrives as a default method. A NULL required pointer or a value of no kind is a
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
}
