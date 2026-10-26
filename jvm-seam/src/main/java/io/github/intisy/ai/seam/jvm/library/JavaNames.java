package io.github.intisy.ai.seam.jvm.library;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** How the type generator turns declared names into Java names and literals. */
final class JavaNames {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]+");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Set<String> RESERVED = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_")));

    /**
     * Member names a generated class body reads in expression position, where a field of the same
     * name would be found before the type or parameter meant.
     */
    private static final Set<String> SHADOWING = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "Objects", "other", "that")));

    private JavaNames() {
    }

    /** {@code app-registry} becomes {@code AppRegistry} and {@code appDescriptor} becomes {@code AppDescriptor}. */
    static String pascalCase(String name) {
        StringBuilder pascal = new StringBuilder();
        for (String part : NON_ALPHANUMERIC.split(name)) {
            if (!part.isEmpty()) {
                pascal.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return pascal.toString();
    }

    /** {@code app-registry} becomes {@code APP_REGISTRY} and {@code veryFast} becomes {@code VERY_FAST}. */
    static String upperSnakeCase(String name) {
        StringBuilder snake = new StringBuilder();
        for (String part : NON_ALPHANUMERIC.split(CAMEL_BOUNDARY.matcher(name).replaceAll("$1_$2"))) {
            if (!part.isEmpty()) {
                snake.append(snake.length() == 0 ? "" : "_").append(part.toUpperCase(Locale.ROOT));
            }
        }
        return snake.toString();
    }

    static boolean isReserved(String word) {
        return RESERVED.contains(word);
    }

    static boolean isIdentifier(String word) {
        return IDENTIFIER.matcher(word).matches() && !isReserved(word);
    }

    /** The field a property is held in: its own name when Java allows it, otherwise one derived from it. */
    static String memberName(String propertyName) {
        if (isIdentifier(propertyName) && !SHADOWING.contains(propertyName)) {
            return propertyName;
        }
        if (isReserved(propertyName) || SHADOWING.contains(propertyName)) {
            return propertyName + "Value";
        }
        String pascal = pascalCase(propertyName);
        if (pascal.isEmpty() || !Character.isLetter(pascal.charAt(0))) {
            return "value" + pascal;
        }
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    static String constantName(String value) {
        String snake = upperSnakeCase(value);
        if (snake.isEmpty()) {
            return "VALUE";
        }
        return Character.isLetter(snake.charAt(0)) ? snake : "VALUE_" + snake;
    }

    static String capitalized(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * A Java string literal holding {@code value}, in ASCII.
     *
     * @implNote A quote, a backslash and a line break are written as escape sequences and never as
     * {@code \}{@code u} escapes, because javac translates those before it reads the literal, so one
     * standing for a quote or a line break would end the literal or the line.
     */
    static String stringLiteral(String value) {
        StringBuilder literal = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            literal.append(escaped(character));
        }
        return literal.append('"').toString();
    }

    private static String escaped(char character) {
        switch (character) {
            case '"':
                return "\\\"";
            case '\\':
                return "\\\\";
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            default:
                return character < 0x20 || character > 0x7e ? unicodeEscape(character) : String.valueOf(character);
        }
    }

    /** Non-ASCII text as {@code \}{@code u} escapes, for a comment the caller has already cleared of line breaks. */
    static String asciiText(String text) {
        StringBuilder ascii = new StringBuilder();
        for (char character : text.toCharArray()) {
            ascii.append(character > 0x7e ? unicodeEscape(character) : String.valueOf(character));
        }
        return ascii.toString();
    }

    private static String unicodeEscape(char character) {
        return String.format("\\u%04x", (int) character);
    }
}
