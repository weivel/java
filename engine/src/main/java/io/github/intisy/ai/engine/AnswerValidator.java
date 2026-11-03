package io.github.intisy.ai.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks what a bound call or an install method answered against the model's definition for it.
 *
 * @implNote Paths are rooted at the answer, as in {@code items[0].id}, and are the ones the
 * TypeScript engine reports, because the conformance kit compares them.
 */
public final class AnswerValidator {

    private static final String CHART = "chart";

    private AnswerValidator() {
    }

    /**
     * @param schemaId {@link AnswerSchemas#UI_DATA} or {@link AnswerSchemas#INSTALL_METHOD}
     * @param definition the definition the answer is checked against, for example {@code table}
     * @param value the parsed answer
     * @return every issue found, or empty when the answer is valid
     * @throws IllegalArgumentException when the schema or definition is not one an answer is checked against
     */
    public static List<SchemaIssue> validate(String schemaId, String definition, Object value) {
        if (!AnswerSchemas.answerable(schemaId).contains(definition)) {
            throw new IllegalArgumentException(definition + " is not a definition of " + schemaId);
        }
        JsonSchema schema = AnswerSchemas.definitions(schemaId).get(definition);
        List<SchemaIssue> issues = SchemaValidator.validate(value, schema, null, null);
        if (issues.isEmpty() || schema.getAnyOf() == null || !(value instanceof Map)) {
            return issues;
        }
        return CHART.equals(definition) ? chosenBranchIssues(schema, (Map<?, ?>) value) : everyBranchIssues(schema, value);
    }

    /**
     * @implNote A chart answers with series or with a measure, and the union would report only that
     * neither matched. The answer's own {@code series} key picks the branch, as the renderer does,
     * so the issues name the field that is wrong in the branch the author meant.
     */
    private static List<SchemaIssue> chosenBranchIssues(JsonSchema chart, Map<?, ?> value) {
        JsonSchema chosen = chart.getAnyOf().get(value.containsKey("series") ? 0 : 1);
        return SchemaValidator.validate(value, chosen, null, null);
    }

    /**
     * @implNote Every branch's issues are reported once per path, in branch order, because an answer
     * that fits no branch has no single branch to blame: a refusal without a {@code fix} fails the
     * success branch at {@code ok} and the refusal branch at {@code fix}, and both are true.
     */
    private static List<SchemaIssue> everyBranchIssues(JsonSchema union, Object value) {
        List<SchemaIssue> issues = new ArrayList<SchemaIssue>();
        Set<String> reported = new HashSet<String>();
        for (JsonSchema branch : union.getAnyOf()) {
            for (SchemaIssue issue : SchemaValidator.validate(value, branch, null, null)) {
                if (reported.add(issue.getPath())) {
                    issues.add(issue);
                }
            }
        }
        return issues;
    }
}
