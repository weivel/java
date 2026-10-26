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
 * the fix the schema carries.
 */
public final class LibraryDeclaration {

    /** One exported function of the {@code json} kind: UTF-8 JSON in, UTF-8 JSON out. */
    public static final class FunctionDeclaration {
        private final String name;
        private final String symbol;
        private final String kind;

        FunctionDeclaration(String name, String symbol, String kind) {
            this.name = name;
            this.symbol = symbol;
            this.kind = kind;
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
    private static final Set<String> DECLARATION_KEYS = keys("$schema", "id", "version", "binary", "release", "services");
    private static final Set<String> SERVICE_KEYS = keys("id", "functions");
    private static final Set<String> FUNCTION_KEYS = keys("name", "symbol", "kind", "argument", "result");

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
        checkPattern(declaration, "", "id", KEBAB_ID, ID_FIX, violations);
        checkVersion(declaration, violations);
        checkPattern(declaration, "", "binary", C_IDENTIFIER, "use a C identifier for the binary's base name, for example \"echo\"", violations);
        checkPattern(declaration, "", "release", C_IDENTIFIER, "name the exported release function as a C identifier, for example \"echo_release\"", violations);
        JsonArray services = nonEmptyArray(declaration, "", "services", "declare at least one service under \"services\"", violations);
        for (int index = 0; services != null && index < services.size(); index++) {
            checkService(services.get(index), "/services/" + index, violations);
        }
    }

    private static void checkVersion(JsonObject declaration, List<Violation> violations) {
        JsonElement version = declaration.get("version");
        String fix = "set \"version\" to the library's version, for example \"0.1.0\"";
        if (version == null) {
            violations.add(missing("", "version", fix));
        } else if (!isString(version) || version.getAsString().isEmpty()) {
            violations.add(new Violation("/version must be a non-empty string", fix));
        }
    }

    private static void checkService(JsonElement candidate, String path, List<Violation> violations) {
        if (!candidate.isJsonObject()) {
            violations.add(new Violation(path + " must be an object", "declare each service as { \"id\": ..., \"functions\": [...] }"));
            return;
        }
        JsonObject service = candidate.getAsJsonObject();
        checkKeys(service, path, SERVICE_KEYS, violations);
        checkPattern(service, path, "id", KEBAB_ID, ID_FIX, violations);
        JsonArray functions = nonEmptyArray(service, path, "functions", "declare at least one function under \"functions\"", violations);
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
        checkPattern(function, path, "name", FUNCTION_NAME, "start \"name\" with a lowercase letter and use letters and digits only, for example \"repeat\"", violations);
        checkPattern(function, path, "symbol", C_IDENTIFIER, "use a C identifier for the exported symbol, for example \"echo_repeat\"", violations);
        checkKind(function, path, violations);
        checkSchemaValue(function, path, "argument", violations);
        checkSchemaValue(function, path, "result", violations);
    }

    private static void checkKind(JsonObject function, String path, List<Violation> violations) {
        JsonElement kind = function.get("kind");
        String fix = "set \"kind\" to \"json\", the only kind there is";
        if (kind == null) {
            violations.add(missing(path, "kind", fix));
        } else if (!isString(kind) || !kind.getAsString().equals("json")) {
            violations.add(new Violation(path + "/kind must be \"json\"", fix));
        }
    }

    private static void checkSchemaValue(JsonObject function, String path, String key, List<Violation> violations) {
        JsonElement schema = function.get(key);
        String fix = "give \"" + key + "\" a JSON Schema: an object, or true or false";
        if (schema == null) {
            violations.add(missing(path, key, fix));
        } else if (!schema.isJsonObject() && !(schema.isJsonPrimitive() && schema.getAsJsonPrimitive().isBoolean())) {
            violations.add(new Violation(path + "/" + key + " must be a JSON Schema object or a boolean", fix));
        }
    }

    private static void checkPattern(JsonObject owner, String path, String key, Pattern pattern, String fix, List<Violation> violations) {
        JsonElement value = owner.get(key);
        if (value == null) {
            violations.add(missing(path, key, fix));
        } else if (!isString(value) || !pattern.matcher(value.getAsString()).matches()) {
            violations.add(new Violation(path + "/" + key + " must match " + pattern.pattern(), fix));
        }
    }

    private static JsonArray nonEmptyArray(JsonObject owner, String path, String key, String fix, List<Violation> violations) {
        JsonElement value = owner.get(key);
        if (value == null) {
            violations.add(missing(path, key, fix));
            return null;
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() == 0) {
            violations.add(new Violation(path + "/" + key + " must be an array of at least one item", fix));
            return null;
        }
        return value.getAsJsonArray();
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
                    declared.get("kind").getAsString()));
        }
        return new ServiceDeclaration(service.get("id").getAsString(), functions);
    }

    private static Set<String> keys(String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(names)));
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
