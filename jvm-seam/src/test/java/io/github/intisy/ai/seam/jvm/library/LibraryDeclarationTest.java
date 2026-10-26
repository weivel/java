package io.github.intisy.ai.seam.jvm.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.intisy.ai.engine.PluginException;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class LibraryDeclarationTest {

    private static final String ECHO_DECLARATION = "{\"$schema\":\"https://weivel.github.io/model/library.schema.json\","
            + "\"id\":\"echo\",\"version\":\"0.1.1\",\"binary\":\"echo\",\"release\":\"echo_release\","
            + "\"services\":[{\"id\":\"echo\",\"functions\":["
            + "{\"name\":\"echo\",\"symbol\":\"echo_echo\",\"kind\":\"json\",\"argument\":{},\"result\":{}},"
            + "{\"name\":\"repeat\",\"symbol\":\"echo_repeat\",\"kind\":\"json\",\"argument\":{\"type\":\"object\"},\"result\":true}]}]}";

    private static PluginException refusalOf(final String text) {
        return assertThrows(PluginException.class, () -> LibraryDeclaration.parse(text));
    }

    @Test
    void readsTheEchoFixturesDeclaration() {
        LibraryDeclaration declaration = LibraryDeclaration.parse(ECHO_DECLARATION);

        assertEquals("echo", declaration.id());
        assertEquals("0.1.1", declaration.version());
        assertEquals("echo", declaration.binary());
        assertEquals("echo_release", declaration.release());
        assertEquals("echo", declaration.services().get(0).id());
        assertEquals("echo_repeat", declaration.services().get(0).functions().get(1).symbol());
        assertEquals("repeat", declaration.services().get(0).functions().get(1).name());
    }

    @Test
    void namesEachSchemaViolationInTheRefusalsDetail() {
        PluginException refusal = refusalOf("{\"id\":\"Echo\",\"version\":\"\",\"binary\":\"echo\",\"release\":\"echo_release\",\"services\":[]}");

        assertTrue(refusal.getDetail().contains("id"), refusal.getDetail());
        assertTrue(refusal.getDetail().contains("version"), refusal.getDetail());
        assertTrue(refusal.getDetail().contains("services"), refusal.getDetail());
        assertEquals("Echo", refusal.getPluginId());
    }

    @Test
    void refusesTextThatIsNotJson() {
        assertTrue(refusalOf("{").getDetail().contains("library.json"));
    }

    @Test
    void refusesTrailingTextAfterTheDeclaration() {
        assertTrue(refusalOf(ECHO_DECLARATION + " extra").getDetail().contains("library.json"));
    }

    @Test
    void refusesAnUnknownKind() {
        PluginException refusal = refusalOf(ECHO_DECLARATION.replace("\"kind\":\"json\",\"argument\":{}", "\"kind\":\"bytes\",\"argument\":{}"));
        assertTrue(refusal.getDetail().contains("/services/0/functions/0/kind"), refusal.getDetail());
        assertEquals("set \"kind\" to \"json\", the only kind there is", refusal.getFix());
    }

    @Test
    void refusesASymbolThatIsNotACIdentifier() {
        PluginException refusal = refusalOf(ECHO_DECLARATION.replace("echo_echo", "echo-echo"));
        assertTrue(refusal.getDetail().contains("/services/0/functions/0/symbol"), refusal.getDetail());
    }

    @Test
    void refusesAFunctionNameThatIsNotACamelCaseIdentifier() {
        PluginException refusal = refusalOf(ECHO_DECLARATION.replace("\"name\":\"repeat\"", "\"name\":\"Repeat\""));
        assertTrue(refusal.getDetail().contains("/services/0/functions/1/name"), refusal.getDetail());
    }

    @Test
    void refusesAnUnknownKeyAtEveryLevel() {
        String text = ECHO_DECLARATION
                .replace("\"binary\":\"echo\"", "\"binary\":\"echo\",\"extra\":1")
                .replace("{\"id\":\"echo\",\"functions\"", "{\"id\":\"echo\",\"extra\":1,\"functions\"")
                .replace("\"result\":true}", "\"result\":true,\"extra\":1}");

        String detail = refusalOf(text).getDetail();

        assertTrue(detail.contains("/extra"), detail);
        assertTrue(detail.contains("/services/0/extra"), detail);
        assertTrue(detail.contains("/services/0/functions/1/extra"), detail);
    }

    @Test
    void refusesAMissingRequiredField() {
        String detail = refusalOf(ECHO_DECLARATION.replace("\"release\":\"echo_release\",", "")).getDetail();
        assertTrue(detail.contains("release"), detail);
    }

    @Test
    void refusesAnArgumentSchemaThatIsNeitherAnObjectNorABoolean() {
        String detail = refusalOf(ECHO_DECLARATION.replace("\"argument\":{},", "\"argument\":3,")).getDetail();
        assertTrue(detail.contains("/services/0/functions/0/argument"), detail);
    }

    @Test
    void refusesAServiceWithNoFunctions() {
        String text = "{\"id\":\"echo\",\"version\":\"1\",\"binary\":\"echo\",\"release\":\"echo_release\",\"services\":[{\"id\":\"echo\",\"functions\":[]}]}";
        String detail = refusalOf(text).getDetail();
        assertTrue(detail.contains("/services/0/functions"), detail);
    }

    private static String echoWithContext(final String context) {
        return ECHO_DECLARATION.replace("\"argument\":{},\"result\":{}}", "\"argument\":{},\"result\":{},\"context\":" + context + "}");
    }

    @Test
    void readsTheContextAFunctionDeclares() {
        LibraryDeclaration declaration = LibraryDeclaration.parse(echoWithContext("[\"environment\",\"home\",\"arguments\"]"));

        assertEquals(Arrays.asList("environment", "home", "arguments"), declaration.services().get(0).functions().get(0).context());
        assertEquals(Collections.<String>emptyList(), declaration.services().get(0).functions().get(1).context());
    }

    @Test
    void refusesAnUnknownContextFact() {
        PluginException refusal = refusalOf(echoWithContext("[\"cwd\"]"));
        assertTrue(refusal.getDetail().contains("/services/0/functions/0/context/0"), refusal.getDetail());
    }

    @Test
    void refusesARepeatedContextFact() {
        PluginException refusal = refusalOf(echoWithContext("[\"home\",\"home\"]"));
        assertTrue(refusal.getDetail().contains("/services/0/functions/0/context"), refusal.getDetail());
        assertTrue(refusal.getDetail().contains("home"), refusal.getDetail());
    }

    @Test
    void refusesAContextThatIsNotAListOfFacts() {
        for (String context : new String[] {"[]", "\"home\"", "[1]"}) {
            PluginException refusal = refusalOf(echoWithContext(context));
            assertTrue(refusal.getDetail().contains("/services/0/functions/0/context"), context + ": " + refusal.getDetail());
        }
    }
}
