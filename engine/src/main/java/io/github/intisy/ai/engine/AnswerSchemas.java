package io.github.intisy.ai.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The schemas of what a view node's binding and an install method answer, one definition per answer
 * shape.
 *
 * @implNote Mirrors {@code ui-data.schema.json} and {@code install-method.schema.json} from
 * weivel/model with each {@code $ref} inlined, as {@link ManifestSchema} does for the manifest; the
 * model files are the source and {@code PublishedAnswerSchemasTest} fails when this drifts from them.
 */
public final class AnswerSchemas {

    /** Canonical URL of the binding answers, served by weivel/model's GitHub Pages. */
    public static final String UI_DATA = "https://weivel.github.io/model/ui-data.schema.json";

    /** Canonical URL of the install-method answers, served by weivel/model's GitHub Pages. */
    public static final String INSTALL_METHOD = "https://weivel.github.io/model/install-method.schema.json";

    private static final List<String> BINDABLE = Arrays.asList("list", "table", "tree", "text", "code", "log",
            "diff", "detail", "chart", "progress", "badge");

    private AnswerSchemas() {
    }

    /**
     * @param schemaId {@link #UI_DATA} or {@link #INSTALL_METHOD}
     * @return every definition the published file declares, helpers included, in its order
     * @throws IllegalArgumentException when no such schema is known
     */
    public static Map<String, JsonSchema> definitions(String schemaId) {
        if (UI_DATA.equals(schemaId)) {
            return uiData();
        }
        if (INSTALL_METHOD.equals(schemaId)) {
            return installMethod();
        }
        throw new IllegalArgumentException("no schema " + schemaId);
    }

    /**
     * @param schemaId {@link #UI_DATA} or {@link #INSTALL_METHOD}
     * @return the definitions an answer can be checked against, as opposed to the helpers they share
     * @throws IllegalArgumentException when no such schema is known
     */
    public static List<String> answerable(String schemaId) {
        if (UI_DATA.equals(schemaId)) {
            return BINDABLE;
        }
        return new ArrayList<String>(definitions(schemaId).keySet());
    }

    private static Map<String, JsonSchema> installMethod() {
        Map<String, JsonSchema> definitions = new LinkedHashMap<String, JsonSchema>();
        definitions.put("detectAnswer", detectAnswer());
        definitions.put("operationAnswer", operationAnswer());
        return definitions;
    }

    private static JsonSchema detectAnswer() {
        JsonSchema answer = described(JsonSchema.ofType("object"),
                "What detect answers. A package that is not installed is installed false, not a refusal.");
        answer.setRequired(Arrays.asList("installed"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("installed", JsonSchema.ofType("boolean"));
        properties.put("version", JsonSchema.ofType("string"));
        answer.setProperties(properties);
        return answer;
    }

    private static JsonSchema operationAnswer() {
        JsonSchema success = JsonSchema.ofType("object");
        success.setRequired(Arrays.asList("ok"));
        success.setProperties(single("ok", constant(Boolean.TRUE)));

        JsonSchema refusal = JsonSchema.ofType("object");
        refusal.setRequired(Arrays.asList("ok", "detail", "fix"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("ok", constant(Boolean.FALSE));
        properties.put("detail", JsonSchema.ofType("string"));
        properties.put("fix", JsonSchema.ofType("string"));
        refusal.setProperties(properties);

        JsonSchema answer = JsonSchema.anyOf(Arrays.asList(success, refusal));
        answer.setDescription("What install, update and remove answer: success, or a refusal carrying a detail and a fix.");
        return answer;
    }

    private static Map<String, JsonSchema> uiData() {
        JsonSchema itemsAnswer = itemsAnswer();
        JsonSchema textAnswer = textAnswer();
        JsonSchema entriesAnswer = entriesAnswer();
        JsonSchema measureAnswer = measureAnswer();
        JsonSchema chartAnswer = chartAnswer();
        JsonSchema badgeAnswer = badgeAnswer();

        Map<String, JsonSchema> definitions = new LinkedHashMap<String, JsonSchema>();
        definitions.put("scalar", scalar());
        definitions.put("tone", tone());
        definitions.put("item", item());
        definitions.put("column", column());
        definitions.put("entry", entry());
        definitions.put("series", series());
        definitions.put("itemsAnswer", itemsAnswer);
        definitions.put("textAnswer", textAnswer);
        definitions.put("entriesAnswer", entriesAnswer);
        definitions.put("measureAnswer", measureAnswer);
        definitions.put("chartAnswer", chartAnswer);
        definitions.put("badgeAnswer", badgeAnswer);
        definitions.put("list", itemsAnswer);
        definitions.put("table", itemsAnswer);
        definitions.put("tree", itemsAnswer);
        definitions.put("text", textAnswer);
        definitions.put("code", textAnswer);
        definitions.put("log", textAnswer);
        definitions.put("diff", textAnswer);
        definitions.put("detail", entriesAnswer);
        definitions.put("chart", chartAnswer);
        definitions.put("progress", measureAnswer);
        definitions.put("badge", badgeAnswer);
        return definitions;
    }

    private static JsonSchema scalar() {
        JsonSchema scalar = JsonSchema.anyOf(Arrays.asList(JsonSchema.ofType("string"), JsonSchema.ofType("number"),
                JsonSchema.ofType("boolean"), JsonSchema.ofType("null")));
        scalar.setDescription("A string, number, boolean or null.");
        return scalar;
    }

    private static JsonSchema tone() {
        JsonSchema tone = described(JsonSchema.ofType(null), "A tone role, never a raw colour.");
        tone.setEnumValues(Arrays.asList("neutral", "accent", "success", "warning", "danger"));
        tone.setFix("set the tone to one of neutral, accent, success, warning or danger; a raw colour is not accepted");
        return tone;
    }

    private static JsonSchema item() {
        JsonSchema state = JsonSchema.ofType(null);
        state.setEnumValues(Arrays.asList("installed", "detected", "available"));
        state.setFix("set the state to one of installed, detected or available");

        JsonSchema values = JsonSchema.ofType("object");
        values.setAdditionalProperties(scalar());

        JsonSchema item = described(JsonSchema.ofType("object"),
                "One row of an items answer. A tree is flat items linked by parent.");
        item.setRequired(Arrays.asList("id"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("id", JsonSchema.ofType("string"));
        properties.put("label", JsonSchema.ofType("string"));
        properties.put("state", state);
        properties.put("parent", JsonSchema.ofType("string"));
        properties.put("values", values);
        properties.put("tone", tone());
        item.setProperties(properties);
        return item;
    }

    private static JsonSchema column() {
        JsonSchema column = JsonSchema.ofType("object");
        column.setRequired(Arrays.asList("key", "label"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("key", JsonSchema.ofType("string"));
        properties.put("label", JsonSchema.ofType("string"));
        properties.put("sortable", JsonSchema.ofType("boolean"));
        column.setProperties(properties);
        return column;
    }

    private static JsonSchema entry() {
        JsonSchema entry = JsonSchema.ofType("object");
        entry.setRequired(Arrays.asList("label", "value"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("label", JsonSchema.ofType("string"));
        properties.put("value", scalar());
        entry.setProperties(properties);
        return entry;
    }

    private static JsonSchema series() {
        JsonSchema points = JsonSchema.ofType("array");
        points.setItems(JsonSchema.ofType("number"));
        JsonSchema series = JsonSchema.ofType("object");
        series.setRequired(Arrays.asList("label", "points"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("label", JsonSchema.ofType("string"));
        properties.put("points", points);
        series.setProperties(properties);
        return series;
    }

    private static JsonSchema itemsAnswer() {
        JsonSchema items = JsonSchema.ofType("array");
        items.setItems(item());
        JsonSchema columns = JsonSchema.ofType("array");
        columns.setItems(column());
        JsonSchema answer = described(JsonSchema.ofType("object"),
                "The items family: list, table and tree. Sorting, filtering and selection belong to the renderer, over items.");
        answer.setRequired(Arrays.asList("items"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("items", items);
        properties.put("columns", columns);
        answer.setProperties(properties);
        return answer;
    }

    private static JsonSchema textAnswer() {
        JsonSchema answer = described(JsonSchema.ofType("object"), "The text family: text, code, log and diff.");
        answer.setRequired(Arrays.asList("text"));
        answer.setProperties(single("text", JsonSchema.ofType("string")));
        return answer;
    }

    private static JsonSchema entriesAnswer() {
        JsonSchema entries = JsonSchema.ofType("array");
        entries.setItems(entry());
        JsonSchema answer = described(JsonSchema.ofType("object"), "The entries family: detail.");
        answer.setRequired(Arrays.asList("entries"));
        answer.setProperties(single("entries", entries));
        return answer;
    }

    private static JsonSchema measureAnswer() {
        JsonSchema answer = described(JsonSchema.ofType("object"),
                "The measure family: progress, and the single-value branch of chart.");
        answer.setRequired(Arrays.asList("value", "maximum"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("value", JsonSchema.ofType("number"));
        properties.put("maximum", JsonSchema.ofType("number"));
        answer.setProperties(properties);
        return answer;
    }

    private static JsonSchema chartAnswer() {
        JsonSchema seriesList = JsonSchema.ofType("array");
        seriesList.setItems(series());
        JsonSchema seriesBranch = JsonSchema.ofType("object");
        seriesBranch.setRequired(Arrays.asList("series"));
        seriesBranch.setProperties(single("series", seriesList));
        JsonSchema answer = JsonSchema.anyOf(Arrays.asList(seriesBranch, measureAnswer()));
        answer.setDescription("The chart family: series of points, or one value against a maximum.");
        return answer;
    }

    private static JsonSchema badgeAnswer() {
        JsonSchema answer = described(JsonSchema.ofType("object"), "The badge family.");
        answer.setRequired(Arrays.asList("text"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("text", JsonSchema.ofType("string"));
        properties.put("tone", tone());
        answer.setProperties(properties);
        return answer;
    }

    private static JsonSchema constant(Object value) {
        JsonSchema schema = JsonSchema.ofType(null);
        schema.setConst(value);
        return schema;
    }

    private static JsonSchema described(JsonSchema schema, String description) {
        schema.setDescription(description);
        return schema;
    }

    private static Map<String, JsonSchema> single(String name, JsonSchema schema) {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put(name, schema);
        return properties;
    }
}
