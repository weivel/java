package io.github.intisy.ai.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Holds the answer schemas to the published ones, definition by definition.
 *
 * @implNote Same arrangement as {@link PublishedSchemaTest}: the file in weivel/model is the
 * expected side and the Java is what moves. The sibling files are read from the directory the
 * manifest schema was found in.
 */
class PublishedAnswerSchemasTest {

    private static final File MODEL_DIRECTORY = new File(System.getProperty("model.schema", "plugin.schema.json"))
            .getAbsoluteFile().getParentFile();

    private static Map<String, Object> publishedFile(String name) throws IOException {
        File file = new File(MODEL_DIRECTORY, name);
        assertTrue(file.isFile(), name + " is read from weivel/model, checked out beside this repository; not found at " + file);
        return PublishedSchemaTest.asMap(new TestJson(PublishedSchemaTest.read(file)).read());
    }

    private static void assertMirrors(String schemaId, String fileName) throws IOException {
        Map<String, Object> published = publishedFile(fileName);
        assertEquals(published.get("$id"), schemaId);
        Map<String, Object> definitions = PublishedSchemaTest.asMap(published.get("definitions"));
        Map<String, JsonSchema> mirrored = AnswerSchemas.definitions(schemaId);
        assertEquals(new ArrayList<String>(definitions.keySet()), new ArrayList<String>(mirrored.keySet()),
                fileName + " defines different names than AnswerSchemas");
        List<String> differences = new ArrayList<String>();
        for (String name : definitions.keySet()) {
            PublishedSchemaTest.compare(name, PublishedSchemaTest.inlinedDefinition(published, name),
                    mirrored.get(name).toTree(), differences);
        }
        assertTrue(differences.isEmpty(), fileName + " is the source and AnswerSchemas no longer implements it:\n  "
                + String.join("\n  ", differences));
    }

    @Test
    void implementsTheUiDataSchemaExactly() throws IOException {
        assertMirrors(AnswerSchemas.UI_DATA, "ui-data.schema.json");
    }

    @Test
    void implementsTheInstallMethodSchemaExactly() throws IOException {
        assertMirrors(AnswerSchemas.INSTALL_METHOD, "install-method.schema.json");
    }

    @Test
    void namesTheBindableComponentsTheModelBinds() {
        assertEquals(Arrays.asList("list", "table", "tree", "text", "code", "log", "diff", "detail", "chart",
                "progress", "badge"), AnswerSchemas.answerable(AnswerSchemas.UI_DATA));
    }
}
