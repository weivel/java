package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The definitions each {@code weivel/model} schema publishes, keyed by the file a {@code $ref} names.
 *
 * @implNote Read from a resource the build derives from the model sibling, so a library type may
 * reference exactly what the model publishes and nothing a hand-kept list claims it does. The
 * TypeScript generator derives its own copy from the same files.
 */
final class ModelDefinitions {

    private static final String RESOURCE = "model-definitions.json";
    private static Map<String, List<String>> published;

    private ModelDefinitions() {
    }

    static synchronized Map<String, List<String>> published() {
        if (published == null) {
            published = read();
        }
        return published;
    }

    private static Map<String, List<String>> read() {
        try (InputStream stream = ModelDefinitions.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(RESOURCE + " is missing beside " + ModelDefinitions.class.getName()
                        + "; the build derives it from weivel/model, so rebuild with the model sibling present");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return definitionsOf(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (IOException unreadable) {
            throw new IllegalStateException("cannot read " + RESOURCE + ": " + unreadable.getMessage(), unreadable);
        }
    }

    private static Map<String, List<String>> definitionsOf(JsonObject byFile) {
        Map<String, List<String>> definitions = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, JsonElement> file : byFile.entrySet()) {
            List<String> names = new ArrayList<String>();
            for (JsonElement name : file.getValue().getAsJsonArray()) {
                names.add(name.getAsString());
            }
            definitions.put(file.getKey(), Collections.unmodifiableList(names));
        }
        return Collections.unmodifiableMap(definitions);
    }
}
