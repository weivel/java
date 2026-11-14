package com.weivel.core;

/** A yes-or-no field the core lets a caller leave out, so the field's documented default applies. */
public enum Choice {
    /** The field's default ({@code WEIVEL_CHOICE_DEFAULT}). */
    DEFAULT(0),
    /** No ({@code WEIVEL_CHOICE_NO}). */
    NO(1),
    /** Yes ({@code WEIVEL_CHOICE_YES}). */
    YES(2);

    private final int value;

    Choice(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }

    /** @throws IllegalArgumentException for a value the core's header gives no choice */
    static Choice of(int value) {
        for (Choice choice : values()) {
            if (choice.value == value) {
                return choice;
            }
        }
        throw new IllegalArgumentException(value + " is no choice the core's header names");
    }
}
