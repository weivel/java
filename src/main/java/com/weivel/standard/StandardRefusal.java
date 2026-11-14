package com.weivel.standard;

import java.util.Objects;

/**
 * Why the standard refused something, its {@code weivel_standard_refusal} copied: an append-only code, what happened
 * and what would make it succeed in English, and the field's path, empty for the whole document.
 */
public final class StandardRefusal {
    /** A call was malformed ({@code WEIVEL_STANDARD_REFUSAL_MALFORMED_CALL}). */
    public static final int MALFORMED_CALL = 1;
    /** The text is not one well-formed JSON document. */
    public static final int MALFORMED_JSON = 2;
    /** A field holds a JSON value of the wrong type. */
    public static final int WRONG_TYPE = 3;
    /** A field holds a value the core's manifest cannot hold. */
    public static final int UNREPRESENTABLE = 4;
    /** A field the schema requires is absent. */
    public static final int MISSING_FIELD = 5;
    /** A field holds a value its rule refuses. */
    public static final int INVALID_VALUE = 6;
    /** A list item, or a plugin's identity, repeats an earlier one. */
    public static final int DUPLICATE = 7;
    /** A provided service id is neither namespaced nor well-known. */
    public static final int NOT_WELL_KNOWN = 8;
    /** A field is present beside one that excludes it. */
    public static final int CONFLICTING_FIELD = 9;
    /** A file or directory exists and could not be read, or is not what was needed. */
    public static final int UNREADABLE = 10;
    /** The file or directory named does not exist. */
    public static final int NOT_FOUND = 11;
    /** A manifest's file name is not its importName's. */
    public static final int NAME_MISMATCH = 12;

    private final int code;
    private final String detail;
    private final String fix;
    private final String path;

    /**
     * Makes the value.
     *
     * @param code the standard's refusal code
     * @param detail what happened
     * @param fix what would make it succeed
     * @param path the field, empty for the whole document
     */
    public StandardRefusal(int code, String detail, String fix, String path) {
        this.code = code;
        this.detail = Objects.requireNonNull(detail, "detail");
        this.fix = Objects.requireNonNull(fix, "fix");
        this.path = Objects.requireNonNull(path, "path");
    }

    /**
     * Answers {@code code}.
     *
     * @return the standard's refusal code
     */
    public int code() {
        return code;
    }

    /**
     * Answers {@code detail}.
     *
     * @return what happened
     */
    public String detail() {
        return detail;
    }

    /**
     * Answers {@code fix}.
     *
     * @return what would make it succeed
     */
    public String fix() {
        return fix;
    }

    /**
     * Answers {@code path}.
     *
     * @return the field, empty for the whole document
     */
    public String path() {
        return path;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StandardRefusal)) {
            return false;
        }
        StandardRefusal that = (StandardRefusal) other;
        return code == that.code && detail.equals(that.detail) && fix.equals(that.fix) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, detail, fix, path);
    }

    @Override
    public String toString() {
        return "StandardRefusal[code=" + code + ", detail=" + detail + ", fix=" + fix + ", path=" + path + "]";
    }
}
