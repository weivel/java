package com.weivel.conformance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Reads the parts of a vector a driver drives, refusing what it does not know: a key, a kind of value or a shape no
 * driver reads fails the run, never passes unread.
 */
final class Shapes {
    private Shapes() {
    }

    static void refuseUnknownKeys(String where, Map<String, Object> value, String... known) {
        List<String> knownKeys = Arrays.asList(known);
        for (String key : value.keySet()) {
            if (!knownKeys.contains(key)) {
                throw new IllegalArgumentException(where + " carries " + key + ", which this driver does not drive");
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(String where, Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(where + " is no object");
        }
        return (Map<String, Object>) value;
    }

    static Map<String, Object> object(String where, Map<String, Object> owner, String key) {
        return object(where + "'s " + key, required(where, owner, key));
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(String where, Map<String, Object> owner, String key) {
        Object value = required(where, owner, key);
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(where + "'s " + key + " is no array");
        }
        return (List<Object>) value;
    }

    static List<Map<String, Object>> objects(String where, Map<String, Object> owner, String key) {
        List<Map<String, Object>> objects = new ArrayList<>();
        List<Object> items = array(where, owner, key);
        for (int index = 0; index < items.size(); index++) {
            objects.add(object(where + "'s " + key + " " + index, items.get(index)));
        }
        return objects;
    }

    static List<String> texts(String where, Map<String, Object> owner, String key) {
        List<String> texts = new ArrayList<>();
        for (Object item : array(where, owner, key)) {
            if (!(item instanceof String)) {
                throw new IllegalArgumentException(where + "'s " + key + " holds " + item + ", which is no string");
            }
            texts.add((String) item);
        }
        return Collections.unmodifiableList(texts);
    }

    static String text(String where, Map<String, Object> owner, String key) {
        Object value = required(where, owner, key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(where + "'s " + key + " is no string");
        }
        return (String) value;
    }

    /** A whole number, or empty for JSON's {@code null}. */
    static OptionalLong numberOrNull(String where, Map<String, Object> owner, String key) {
        Object value = required(where, owner, key);
        if (value == StandardJson.NULL) {
            return OptionalLong.empty();
        }
        if (!(value instanceof Long)) {
            throw new IllegalArgumentException(where + "'s " + key + " is no whole number");
        }
        return OptionalLong.of((Long) value);
    }

    static long number(String where, Map<String, Object> owner, String key) {
        OptionalLong number = numberOrNull(where, owner, key);
        if (!number.isPresent()) {
            throw new IllegalArgumentException(where + "'s " + key + " is null, where a number is driven");
        }
        return number.getAsLong();
    }

    /** A flag that is either absent, so {@code absent} applies, or a boolean. */
    static boolean flag(String where, Map<String, Object> owner, String key, boolean absent) {
        if (!owner.containsKey(key)) {
            return absent;
        }
        Object value = owner.get(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(where + "'s " + key + " is no boolean");
        }
        return (Boolean) value;
    }

    /** The one key of {@code candidates} a step or an action names. */
    static String theOneNamed(String where, Map<String, Object> value, String... candidates) {
        List<String> named = new ArrayList<>();
        for (String candidate : candidates) {
            if (value.containsKey(candidate)) {
                named.add(candidate);
            }
        }
        if (named.size() != 1) {
            throw new IllegalArgumentException(where + " names " + named.size() + " of " + Arrays.asList(candidates)
                    + "; it names exactly one");
        }
        return named.get(0);
    }

    private static Object required(String where, Map<String, Object> owner, String key) {
        if (!owner.containsKey(key)) {
            throw new IllegalArgumentException(where + " carries no " + key);
        }
        return owner.get(key);
    }
}
