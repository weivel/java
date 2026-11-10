package io.github.intisy.ai.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DescriptionRulesTest {

    private static final String CALL = "{\"service\": \"demo:screens\", \"method\": \"refresh\"}";

    private static Object manifestWith(String members) {
        return new TestJson("{\"importName\": \"demo\", \"api\": 2, \"services\": {\"provides\": [\"demo:screens\"]}"
                + (members.isEmpty() ? "" : ", " + members) + "}").read();
    }

    private static String viewOf(String... nodes) {
        return "\"views\": {\"main\": {\"title\": \"Main\", \"nodes\": [" + String.join(", ", nodes) + "]}}";
    }

    private static List<String> pathsOf(List<SchemaIssue> issues) {
        List<String> paths = new ArrayList<String>();
        for (SchemaIssue issue : issues) {
            paths.add(issue.getPath());
        }
        return paths;
    }

    private static List<String> fixesOf(List<SchemaIssue> issues) {
        List<String> fixes = new ArrayList<String>();
        for (SchemaIssue issue : issues) {
            fixes.add(issue.getFix());
        }
        return fixes;
    }

    private static List<String> validatedPaths(Object manifest) {
        return pathsOf(ManifestValidator.validate(manifest, Collections.<String>emptyList()));
    }

    @Test
    void acceptsADescriptionWhoseReferencesAllResolve() {
        Object manifest = manifestWith("\"settings\": {\"retryLimit\": {\"type\": \"number\", \"label\": \"Retries\"}}, "
                + "\"actions\": {\"refresh\": {\"label\": \"Refresh\", \"call\": " + CALL + "}}, "
                + viewOf("{\"id\": \"root\", \"component\": \"stack\"}",
                        "{\"id\": \"go\", \"parent\": \"root\", \"component\": \"button\", \"action\": \"refresh\", \"bind\": " + CALL + "}",
                        "{\"id\": \"limit\", \"parent\": \"root\", \"component\": \"field\", \"field\": \"retryLimit\"}"));
        assertEquals(Collections.emptyList(), DescriptionRules.issues(manifest));
        assertEquals(Collections.emptyList(), validatedPaths(manifest));
    }

    @Test
    void reportsEachRepeatOfANodeIdAtItsOwnPath() {
        List<SchemaIssue> issues = DescriptionRules.issues(manifestWith(viewOf(
                "{\"id\": \"a\", \"component\": \"stack\"}",
                "{\"id\": \"a\", \"component\": \"text\"}",
                "{\"id\": \"a\", \"component\": \"text\"}")));
        assertEquals(Arrays.asList("views.main.nodes[1].id", "views.main.nodes[2].id"), pathsOf(issues));
        assertEquals(Arrays.asList("give each node in a view its own id", "give each node in a view its own id"), fixesOf(issues));
    }

    @Test
    void refusesANodeThatIsItsOwnParent() {
        List<SchemaIssue> issues = DescriptionRules.issues(manifestWith(viewOf("{\"id\": \"a\", \"parent\": \"a\", \"component\": \"stack\"}")));
        assertEquals(Arrays.asList("views.main.nodes[0].parent"), pathsOf(issues));
        assertEquals(Arrays.asList("name the id of a node listed before this one"), fixesOf(issues));
    }

    @Test
    void refusesAParentListedAfterItsChild() {
        Object manifest = manifestWith(viewOf(
                "{\"id\": \"a\", \"parent\": \"b\", \"component\": \"stack\"}",
                "{\"id\": \"b\", \"component\": \"stack\"}"));
        assertEquals(Arrays.asList("views.main.nodes[0].parent"), validatedPaths(manifest));
    }

    @Test
    void resolvesAParentOnAnyEarlierNodeCarryingThatId() {
        Object manifest = manifestWith(viewOf(
                "{\"id\": \"a\", \"component\": \"stack\"}",
                "{\"id\": \"a\", \"component\": \"stack\"}",
                "{\"id\": \"c\", \"parent\": \"a\", \"component\": \"text\"}"));
        assertEquals(Arrays.asList("views.main.nodes[1].id"), validatedPaths(manifest));
    }

    @Test
    void refusesAnUnknownActionWithTheFix() {
        List<SchemaIssue> issues = DescriptionRules.issues(manifestWith(viewOf("{\"id\": \"a\", \"component\": \"button\", \"action\": \"missing\"}")));
        assertEquals(Arrays.asList("views.main.nodes[0].action"), pathsOf(issues));
        assertEquals(Arrays.asList("name one of this manifest's actions"), fixesOf(issues));
    }

    @Test
    void refusesAFieldWithNoSettingsAndDoesNotThrow() {
        List<SchemaIssue> issues = DescriptionRules.issues(manifestWith(viewOf("{\"id\": \"a\", \"component\": \"field\", \"field\": \"retryLimit\"}")));
        assertEquals(Arrays.asList("views.main.nodes[0].field"), pathsOf(issues));
        assertEquals(Arrays.asList("name one of this manifest's settings"), fixesOf(issues));
    }

    @Test
    void refusesABindingToAServiceThePluginDoesNotProvideInAnActionAndANode() {
        Object manifest = new TestJson("{\"importName\": \"demo\", \"api\": 2, \"services\": {\"consumes\": [\"demo:other\"]}, "
                + "\"actions\": {\"refresh\": {\"label\": \"Refresh\", \"call\": {\"service\": \"demo:other\", \"method\": \"refresh\"}}}, "
                + viewOf("{\"id\": \"a\", \"component\": \"list\", \"bind\": {\"service\": \"demo:other\", \"method\": \"read\"}}") + "}").read();
        List<SchemaIssue> issues = DescriptionRules.issues(manifest);
        assertEquals(Arrays.asList("actions.refresh.call.service", "views.main.nodes[0].bind.service"), pathsOf(issues));
        assertEquals(Arrays.asList("bind to a service this plugin provides", "bind to a service this plugin provides"), fixesOf(issues));
    }

    @Test
    void refusesEveryBindingWhenNoServicesAreDeclared() {
        Object manifest = new TestJson("{\"importName\": \"demo\", \"api\": 2, "
                + "\"actions\": {\"refresh\": {\"label\": \"Refresh\", \"call\": " + CALL + "}}}").read();
        assertEquals(Arrays.asList("actions.refresh.call.service"), pathsOf(DescriptionRules.issues(manifest)));
    }

    @Test
    void countsAProvidedServiceWrittenInItsObjectForm() {
        Object manifest = new TestJson("{\"importName\": \"demo\", \"api\": 2, \"services\": {\"provides\": [{\"id\": \"demo:screens\"}]}, "
                + "\"actions\": {\"refresh\": {\"label\": \"Refresh\", \"call\": " + CALL + "}}}").read();
        assertEquals(Collections.emptyList(), DescriptionRules.issues(manifest));
    }

    @Test
    void ordersActionsThenViewsByKeyThenNodesByIndexThenIdParentActionFieldBind() {
        String stranger = "{\"service\": \"x:y\", \"method\": \"m\"}";
        Object manifest = manifestWith("\"actions\": {\"zeta\": {\"label\": \"Z\", \"call\": " + stranger + "}}, "
                + "\"views\": {"
                + "\"second\": {\"title\": \"S\", \"nodes\": [{\"id\": \"n\", \"component\": \"text\", \"action\": \"q\"}]}, "
                + "\"first\": {\"title\": \"F\", \"nodes\": [{\"id\": \"a\", \"component\": \"stack\"}, "
                + "{\"id\": \"a\", \"parent\": \"nope\", \"component\": \"field\", \"action\": \"q\", \"field\": \"f\", \"bind\": " + stranger + "}]}}");
        assertEquals(Arrays.asList(
                "actions.zeta.call.service",
                "views.second.nodes[0].action",
                "views.first.nodes[1].id",
                "views.first.nodes[1].parent",
                "views.first.nodes[1].action",
                "views.first.nodes[1].field",
                "views.first.nodes[1].bind.service"), validatedPaths(manifest));
    }

    @Test
    void refusesAChoiceSettingWithNoChoices() {
        Object empty = manifestWith("\"settings\": {\"mode\": {\"type\": \"choice\", \"label\": \"Mode\", \"choices\": []}}");
        assertEquals(Arrays.asList("settings.mode.choices"), validatedPaths(empty));
        Object absent = manifestWith("\"settings\": {\"mode\": {\"type\": \"multi-choice\", \"label\": \"Mode\"}}");
        assertEquals(Arrays.asList("settings.mode.choices"), validatedPaths(absent));
        Object text = manifestWith("\"settings\": {\"mode\": {\"type\": \"text\", \"label\": \"Mode\"}}");
        assertEquals(Collections.emptyList(), validatedPaths(text));
    }

    @Test
    void refusesASettingKeyOrViewIdEndingInANewline() {
        Object setting = manifestWith("\"settings\": {\"name\\n\": {\"type\": \"text\", \"label\": \"Name\"}}");
        assertEquals(Arrays.asList("settings"), validatedPaths(setting));
        Object view = manifestWith("\"views\": {\"v\\n\": {\"title\": \"V\", \"nodes\": [{\"id\": \"a\", \"component\": \"text\"}]}}");
        assertEquals(Arrays.asList("views"), validatedPaths(view));
        Object input = manifestWith("\"actions\": {\"go\": {\"label\": \"Go\", \"call\": " + CALL + ", "
                + "\"input\": {\"Bad-Key\": {\"type\": \"text\", \"label\": \"B\"}}}}");
        assertEquals(Arrays.asList("actions.go.input"), validatedPaths(input));
    }

    @Test
    void ignoresAnUnknownNodePropertyCarryingARawValue() {
        Object manifest = manifestWith(viewOf("{\"id\": \"a\", \"component\": \"text\", \"color\": \"#f00\"}"));
        assertTrue(validatedPaths(manifest).isEmpty());
    }
}
