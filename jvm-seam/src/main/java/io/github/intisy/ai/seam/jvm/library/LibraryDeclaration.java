package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.intisy.ai.engine.PluginException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A library's {@code library.json}, as {@code library.schema.json} in {@code weivel/model} defines it.
 *
 * @implNote Validated structurally here rather than by the engine's schema validator: that one
 * cannot express {@code additionalProperties: false}, and taking a schema library into
 * {@code engine} would break its rule of depending on nothing. Every rule of the published schema
 * is checked, and each violation is named the way the TypeScript implementation names it, with
 * the fix the schema carries. The rules are package-private constants so that
 * {@code LibraryDeclarationSchemaTest} holds each one to the published schema.
 */
public final class LibraryDeclaration {

    /** One exported function of the {@code json} kind: UTF-8 JSON in, UTF-8 JSON out. */
    public static final class FunctionDeclaration {
        private final String name;
        private final String symbol;
        private final String kind;
        private final JsonElement argument;
        private final JsonElement result;
        private final List<String> context;

        FunctionDeclaration(String name, String symbol, String kind, JsonElement argument, JsonElement result, List<String> context) {
            this.name = name;
            this.symbol = symbol;
            this.kind = kind;
            this.argument = argument;
            this.result = result;
            this.context = Collections.unmodifiableList(new ArrayList<String>(context));
        }

        /** @return the name a plugin calls the function by */
        public String name() {
            return name;
        }

        /** @return the exported C symbol that implements it */
        public String symbol() {
            return symbol;
        }

        /** @return how it is called; {@code json} is the only kind */
        public String kind() {
            return kind;
        }

        /** @return a copy of the JSON Schema the argument is declared by: an object, or a boolean */
        public JsonElement argument() {
            return argument.deepCopy();
        }

        /** @return a copy of the JSON Schema the result is declared by: an object, or a boolean */
        public JsonElement result() {
            return result.deepCopy();
        }

        /** @return the facts of the calling process the function reads, in declaration order; empty when it declares none */
        public List<String> context() {
            return context;
        }
    }

    /** One service a library offers, and the functions it is made of. */
    public static final class ServiceDeclaration {
        private final String id;
        private final List<FunctionDeclaration> functions;

        ServiceDeclaration(String id, List<FunctionDeclaration> functions) {
            this.id = id;
            this.functions = Collections.unmodifiableList(new ArrayList<FunctionDeclaration>(functions));
        }

        /** @return the service id plugins consume */
        public String id() {
            return id;
        }

        /** @return the functions of this service, in declaration order */
        public List<FunctionDeclaration> functions() {
            return functions;
        }
    }

    private static final String FILE = "library.json";
    private static final Pattern KEBAB_ID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern C_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern FUNCTION_NAME = Pattern.compile("^[a-z][A-Za-z0-9]*$");
    private static final String ID_FIX = "use lowercase words joined by single hyphens, for example \"echo\"";

    static final Set<String> DECLARATION_KEYS = keys("$schema", "id", "version", "binary", "release", "services");
    static final Set<String> DECLARATION_REQUIRED = keys("id", "version", "binary", "release", "services");
    static final Map<String, Pattern> DECLARATION_PATTERNS = patterns("id", KEBAB_ID, "binary", C_IDENTIFIER, "release", C_IDENTIFIER);
    static final int MIN_VERSION_LENGTH = 1;
    static final int MIN_SERVICES = 1;

    static final Set<String> SERVICE_KEYS = keys("id", "functions");
    static final Set<String> SERVICE_REQUIRED = keys("id", "functions");
    static final Map<String, Pattern> SERVICE_PATTERNS = patterns("id", KEBAB_ID);
    static final int MIN_FUNCTIONS = 1;

    static final Set<String> FUNCTION_KEYS = keys("name", "symbol", "kind", "argument", "result", "context");
    static final Set<String> FUNCTION_REQUIRED = keys("name", "symbol", "kind", "argument", "result");
    static final Map<String, Pattern> FUNCTION_PATTERNS = patterns("name", FUNCTION_NAME, "symbol", C_IDENTIFIER);
    static final List<String> KINDS = Collections.singletonList("json");
    static final List<String> CONTEXT_FACTS = Collections.unmodifiableList(Arrays.asList("environment", "home", "arguments"));
    static final boolean CONTEXT_FACTS_UNIQUE = true;
    static final int MIN_CONTEXT_FACTS = 1;
    private static final String CONTEXT_FIX = "list the facts the function reads, each once, from \"environment\", \"home\" and \"arguments\", or remove \"context\"";

    private final String id;
    private final String version;
    private final String binary;
    private final String release;
    private final List<ServiceDeclaration> services;

    private LibraryDeclaration(String id, String version, String binary, String release, List<ServiceDeclaration> services) {
        this.id = id;
        this.version = version;
        this.binary = binary;
        this.release = release;
        this.services = Collections.unmodifiableList(new ArrayList<ServiceDeclaration>(services));
    }

    /** @return the library's permanent identity, matching its repository name */
    public String id() {
        return id;
    }

    /** @return the library's own version */
    public String version() {
        return version;
    }

    /** @return the shared library's base name, without a platform suffix or extension */
    public String binary() {
        return binary;
    }

    /** @return the exported symbol that frees a buffer the library returned */
    public String release() {
        return release;
    }

    /** @return the services the library offers, in declaration order */
    public List<ServiceDeclaration> services() {
        return services;
    }

    /**
     * Reads a declaration, refusing it with every violation named in the detail and the first one's fix.
     *
     * @param text the content of {@code library.json}
     * @return the declaration it describes
     * @throws PluginException naming the library, or {@code library} when it declares no usable id
     */
    public static LibraryDeclaration parse(String text) {
        JsonElement candidate = parsedJson(text);
        List<Violation> violations = new ArrayList<Violation>();
        checkDeclaration(candidate, violations);
        if (!violations.isEmpty()) {
            throw refusal(declaredId(candidate), violations);
        }
        return declarationOf(candidate.getAsJsonObject());
    }

    private static JsonElement parsedJson(String text) {
        try {
            return StrictJson.parse(text);
        } catch (IOException malformed) {
            throw new PluginException("library", FILE + " is not JSON: " + malformed.getMessage(), "publish a library.json that parses as JSON");
        }
    }

    private static String declaredId(JsonElement candidate) {
        if (candidate.isJsonObject()) {
            JsonElement declared = candidate.getAsJsonObject().get("id");
            if (isString(declared) && !declared.getAsString().isEmpty()) {
                return declared.getAsString();
            }
        }
        return "library";
    }

    private static PluginException refusal(String libraryId, List<Violation> violations) {
        StringBuilder detail = new StringBuilder(FILE).append(": ");
        for (int index = 0; index < violations.size(); index++) {
            detail.append(index == 0 ? "" : "; ").append(violations.get(index).message);
        }
        return new PluginException(libraryId, detail.toString(), violations.get(0).fix);
    }

    private static void checkDeclaration(JsonElement candidate, List<Violation> violations) {
        if (!candidate.isJsonObject()) {
            violations.add(new Violation("the declaration must be an object", "publish library.json as one JSON object"));
            return;
        }
        JsonObject declaration = candidate.getAsJsonObject();
        checkKeys(declaration, "", DECLARATION_KEYS, violations);
        JsonElement schemaPointer = declaration.get("$schema");
        if (schemaPointer != null && !isString(schemaPointer)) {
            violations.add(new Violation("/$schema must be a string", "point \"$schema\" at the published library.schema.json"));
        }
        Level level = new Level(declaration, "", DECLARATION_REQUIRED, DECLARATION_PATTERNS, violations);
        level.checkPattern("id", ID_FIX);
        checkVersion(level);
        level.checkPattern("binary", "use a C identifier for the binary's base name, for example \"echo\"");
        level.checkPattern("release", "name the exported release function as a C identifier, for example \"echo_release\"");
        JsonArray services = level.arrayOfAtLeast("services", MIN_SERVICES, "declare at least one service under \"services\"");
        for (int index = 0; services != null && index < services.size(); index++) {
            checkService(services.get(index), "/services/" + index, violations);
        }
    }

    private static void checkVersion(Level level) {
        String fix = "set \"version\" to the library's version, for example \"0.1.0\"";
        JsonElement version = level.present("version", fix);
        if (version != null && (!isString(version) || version.getAsString().length() < MIN_VERSION_LENGTH)) {
            level.violations.add(new Violation("/version must be a non-empty string", fix));
        }
    }

    private static void checkService(JsonElement candidate, String path, List<Violation> violations) {
        if (!candidate.isJsonObject()) {
            violations.add(new Violation(path + " must be an object", "declare each service as { \"id\": ..., \"functions\": [...] }"));
            return;
        }
        JsonObject service = candidate.getAsJsonObject();
        checkKeys(service, path, SERVICE_KEYS, violations);
        Level level = new Level(service, path, SERVICE_REQUIRED, SERVICE_PATTERNS, violations);
        level.checkPattern("id", ID_FIX);
        JsonArray functions = level.arrayOfAtLeast("functions", MIN_FUNCTIONS, "declare at least one function under \"functions\"");
        for (int index = 0; functions != null && index < functions.size(); index++) {
            checkFunction(functions.get(index), path + "/functions/" + index, violations);
        }
    }

    private static void checkFunction(JsonElement candidate, String path, List<Violation> violations) {
        if (!candidate.isJsonObject()) {
            violations.add(new Violation(path + " must be an object", "declare each function as { \"name\", \"symbol\", \"kind\", \"argument\", \"result\" }"));
            return;
        }
        JsonObject function = candidate.getAsJsonObject();
        checkKeys(function, path, FUNCTION_KEYS, violations);
        Level level = new Level(function, path, FUNCTION_REQUIRED, FUNCTION_PATTERNS, violations);
        level.checkPattern("name", "start \"name\" with a lowercase letter and use letters and digits only, for example \"repeat\"");
        level.checkPattern("symbol", "use a C identifier for the exported symbol, for example \"echo_repeat\"");
        checkKind(level);
        checkSchemaValue(level, "argument");
        checkSchemaValue(level, "result");
        checkContext(level);
    }

    private static void checkContext(Level level) {
        JsonElement context = level.present("context", CONTEXT_FIX);
        if (context == null) {
            return;
        }
        if (!context.isJsonArray() || context.getAsJsonArray().size() < MIN_CONTEXT_FACTS) {
            level.violations.add(new Violation(level.path + "/context must be an array of at least " + Level.itemCount(MIN_CONTEXT_FACTS), CONTEXT_FIX));
            return;
        }
        JsonArray facts = context.getAsJsonArray();
        Set<String> seen = new LinkedHashSet<String>();
        for (int index = 0; index < facts.size(); index++) {
            JsonElement fact = facts.get(index);
            if (!isString(fact) || !CONTEXT_FACTS.contains(fact.getAsString())) {
                level.violations.add(new Violation(level.path + "/context/" + index + " must be one of " + CONTEXT_FACTS, CONTEXT_FIX));
            } else if (CONTEXT_FACTS_UNIQUE && !seen.add(fact.getAsString())) {
                level.violations.add(new Violation(level.path + "/context must not repeat \"" + fact.getAsString() + "\"", CONTEXT_FIX));
            }
        }
    }

    private static void checkKind(Level level) {
        String fix = "set \"kind\" to \"json\", the only kind there is";
        JsonElement kind = level.present("kind", fix);
        if (kind != null && (!isString(kind) || !KINDS.contains(kind.getAsString()))) {
            level.violations.add(new Violation(level.path + "/kind must be \"json\"", fix));
        }
    }

    private static void checkSchemaValue(Level level, String key) {
        String fix = "give \"" + key + "\" a JSON Schema: an object, or true or false";
        JsonElement schema = level.present(key, fix);
        if (schema != null && !schema.isJsonObject() && !(schema.isJsonPrimitive() && schema.getAsJsonPrimitive().isBoolean())) {
            level.violations.add(new Violation(level.path + "/" + key + " must be a JSON Schema object or a boolean", fix));
        }
    }

    private static void checkKeys(JsonObject owner, String path, Set<String> allowed, List<Violation> violations) {
        for (Map.Entry<String, JsonElement> entry : owner.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                violations.add(new Violation(path + "/" + entry.getKey() + " is not a property library.schema.json defines",
                        "remove \"" + entry.getKey() + "\" from library.json"));
            }
        }
    }

    private static Violation missing(String path, String key, String fix) {
        return new Violation((path.isEmpty() ? "the declaration" : path) + " must have \"" + key + "\"", fix);
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && ((JsonPrimitive) value).isString();
    }

    private static LibraryDeclaration declarationOf(JsonObject declaration) {
        List<ServiceDeclaration> services = new ArrayList<ServiceDeclaration>();
        for (JsonElement service : declaration.getAsJsonArray("services")) {
            services.add(serviceOf(service.getAsJsonObject()));
        }
        return new LibraryDeclaration(
                declaration.get("id").getAsString(),
                declaration.get("version").getAsString(),
                declaration.get("binary").getAsString(),
                declaration.get("release").getAsString(),
                services);
    }

    private static ServiceDeclaration serviceOf(JsonObject service) {
        List<FunctionDeclaration> functions = new ArrayList<FunctionDeclaration>();
        for (JsonElement function : service.getAsJsonArray("functions")) {
            JsonObject declared = function.getAsJsonObject();
            functions.add(new FunctionDeclaration(
                    declared.get("name").getAsString(),
                    declared.get("symbol").getAsString(),
                    declared.get("kind").getAsString(),
                    declared.get("argument").deepCopy(),
                    declared.get("result").deepCopy(),
                    contextOf(declared)));
        }
        return new ServiceDeclaration(service.get("id").getAsString(), functions);
    }

    private static List<String> contextOf(JsonObject function) {
        List<String> facts = new ArrayList<String>();
        JsonElement context = function.get("context");
        if (context != null) {
            for (JsonElement fact : context.getAsJsonArray()) {
                facts.add(fact.getAsString());
            }
        }
        return facts;
    }

    private static Set<String> keys(String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(names)));
    }

    private static Map<String, Pattern> patterns(Object... keysAndPatterns) {
        Map<String, Pattern> patterns = new LinkedHashMap<String, Pattern>();
        for (int index = 0; index < keysAndPatterns.length; index += 2) {
            patterns.put((String) keysAndPatterns[index], (Pattern) keysAndPatterns[index + 1]);
        }
        return Collections.unmodifiableMap(patterns);
    }

    /** One object of the declaration being checked, with the rules its level of the schema sets. */
    private static final class Level {
        private final JsonObject owner;
        private final String path;
        private final Set<String> required;
        private final Map<String, Pattern> patterns;
        private final List<Violation> violations;

        Level(JsonObject owner, String path, Set<String> required, Map<String, Pattern> patterns, List<Violation> violations) {
            this.owner = owner;
            this.path = path;
            this.required = required;
            this.patterns = patterns;
            this.violations = violations;
        }

        /** @return the value under {@code key}, or null when it is absent, which is a violation only when the key is required */
        JsonElement present(String key, String fix) {
            JsonElement value = owner.get(key);
            if (value == null && required.contains(key)) {
                violations.add(missing(path, key, fix));
            }
            return value;
        }

        void checkPattern(String key, String fix) {
            Pattern pattern = patterns.get(key);
            JsonElement value = present(key, fix);
            if (value != null && (!isString(value) || !pattern.matcher(value.getAsString()).matches())) {
                violations.add(new Violation(path + "/" + key + " must match " + pattern.pattern(), fix));
            }
        }

        JsonArray arrayOfAtLeast(String key, int minimum, String fix) {
            JsonElement value = present(key, fix);
            if (value == null) {
                return null;
            }
            if (!value.isJsonArray() || value.getAsJsonArray().size() < minimum) {
                violations.add(new Violation(path + "/" + key + " must be an array of at least " + itemCount(minimum), fix));
                return null;
            }
            return value.getAsJsonArray();
        }

        private static String itemCount(int minimum) {
            return minimum == 1 ? "one item" : minimum + " items";
        }
    }

    private static final class Violation {
        private final String message;
        private final String fix;

        Violation(String message, String fix) {
            this.message = message;
            this.fix = fix;
        }
    }
}
