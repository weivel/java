package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.intisy.ai.engine.PluginException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The walk from one service's schemas to Java types, collecting the imports and the nested
 * declarations the service's interface needs.
 *
 * @implNote It accepts and refuses exactly the schemas the TypeScript generator does, at the same
 * paths. Where TypeScript writes an anonymous structural type, Java needs a named one, so an object
 * with members becomes a nested class and a string enum a nested enum, each named by
 * {@link #nestedName}, and a name already taken is made distinct rather than refused.
 */
final class SchemaTypes {

    static final String CONTRACT_PACKAGE = "io.github.intisy.ai.api";

    private static final String JSON_ELEMENT = "com.google.gson.JsonElement";
    private static final String JSON_OBJECT = "com.google.gson.JsonObject";
    private static final String LIST = "java.util.List";
    private static final String OBJECTS = "java.util.Objects";
    private static final String SERIALIZED_NAME = "com.google.gson.annotations.SerializedName";
    private static final Pattern MODEL_DEFINITION = Pattern.compile("^https://weivel\\.github\\.io/model/([^#/]+)#/definitions/([^/]+)$");
    private static final Set<String> STRUCTURAL_KEYWORDS = set("type", "properties", "required", "additionalProperties", "items", "enum", "$ref");
    private static final List<String> OBJECT_KEYWORDS = Arrays.asList("properties", "required", "additionalProperties");
    private static final Map<String, JavaType> PRIMITIVE_TYPES = primitiveTypes();

    /** The keywords the TypeScript generator ignores, because they do not change the type; the same list. */
    private static final Set<String> TYPE_NEUTRAL_KEYWORDS = set(
            "title", "description", "$comment", "default", "examples", "readOnly", "writeOnly", "fix",
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
            "minLength", "maxLength", "pattern", "format",
            "minItems", "maxItems", "uniqueItems", "minProperties", "maxProperties");

    /** Simple names a nested type must not take, because the generated file already means something by them. */
    private static final Set<String> NAMES_IN_USE = set(
            "JsonElement", "JsonObject", "List", "Objects", "SerializedName", "ServiceType", "Override",
            "Object", "String", "Long", "Double", "Boolean", "Void");

    private final String libraryId;
    private final Set<String> imports = new TreeSet<String>();
    private final Set<String> contractTypes = new TreeSet<String>();
    private final Set<String> takenTypeNames = new HashSet<String>();
    private final List<String> declarations = new ArrayList<String>();

    SchemaTypes(String libraryId, String interfaceName) {
        this.libraryId = libraryId;
        takenTypeNames.addAll(NAMES_IN_USE);
        takenTypeNames.add(interfaceName);
        for (List<String> definitions : ModelDefinitions.published().values()) {
            for (String definition : definitions) {
                takenTypeNames.add(JavaNames.pascalCase(definition));
            }
        }
    }

    /** @return the fully qualified names the service's file imports, sorted */
    Set<String> imports() {
        return Collections.unmodifiableSet(imports);
    }

    /** @return the simple names of the contract types a {@code $ref} mapped to */
    Set<String> contractTypes() {
        return Collections.unmodifiableSet(contractTypes);
    }

    /** @return the source of each nested class and enum, parents before the types their members need */
    List<String> declarations() {
        return Collections.unmodifiableList(declarations);
    }

    PluginException refusal(String path, String reason, String fix) {
        return new PluginException(libraryId, "cannot generate types: " + path + " " + reason, fix);
    }

    /**
     * @param typeName the name a nested type generated for this schema takes
     * @implNote A nested type is named by its path: the function's name, then {@code Argument} or
     * {@code Result}, then each property's name on the way down, and {@code Item} for an array's
     * elements, each part in Pascal case. {@code repeat}'s argument is {@code RepeatArgument} and
     * the elements of its {@code owners} are {@code RepeatArgumentOwnersItem}.
     */
    JavaType typeOf(JsonElement schema, String path, String typeName) {
        if (isBoolean(schema)) {
            return schema.getAsBoolean() ? imported(JSON_ELEMENT) : JavaType.reference("Void");
        }
        if (!schema.isJsonObject()) {
            throw refusal(path, "is not a schema object", "write the schema as an object; {} accepts anything");
        }
        JsonObject object = schema.getAsJsonObject();
        refuseUnsupportedKeywords(object, path);
        if (object.has("$ref")) {
            return referenceType(object, path);
        }
        refuseMisplacedKeywords(object, path);
        if (object.has("enum")) {
            return enumType(object, path, typeName);
        }
        return declaredType(object, path, typeName);
    }

    static String nestedName(String typeName, String part) {
        return typeName + JavaNames.pascalCase(part);
    }

    private void refuseUnsupportedKeywords(JsonObject schema, String path) {
        for (String keyword : schema.keySet()) {
            if (!STRUCTURAL_KEYWORDS.contains(keyword) && !TYPE_NEUTRAL_KEYWORDS.contains(keyword)) {
                throw refusal(path + "." + keyword, "is not a schema form the generator supports",
                        "use object, array, string, integer, number, boolean, enum, a $ref to a model definition, or {}");
            }
        }
    }

    private void refuseMisplacedKeywords(JsonObject schema, String path) {
        if (!isString(schema.get("type"), "object")) {
            for (String keyword : OBJECT_KEYWORDS) {
                if (schema.has(keyword)) {
                    throw refusal(path + "." + keyword, "applies only beside \"type\": \"object\"", "declare the schema's type as object");
                }
            }
        }
        if (!isString(schema.get("type"), "array") && schema.has("items")) {
            throw refusal(path + ".items", "applies only beside \"type\": \"array\"", "declare the schema's type as array");
        }
    }

    private JavaType referenceType(JsonObject schema, String path) {
        for (String keyword : schema.keySet()) {
            if (!keyword.equals("$ref") && STRUCTURAL_KEYWORDS.contains(keyword)) {
                throw refusal(path + "." + keyword, "sits beside a $ref, which ignores it", "remove it, or drop the $ref");
            }
        }
        JsonElement reference = schema.get("$ref");
        Matcher definition = isString(reference) ? MODEL_DEFINITION.matcher(reference.getAsString()) : null;
        if (definition == null || !definition.matches()) {
            throw refusal(path + ".$ref", "is " + reference + ", which does not name a weivel/model definition",
                    "reference https://weivel.github.io/model/<file>#/definitions/<name>, or declare the shape inline");
        }
        return contractType(definition.group(1), definition.group(2), path);
    }

    private JavaType contractType(String file, String definition, String path) {
        List<String> published = ModelDefinitions.published().get(file);
        if (published == null || !published.contains(definition)) {
            throw refusal(path + ".$ref", "names " + definition + " in " + file + ", which weivel/model does not publish",
                    "reference a definition the model publishes, or declare the shape inline");
        }
        String simpleName = JavaNames.pascalCase(definition);
        if (!contractDeclares(simpleName)) {
            throw refusal(path + ".$ref", "names " + definition + " in " + file + ", for which the contract declares no "
                    + CONTRACT_PACKAGE + "." + simpleName, "add the type to the contract, or declare the shape inline");
        }
        contractTypes.add(simpleName);
        return imported(CONTRACT_PACKAGE + "." + simpleName);
    }

    private static boolean contractDeclares(String simpleName) {
        try {
            Class.forName(CONTRACT_PACKAGE + "." + simpleName, false, SchemaTypes.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private JavaType enumType(JsonObject schema, String path, String typeName) {
        JsonElement values = schema.get("enum");
        if (!values.isJsonArray() || values.getAsJsonArray().size() == 0 || !allEnumValues(values.getAsJsonArray())) {
            throw refusal(path + ".enum", "is not a non-empty list of strings, numbers, booleans or null", "list the allowed primitive values");
        }
        return EnumValues.of(values.getAsJsonArray()).javaType(this, typeName);
    }

    private static boolean allEnumValues(JsonArray values) {
        for (JsonElement value : values) {
            if (!value.isJsonNull() && !value.isJsonPrimitive()) {
                return false;
            }
        }
        return true;
    }

    private Set<String> requiredNames(JsonObject schema, String path) {
        JsonElement required = absentIfNull(schema.get("required"));
        if (required == null) {
            return Collections.emptySet();
        }
        if (!required.isJsonArray()) {
            throw refusal(path + ".required", "is not a list of property names", "list the required property names as strings");
        }
        Set<String> names = new LinkedHashSet<String>();
        for (JsonElement name : required.getAsJsonArray()) {
            if (!isString(name)) {
                throw refusal(path + ".required", "is not a list of property names", "list the required property names as strings");
            }
            names.add(name.getAsString());
        }
        return names;
    }

    /**
     * @implNote An object with no members that accepts any further property is exactly a JSON object,
     * so it maps onto {@code JsonObject}. One with members becomes a class, which carries the declared
     * properties only: a further property an open object allows is dropped when Gson reads it and
     * cannot be sent, because a final class cannot hold an index signature.
     */
    private JavaType objectType(JsonObject schema, String path, String typeName) {
        JsonElement additional = schema.get("additionalProperties");
        if (additional != null && !isBoolean(additional)) {
            throw refusal(path + ".additionalProperties", "is a schema, which the generator does not support",
                    "set additionalProperties to true or false, or remove it");
        }
        JsonElement properties = absentIfNull(schema.get("properties"));
        if (properties != null && !properties.isJsonObject()) {
            throw refusal(path + ".properties", "is not an object of schemas", "map each property name to its schema");
        }
        JsonObject declared = properties == null ? new JsonObject() : properties.getAsJsonObject();
        Set<String> required = requiredNames(schema, path);
        boolean closed = additional != null && !additional.getAsBoolean();
        if (declared.size() == 0 && required.isEmpty() && !closed) {
            return imported(JSON_OBJECT);
        }
        String className = claimTypeName(typeName);
        int slot = reserveDeclaration();
        List<JavaDeclarations.Member> members = members(declared, required, path, className);
        declare(slot, JavaDeclarations.classSource(className, members));
        if (JavaDeclarations.usesObjects(members)) {
            imports.add(OBJECTS);
        }
        if (JavaDeclarations.usesSerializedName(members)) {
            imports.add(SERIALIZED_NAME);
        }
        return JavaType.reference(className);
    }

    private List<JavaDeclarations.Member> members(JsonObject declared, Set<String> required, String path, String typeName) {
        MemberNames names = new MemberNames();
        List<JavaDeclarations.Member> members = new ArrayList<JavaDeclarations.Member>();
        for (Map.Entry<String, JsonElement> property : declared.entrySet()) {
            String name = property.getKey();
            JavaType type = typeOf(property.getValue(), path + ".properties." + name, nestedName(typeName, name));
            String spelled = required.contains(name) ? type.present() : type.absentable();
            members.add(new JavaDeclarations.Member(name, names.claim(name), spelled));
        }
        for (String name : required) {
            if (!declared.has(name)) {
                members.add(new JavaDeclarations.Member(name, names.claim(name), imported(JSON_ELEMENT).present()));
            }
        }
        return members;
    }

    private JavaType arrayType(JsonObject schema, String path, String typeName) {
        JsonElement items = schema.get("items");
        if (items == null || !(items.isJsonObject() || isBoolean(items))) {
            throw refusal(path + ".items", "is not one schema for every element", "give the array a single items schema");
        }
        JavaType element = typeOf(items, path + ".items", typeName + "Item");
        imports.add(LIST);
        return JavaType.reference("List<" + element.absentable() + ">");
    }

    private JavaType declaredType(JsonObject schema, String path, String typeName) {
        JsonElement type = schema.get("type");
        if (type == null) {
            return imported(JSON_ELEMENT);
        }
        if (isString(type, "object")) {
            return objectType(schema, path, typeName);
        }
        if (isString(type, "array")) {
            return arrayType(schema, path, typeName);
        }
        if (isString(type) && PRIMITIVE_TYPES.containsKey(type.getAsString())) {
            return PRIMITIVE_TYPES.get(type.getAsString());
        }
        throw refusal(path + ".type", "is " + type + ", which the generator does not support",
                "use one of object, array, string, integer, number or boolean");
    }

    JavaType declareEnum(String typeName, List<String> values) {
        String enumName = claimTypeName(typeName);
        int slot = reserveDeclaration();
        Map<String, String> constantsByValue = new LinkedHashMap<String, String>();
        Set<String> taken = new HashSet<String>();
        for (String value : values) {
            String base = JavaNames.constantName(value);
            String candidate = base;
            for (int suffix = 2; !taken.add(candidate); suffix++) {
                candidate = base + "_" + suffix;
            }
            constantsByValue.put(value, candidate);
        }
        List<JavaDeclarations.Constant> constants = new ArrayList<JavaDeclarations.Constant>();
        for (Map.Entry<String, String> constant : constantsByValue.entrySet()) {
            constants.add(new JavaDeclarations.Constant(constant.getKey(), constant.getValue()));
        }
        declare(slot, JavaDeclarations.enumSource(enumName, constants));
        imports.add(SERIALIZED_NAME);
        return JavaType.reference(enumName);
    }

    /**
     * @implNote A path whose name is already taken, by another schema or by a name the file uses,
     * gets {@code 2}, {@code 3} and so on appended, in the order the walk reaches it. A property
     * named {@code _}, or one made of letters outside ASCII, adds nothing to the name, so its nested type would otherwise
     * be its parent's.
     */
    private String claimTypeName(String typeName) {
        String candidate = typeName;
        for (int suffix = 2; !takenTypeNames.add(candidate); suffix++) {
            candidate = typeName + suffix;
        }
        return candidate;
    }

    private int reserveDeclaration() {
        declarations.add(null);
        return declarations.size() - 1;
    }

    private void declare(int slot, String source) {
        declarations.set(slot, source);
    }

    JavaType imported(String qualifiedName) {
        imports.add(qualifiedName);
        return JavaType.reference(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
    }

    private static JsonElement absentIfNull(JsonElement value) {
        return value == null || value.isJsonNull() ? null : value;
    }

    private static boolean isBoolean(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static boolean isString(JsonElement value, String expected) {
        return isString(value) && value.getAsString().equals(expected);
    }

    private static Map<String, JavaType> primitiveTypes() {
        Map<String, JavaType> types = new HashMap<String, JavaType>();
        types.put("string", JavaType.reference("String"));
        types.put("integer", JavaType.primitive("long", "Long"));
        types.put("number", JavaType.primitive("double", "Double"));
        types.put("boolean", JavaType.primitive("boolean", "Boolean"));
        return Collections.unmodifiableMap(types);
    }

    private static Set<String> set(String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(names)));
    }

    /**
     * The field names of one generated class, each distinct even once capitalised into its getter.
     *
     * @implNote {@code Class} is held from the start because its getter would be {@code getClass()},
     * which {@code Object} declares final.
     */
    private static final class MemberNames {
        private final Set<String> capitalised = new HashSet<String>(Collections.singleton("Class"));

        String claim(String propertyName) {
            String base = JavaNames.memberName(propertyName);
            String candidate = base;
            for (int suffix = 2; !capitalised.add(JavaNames.capitalized(candidate)); suffix++) {
                candidate = base + suffix;
            }
            return candidate;
        }
    }

    /**
     * The distinct values of an enum, and the Java type closest to the set they allow.
     *
     * @implNote Only an enum of strings becomes a Java enum, because a Gson enum constant is written
     * as a string. Booleans alone map to {@code boolean} and numbers alone to {@code long} or
     * {@code double}, which allow more values than the enum does; a mix of kinds maps to
     * {@code JsonElement}. A {@code null} among the values only makes the type a reference type, and
     * {@code null} alone is {@code Void}.
     */
    private static final class EnumValues {
        private final List<String> strings = new ArrayList<String>();
        private final List<JsonPrimitive> others = new ArrayList<JsonPrimitive>();
        private boolean allowsNull;

        static EnumValues of(JsonArray values) {
            EnumValues distinct = new EnumValues();
            Set<String> seen = new HashSet<String>();
            for (JsonElement value : values) {
                if (seen.add(value.toString())) {
                    distinct.add(value);
                }
            }
            return distinct;
        }

        private void add(JsonElement value) {
            if (value.isJsonNull()) {
                allowsNull = true;
            } else if (value.getAsJsonPrimitive().isString()) {
                strings.add(value.getAsString());
            } else {
                others.add(value.getAsJsonPrimitive());
            }
        }

        JavaType javaType(SchemaTypes walk, String typeName) {
            if (strings.isEmpty() && others.isEmpty()) {
                return JavaType.reference("Void");
            }
            if (others.isEmpty()) {
                return walk.declareEnum(typeName, strings);
            }
            JavaType type = strings.isEmpty() ? scalarType(others) : null;
            if (type == null) {
                return walk.imported(JSON_ELEMENT);
            }
            return allowsNull ? type.boxed() : type;
        }

        private static JavaType scalarType(Collection<JsonPrimitive> values) {
            boolean booleans = true;
            boolean numbers = true;
            boolean integral = true;
            for (JsonPrimitive value : values) {
                booleans &= value.isBoolean();
                numbers &= value.isNumber();
                integral &= value.isNumber() && isIntegral(value);
            }
            if (booleans) {
                return PRIMITIVE_TYPES.get("boolean");
            }
            if (numbers) {
                return PRIMITIVE_TYPES.get(integral ? "integer" : "number");
            }
            return null;
        }

        private static boolean isIntegral(JsonPrimitive number) {
            return new BigDecimal(number.getAsString()).stripTrailingZeros().scale() <= 0;
        }
    }
}
