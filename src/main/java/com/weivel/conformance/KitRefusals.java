package com.weivel.conformance;

import com.weivel.core.Answer;
import com.weivel.core.CoreConstants;
import com.weivel.core.Refusal;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** A refusal as a kit vector states it: its code by the kit's name, such as {@code timed_out}. */
final class KitRefusals {
    private static final String PREFIX = "REFUSAL_";
    private static final Map<Integer, String> NAMES = codeNames();

    private KitRefusals() {
    }

    /**
     * The kit's name for a core refusal code.
     *
     * @implNote The names are read from {@link CoreConstants}, each the core's {@code WEIVEL_REFUSAL_} constant in
     * lower case, so no list of them is kept here to drift from the core's.
     */
    static String codeName(int code) {
        String name = NAMES.get(code);
        return name != null ? name : Integer.toString(code);
    }

    /** {@code {code, subject, hasFix}}, the subject JSON's null when the refusal names none. */
    static Map<String, Object> kitRefusal(Refusal refusal) {
        Map<String, Object> kit = new LinkedHashMap<>();
        kit.put("code", codeName(refusal.code()));
        kit.put("subject", refusal.subject().isPresent() ? refusal.subject().get() : StandardJson.NULL);
        kit.put("hasFix", refusal.hasFix());
        return Collections.unmodifiableMap(kit);
    }

    /** The core's refusal an answer carries; empty for any other answer. */
    static Optional<Refusal> refusalOf(Answer<?> answer) {
        return answer instanceof Answer.Refused ? ((Answer.Refused<?>) answer).refusal() : Optional.<Refusal>empty();
    }

    private static Map<Integer, String> codeNames() {
        Map<Integer, String> names = new HashMap<>();
        for (Field field : CoreConstants.class.getFields()) {
            if (field.getName().startsWith(PREFIX) && field.getType() == int.class
                    && Modifier.isStatic(field.getModifiers())) {
                names.put(constant(field), field.getName().substring(PREFIX.length()).toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableMap(names);
    }

    private static int constant(Field field) {
        try {
            return field.getInt(null);
        } catch (IllegalAccessException unreadable) {
            throw new IllegalStateException("the core's constant " + field.getName() + " could not be read",
                    unreadable);
        }
    }
}
