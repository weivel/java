package io.github.intisy.ai.seam.jvm.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Holds {@link LibraryDeclaration}'s structural checks to the published {@code library.schema.json}.
 *
 * @implNote TypeScript compiles the real schema, while Java keeps its own copy of the rules, so a
 * schema change that only TypeScript followed would leave every suite green and the two hosts
 * disagreeing about one declaration. Read from the model sibling, which is a declared input of
 * the test task, so editing the schema re-runs this.
 */
class LibraryDeclarationSchemaTest {

    private static JsonObject declarationLevel;
    private static JsonObject serviceLevel;
    private static JsonObject functionLevel;

    @BeforeAll
    static void readThePublishedSchema() throws IOException {
        String modelDirectory = System.getProperty("model.directory");
        byte[] schema = Files.readAllBytes(Paths.get(modelDirectory, "library.schema.json"));
        declarationLevel = JsonParser.parseString(new String(schema, StandardCharsets.UTF_8)).getAsJsonObject();
        serviceLevel = itemsOf(declarationLevel, "services");
        functionLevel = itemsOf(serviceLevel, "functions");
    }

    private static JsonObject itemsOf(JsonObject level, String arrayProperty) {
        return propertiesOf(level).getAsJsonObject(arrayProperty).getAsJsonObject("items");
    }

    private static JsonObject propertiesOf(JsonObject level) {
        return level.getAsJsonObject("properties");
    }

    private static Set<String> propertyNames(JsonObject level) {
        return new LinkedHashSet<String>(propertiesOf(level).keySet());
    }

    private static Set<String> required(JsonObject level) {
        Set<String> names = new LinkedHashSet<String>();
        for (JsonElement name : level.getAsJsonArray("required")) {
            names.add(name.getAsString());
        }
        return names;
    }

    private static Map<String, String> patterns(JsonObject level) {
        Map<String, String> patterns = new TreeMap<String, String>();
        for (Map.Entry<String, JsonElement> property : propertiesOf(level).entrySet()) {
            JsonElement pattern = property.getValue().getAsJsonObject().get("pattern");
            if (pattern != null) {
                patterns.put(property.getKey(), pattern.getAsString());
            }
        }
        return patterns;
    }

    private static Map<String, String> patternTexts(Map<String, Pattern> patterns) {
        Map<String, String> texts = new TreeMap<String, String>();
        for (Map.Entry<String, Pattern> pattern : patterns.entrySet()) {
            texts.put(pattern.getKey(), pattern.getValue().pattern());
        }
        return texts;
    }

    private static int minItemsOf(JsonObject level, String arrayProperty) {
        return propertiesOf(level).getAsJsonObject(arrayProperty).get("minItems").getAsInt();
    }

    private static int minLengthOf(JsonObject level, String property) {
        return propertiesOf(level).getAsJsonObject(property).get("minLength").getAsInt();
    }

    private static List<String> enumOf(JsonObject level, String property) {
        List<String> values = new ArrayList<String>();
        JsonArray declared = propertiesOf(level).getAsJsonObject(property).getAsJsonArray("enum");
        for (JsonElement value : declared) {
            values.add(value.getAsString());
        }
        return values;
    }

    @Test
    void checksTheDeclarationLevelTheSchemaPublishes() {
        assertEquals(propertyNames(declarationLevel), LibraryDeclaration.DECLARATION_KEYS);
        assertEquals(required(declarationLevel), LibraryDeclaration.DECLARATION_REQUIRED);
        assertEquals(patterns(declarationLevel), patternTexts(LibraryDeclaration.DECLARATION_PATTERNS));
        assertEquals(minLengthOf(declarationLevel, "version"), LibraryDeclaration.MIN_VERSION_LENGTH);
        assertEquals(minItemsOf(declarationLevel, "services"), LibraryDeclaration.MIN_SERVICES);
        assertEquals(false, declarationLevel.get("additionalProperties").getAsBoolean());
    }

    @Test
    void checksTheServiceLevelTheSchemaPublishes() {
        assertEquals(propertyNames(serviceLevel), LibraryDeclaration.SERVICE_KEYS);
        assertEquals(required(serviceLevel), LibraryDeclaration.SERVICE_REQUIRED);
        assertEquals(patterns(serviceLevel), patternTexts(LibraryDeclaration.SERVICE_PATTERNS));
        assertEquals(minItemsOf(serviceLevel, "functions"), LibraryDeclaration.MIN_FUNCTIONS);
        assertEquals(false, serviceLevel.get("additionalProperties").getAsBoolean());
    }

    @Test
    void checksTheFunctionLevelTheSchemaPublishes() {
        assertEquals(propertyNames(functionLevel), LibraryDeclaration.FUNCTION_KEYS);
        assertEquals(required(functionLevel), LibraryDeclaration.FUNCTION_REQUIRED);
        assertEquals(patterns(functionLevel), patternTexts(LibraryDeclaration.FUNCTION_PATTERNS));
        assertEquals(enumOf(functionLevel, "kind"), LibraryDeclaration.KINDS);
        assertEquals(false, functionLevel.get("additionalProperties").getAsBoolean());
    }
}
