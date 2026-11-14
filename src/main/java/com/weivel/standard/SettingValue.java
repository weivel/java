package com.weivel.standard;

import java.util.Objects;

/**
 * One setting's value, {@code weivel_standard_setting_value} as a Java value: text, a finite number, or a boolean.
 *
 * @implNote The header's kind NONE is never a value: here it is an empty {@code Optional} where an answer holds
 * none, and no factory makes it.
 */
public final class SettingValue {
    /** What a setting holds, as {@code weivel_standard_setting_kind}. */
    public enum Kind {
        /** UTF-8 text. */
        TEXT,
        /** A finite IEEE 754 double. */
        NUMBER,
        /** A boolean. */
        BOOLEAN
    }

    private final Kind kind;
    private final Object value;

    private SettingValue(Kind kind, Object value) {
        this.kind = kind;
        this.value = value;
    }

    /**
     * A text setting.
     *
     * @param text the text
     * @return the value
     */
    public static SettingValue text(String text) {
        return new SettingValue(Kind.TEXT, Objects.requireNonNull(text, "text"));
    }

    /**
     * A number setting.
     *
     * @param number the number, finite
     * @return the value
     * @throws IllegalArgumentException for a number that is not finite, which the header allows no setting to hold
     */
    public static SettingValue number(double number) {
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw new IllegalArgumentException(number + " is not finite, and a setting's number is");
        }
        return new SettingValue(Kind.NUMBER, number);
    }

    /**
     * A boolean setting.
     *
     * @param value the boolean
     * @return the value
     */
    public static SettingValue bool(boolean value) {
        return new SettingValue(Kind.BOOLEAN, value);
    }

    /**
     * Answers {@code kind}.
     *
     * @return which of the three it holds
     */
    public Kind kind() {
        return kind;
    }

    /**
     * The text.
     *
     * @return the text
     * @throws IllegalStateException for a value of another kind
     */
    public String asText() {
        return (String) of(Kind.TEXT);
    }

    /**
     * The number.
     *
     * @return the number
     * @throws IllegalStateException for a value of another kind
     */
    public double asNumber() {
        return (Double) of(Kind.NUMBER);
    }

    /**
     * The boolean.
     *
     * @return the boolean
     * @throws IllegalStateException for a value of another kind
     */
    public boolean asBoolean() {
        return (Boolean) of(Kind.BOOLEAN);
    }

    private Object of(Kind wanted) {
        if (kind != wanted) {
            throw new IllegalStateException("the setting holds a " + kind + ", not a " + wanted);
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SettingValue && kind == ((SettingValue) other).kind
                && value.equals(((SettingValue) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }

    @Override
    public String toString() {
        return "SettingValue[" + kind + "=" + value + "]";
    }
}
