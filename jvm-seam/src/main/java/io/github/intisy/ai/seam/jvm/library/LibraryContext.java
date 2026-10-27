package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ambient facts a library cannot read itself, gathered by the host: the environment, the
 * user's home directory and the host process's argument vector.
 *
 * @implNote Nothing is checked here. A supplier's answer is checked when a call gathers it, where
 * a malformed fact can be refused naming the library and the function that asked for it.
 */
public final class LibraryContext {

    static final String ENVIRONMENT = "environment";
    static final String HOME = "home";
    static final String ARGUMENTS = "arguments";

    private final Map<String, String> environment;
    private final String home;
    private final List<String> arguments;
    private final boolean argumentsSupplied;

    /**
     * @param environment every environment variable, by name
     * @param home the user's home directory
     * @param arguments the host process's argument vector
     */
    public LibraryContext(Map<String, String> environment, String home, List<String> arguments) {
        this(environment, home, arguments, true);
    }

    private LibraryContext(Map<String, String> environment, String home, List<String> arguments, boolean argumentsSupplied) {
        this.environment = environment == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(environment));
        this.home = home;
        this.arguments = arguments == null ? null : Collections.unmodifiableList(new ArrayList<String>(arguments));
        this.argumentsSupplied = argumentsSupplied;
    }

    /**
     * The facts of the running process.
     *
     * @implNote Java 8 cannot read its own argument vector, so the embedder passes the one it was started with.
     * @param arguments the host process's argument vector, as the embedder received it
     * @return the process's environment and home directory, with the given arguments
     */
    public static LibraryContext ofProcess(List<String> arguments) {
        return new LibraryContext(System.getenv(), System.getProperty("user.home"), arguments);
    }

    /**
     * The facts of the running process without its argument vector, so a call to a function that
     * declares {@code arguments} is refused rather than sent an empty one.
     *
     * @return the process's environment and home directory, with no arguments supplied
     */
    public static LibraryContext ofProcess() {
        return new LibraryContext(System.getenv(), System.getProperty("user.home"), null, false);
    }

    /** @return every environment variable, by name */
    public Map<String, String> environment() {
        return environment;
    }

    /** @return the user's home directory */
    public String home() {
        return home;
    }

    /** @return the host process's argument vector, or null when the embedder supplied none */
    public List<String> arguments() {
        return arguments;
    }

    /** @return the first fact that is not the shape the wire needs, or null when every one is */
    static String malformedFact(LibraryContext context) {
        if (context == null || !isStringMap(context.environment)) {
            return ENVIRONMENT;
        }
        if (context.home == null) {
            return HOME;
        }
        if (context.argumentsSupplied && !isStringCollection(context.arguments)) {
            return ARGUMENTS;
        }
        return null;
    }

    /** @return whether the embedder supplied an argument vector, which {@link #ofProcess()} does not */
    boolean argumentsSupplied() {
        return argumentsSupplied;
    }

    static String shapeOf(String fact) {
        if (ENVIRONMENT.equals(fact)) {
            return "a map whose keys and values are all strings";
        }
        return HOME.equals(fact) ? "a string" : "a list of strings";
    }

    private static boolean isStringMap(Map<?, ?> values) {
        return values != null && isStringCollection(values.keySet()) && isStringCollection(values.values());
    }

    private static boolean isStringCollection(Collection<?> values) {
        if (values == null) {
            return false;
        }
        for (Object value : values) {
            if (!(value instanceof String)) {
                return false;
            }
        }
        return true;
    }

    /** @return the facts the function declared, in the declaration's order, as the wire carries them */
    JsonObject declaredFacts(List<String> declared) {
        JsonObject facts = new JsonObject();
        for (String fact : declared) {
            facts.add(fact, factJson(fact));
        }
        return facts;
    }

    private JsonElement factJson(String fact) {
        if (ENVIRONMENT.equals(fact)) {
            JsonObject variables = new JsonObject();
            for (Map.Entry<String, String> variable : environment.entrySet()) {
                variables.addProperty(variable.getKey(), variable.getValue());
            }
            return variables;
        }
        if (HOME.equals(fact)) {
            return new JsonPrimitive(home);
        }
        JsonArray vector = new JsonArray();
        for (String argument : arguments) {
            vector.add(argument);
        }
        return vector;
    }
}
