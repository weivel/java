package io.github.intisy.ai.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerValidatorTest {

    private static final String UI_DATA = "https://weivel.github.io/model/ui-data.schema.json";

    private static final String INSTALL_METHOD = "https://weivel.github.io/model/install-method.schema.json";

    private static List<String> pathsOf(String schemaId, String definition, String valueJson) {
        Object value = new TestJson(valueJson).read();
        List<String> paths = new ArrayList<String>();
        for (SchemaIssue issue : AnswerValidator.validate(schemaId, definition, value)) {
            paths.add(issue.getPath());
        }
        return paths;
    }

    @Test
    void acceptsADetectAnswer() {
        assertEquals(Collections.emptyList(),
                pathsOf(INSTALL_METHOD, "detectAnswer", "{\"installed\":true,\"version\":\"1.2.3\"}"));
    }

    @Test
    void refusesADetectAnswerWithoutInstalled() {
        assertEquals(Arrays.asList("installed"), pathsOf(INSTALL_METHOD, "detectAnswer", "{\"version\":\"1.2.3\"}"));
    }

    @Test
    void acceptsASuccessAndARefusalAnswer() {
        assertEquals(Collections.emptyList(), pathsOf(INSTALL_METHOD, "operationAnswer", "{\"ok\":true}"));
        assertEquals(Collections.emptyList(),
                pathsOf(INSTALL_METHOD, "operationAnswer", "{\"ok\":false,\"detail\":\"x\",\"fix\":\"y\"}"));
    }

    @Test
    void refusesARefusalWithoutAFixAtBothBranches() {
        assertEquals(Arrays.asList("ok", "fix"),
                pathsOf(INSTALL_METHOD, "operationAnswer", "{\"ok\":false,\"detail\":\"x\"}"));
    }

    @Test
    void acceptsATableAndASeriesAndAMeterChart() {
        assertEquals(Collections.emptyList(),
                pathsOf(UI_DATA, "table", "{\"items\":[{\"id\":\"a\",\"label\":\"A\"}]}"));
        assertEquals(Collections.emptyList(),
                pathsOf(UI_DATA, "chart", "{\"series\":[{\"label\":\"a\",\"points\":[1,2]}]}"));
        assertEquals(Collections.emptyList(), pathsOf(UI_DATA, "chart", "{\"value\":3,\"maximum\":10}"));
    }

    @Test
    void acceptsARefusalAnswerForAScalarValuesRow() {
        assertEquals(Collections.emptyList(),
                pathsOf(UI_DATA, "table", "{\"items\":[{\"id\":\"a\",\"values\":{\"n\":1,\"s\":\"x\",\"b\":true,\"z\":null}}]}"));
    }

    @Test
    void refusesARawColourInABadge() {
        assertEquals(Arrays.asList("tone"), pathsOf(UI_DATA, "badge", "{\"text\":\"x\",\"tone\":\"#f00\"}"));
    }

    @Test
    void refusesAnItemWithNoId() {
        assertEquals(Arrays.asList("items[0].id"), pathsOf(UI_DATA, "table", "{\"items\":[{\"label\":\"A\"}]}"));
    }

    @Test
    void refusesAnEmptyChartAtTheMeasureFieldsOnly() {
        assertEquals(Arrays.asList("value", "maximum"), pathsOf(UI_DATA, "chart", "{}"));
    }

    @Test
    void refusesAScalarThatIsAnObject() {
        assertEquals(Arrays.asList("items[0].values.n"),
                pathsOf(UI_DATA, "list", "{\"items\":[{\"id\":\"a\",\"values\":{\"n\":{}}}]}"));
    }

    @Test
    void refusesADefinitionThatIsNotAnAnswer() {
        assertThrows(IllegalArgumentException.class, () -> AnswerValidator.validate(UI_DATA, "tone", "neutral"));
        assertThrows(IllegalArgumentException.class, () -> AnswerValidator.validate(UI_DATA, "nothing", "x"));
        assertThrows(IllegalArgumentException.class, () -> AnswerValidator.validate("https://x/y.json", "badge", "x"));
        assertTrue(AnswerValidator.validate(UI_DATA, "badge", new TestJson("{\"text\":\"x\"}").read()).isEmpty());
    }
}
