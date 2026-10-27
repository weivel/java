package io.github.intisy.ai.seam.jvm.library;

import static io.github.intisy.ai.seam.jvm.library.EchoFixture.ECHO_PIN;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshCache;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshHost;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.pluginSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.intisy.ai.api.AppDescriptor;
import io.github.intisy.ai.api.AppIntegration;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginHost;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DataInterfacesTest {

    interface Address {
        String city();
    }

    interface Person {
        String name();

        Address address();

        List<Address> previous();
    }

    interface Aged {
        String name();

        int age();
    }

    interface PersonEcho {
        Person echo(JsonElement argument);
    }

    interface AgedEcho {
        Aged echo(JsonElement argument);
    }

    interface DescriptorEcho {
        AppDescriptor echo(JsonElement argument);
    }

    interface DescriptorsEcho {
        List<AppDescriptor> echo(JsonElement argument);
    }

    interface PersonWriter {
        JsonElement echo(Person argument);
    }

    interface IntegrationEcho {
        AppIntegration echo(JsonElement argument);
    }

    interface IntegrationWriter {
        JsonElement echo(AppIntegration argument);
    }

    enum Colour {
        RED,
        DARK_BLUE
    }

    interface ColourEcho {
        Colour echo(Colour argument);
    }

    interface ColourReader {
        Colour echo(JsonElement argument);
    }

    interface ColourWriter {
        JsonElement echo(Colour argument);
    }

    private static final String ALPHA = "{\"id\":\"alpha\",\"label\":\"Alpha CLI\","
            + "\"home\":{\"envOverride\":\"HUB_ALPHA_DIR\",\"nativeEnv\":\"ALPHA_CONFIG_DIR\",\"candidates\":[\"~/.alpha\",\"~/.config/alpha\"]},"
            + "\"detect\":{\"binary\":\"alpha\",\"pkg\":\"alpha-cli\"},"
            + "\"loader\":{\"id\":\"alpha-loader\",\"url\":\"acme-org/alpha-loader\"},"
            + "\"commandsSubdir\":\"commands\","
            + "\"paths\":{\"repos\":\"repos\",\"plugin\":\"plugin\",\"cache\":\"cache\",\"config\":\"config\"},"
            + "\"proxyPort\":40001,\"integration\":\"env-baseurl\",\"wireFormat\":\"anthropic\","
            + "\"usage\":{\"formats\":[\"alpha-jsonl\"]}}";

    private static Path cache;
    private static final List<LibraryBridge> OPENED = new ArrayList<LibraryBridge>();

    @BeforeAll
    static void acquireTheEchoLibrary() throws IOException {
        cache = freshCache();
        new LibraryAcquirer().acquire(ECHO_PIN, cache);
    }

    @AfterAll
    static void closeEveryOpenedLibrary() {
        for (LibraryBridge bridge : OPENED) {
            bridge.close();
        }
    }

    private static <T> T echoAs(Class<T> serviceInterface) {
        PluginHost host = freshHost();
        OPENED.add(new LibraryServices(host).load(ECHO_PIN, cache, Collections.<String, Class<?>>singletonMap("echo", serviceInterface)));
        return serviceInterface.cast(pluginSession(host, "consumer", "echo").getServices().get("echo"));
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    @Test
    void buildsADataInterfaceFromAnObject() {
        Person person = echoAs(PersonEcho.class).echo(json(
                "{\"name\":\"Ada\",\"address\":{\"city\":\"London\"},\"previous\":[{\"city\":\"Paris\"},{\"city\":\"Turin\"}]}"));

        assertEquals("Ada", person.name());
        assertEquals("London", person.address().city());
        assertEquals(2, person.previous().size());
        assertEquals("Turin", person.previous().get(1).city());
    }

    @Test
    void answersNullForAnAbsentReferenceProperty() {
        Person person = echoAs(PersonEcho.class).echo(json("{\"name\":\"Ada\"}"));

        assertNull(person.address());
        assertNull(person.previous());
    }

    @Test
    void refusesAnAbsentPrimitivePropertyWhenItIsRead() {
        Aged aged = echoAs(AgedEcho.class).echo(json("{\"name\":\"Ada\"}"));

        assertEquals("Ada", aged.name());
        PluginException refusal = assertThrows(PluginException.class, aged::age);
        assertEquals("echo", refusal.getPluginId());
        assertTrue(refusal.getDetail().contains("echo.echo"), refusal.getDetail());
        assertTrue(refusal.getDetail().contains("age"), refusal.getDetail());
    }

    @Test
    void refusesANullPrimitivePropertyWhenItIsRead() {
        Aged aged = echoAs(AgedEcho.class).echo(json("{\"name\":\"Ada\",\"age\":null}"));

        PluginException refusal = assertThrows(PluginException.class, aged::age);
        assertTrue(refusal.getDetail().contains("age"), refusal.getDetail());
    }

    @Test
    void readsEveryOtherPropertyOfADescriptorWhosePortIsNotANumber() {
        AppDescriptor alpha = echoAs(DescriptorEcho.class).echo(json(ALPHA.replace("40001", "\"x\"")));

        assertEquals("alpha", alpha.id());
        assertEquals(AppIntegration.ENV_BASEURL, alpha.integration());
        PluginException refusal = assertThrows(PluginException.class, alpha::proxyPort);
        assertEquals("echo", refusal.getPluginId());
        assertTrue(refusal.getDetail().contains("echo.echo"), refusal.getDetail());
        assertTrue(refusal.getDetail().contains("proxyPort"), refusal.getDetail());
    }

    @Test
    void answersAListOfDescriptorsWhenOneHoldsAnUnknownIntegration() {
        List<AppDescriptor> apps = echoAs(DescriptorsEcho.class).echo(json("[" + ALPHA + "," + ALPHA.replace("env-baseurl", "Native") + "]"));

        assertEquals(2, apps.size());
        assertEquals(AppIntegration.ENV_BASEURL, apps.get(0).integration());
        assertEquals("alpha", apps.get(1).id());
        assertEquals(40001, apps.get(1).proxyPort());
        PluginException refusal = assertThrows(PluginException.class, () -> apps.get(1).integration());
        assertTrue(refusal.getDetail().contains("Native"), refusal.getDetail());
    }

    @Test
    void convertsEachPropertyOnceAndAnswersTheSameValueAfter() {
        Person person = echoAs(PersonEcho.class).echo(json("{\"name\":\"Ada\",\"address\":{\"city\":\"London\"}}"));

        assertSame(person.address(), person.address());
    }

    @Test
    void refusesABadlyTypedPropertyEachTimeItIsRead() {
        Aged aged = echoAs(AgedEcho.class).echo(json("{\"name\":\"Ada\",\"age\":\"old\"}"));

        assertThrows(PluginException.class, aged::age);
        assertThrows(PluginException.class, aged::age);
    }

    @Test
    void refusesAnAnswerThatIsNotAnObject() {
        PersonEcho echo = echoAs(PersonEcho.class);

        PluginException refusal = assertThrows(PluginException.class, () -> echo.echo(json("[1]")));

        assertTrue(refusal.getDetail().contains("object"), refusal.getDetail());
    }

    @Test
    void comparesTwoDataInterfacesByValue() {
        PersonEcho echo = echoAs(PersonEcho.class);
        Person first = echo.echo(json("{\"name\":\"Ada\",\"address\":{\"city\":\"London\"}}"));
        Person second = echo.echo(json("{\"address\":{\"city\":\"London\"},\"name\":\"Ada\"}"));
        Person other = echo.echo(json("{\"name\":\"Grace\"}"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, other);
        assertEquals(first.address(), second.address());
        assertTrue(first.toString().contains("London"), first.toString());
    }

    @Test
    void buildsTheContractsAppDescriptor() {
        AppDescriptor alpha = echoAs(DescriptorEcho.class).echo(json(ALPHA));

        assertEquals("alpha", alpha.id());
        assertEquals(Arrays.asList("~/.alpha", "~/.config/alpha"), alpha.home().candidates());
        assertEquals("repos", alpha.paths().repos());
        assertEquals(40001, alpha.proxyPort());
        assertEquals(AppIntegration.ENV_BASEURL, alpha.integration());
        assertEquals("alpha-cli", alpha.detect().pkg());
        assertEquals(Collections.singletonList("alpha-jsonl"), alpha.usage().formats());
        assertNull(alpha.icon());
        assertNull(alpha.startupHook());
    }

    @Test
    void refusesAnIntegrationNoConstantCarriesWhenItIsRead() {
        AppDescriptor alpha = echoAs(DescriptorEcho.class).echo(json(ALPHA.replace("env-baseurl", "smoke-signal")));

        assertEquals("Alpha CLI", alpha.label());
        PluginException refusal = assertThrows(PluginException.class, alpha::integration);
        assertTrue(refusal.getDetail().contains("smoke-signal"), refusal.getDetail());
    }

    @Test
    void comparesDescriptorsByTheirJsonWithoutReadingABadlyTypedProperty() {
        DescriptorEcho echo = echoAs(DescriptorEcho.class);
        String unknown = ALPHA.replace("env-baseurl", "smoke-signal");

        assertEquals(echo.echo(json(unknown)), echo.echo(json(unknown)));
        assertEquals(echo.echo(json(unknown)).hashCode(), echo.echo(json(unknown)).hashCode());
    }

    @Test
    void answersListsThatCannotBeChanged() {
        Person person = echoAs(PersonEcho.class).echo(json("{\"name\":\"Ada\",\"previous\":[{\"city\":\"Paris\"}]}"));

        assertThrows(UnsupportedOperationException.class, () -> person.previous().clear());
        assertEquals(1, person.previous().size());
    }

    @Test
    void writesAReceivedDataValueAsTheJsonItWasReadFrom() {
        String ada = "{\"name\":\"Ada\",\"address\":{\"city\":\"London\"},\"extra\":[1]}";
        Person person = echoAs(PersonEcho.class).echo(json(ada));

        assertEquals(json(ada), echoAs(PersonWriter.class).echo(person));
    }

    @Test
    void readsAndWritesAnEnumByItsTsLiteral() {
        assertEquals(AppIntegration.ENV_BASEURL, echoAs(IntegrationEcho.class).echo(json("\"env-baseurl\"")));
        assertEquals(json("\"env-baseurl\""), echoAs(IntegrationWriter.class).echo(AppIntegration.ENV_BASEURL));
        assertEquals(json("\"native\""), echoAs(IntegrationWriter.class).echo(AppIntegration.NATIVE));
    }

    @Test
    void refusesAnEnumConstantsNameWhenItCarriesATsLiteral() {
        IntegrationEcho echo = echoAs(IntegrationEcho.class);

        PluginException refusal = assertThrows(PluginException.class, () -> echo.echo(json("\"ENV_BASEURL\"")));

        assertTrue(refusal.getDetail().contains("ENV_BASEURL"), refusal.getDetail());
    }

    @Test
    void roundTripsAnEnumWithoutATsLiteralByItsName() {
        assertEquals(json("\"DARK_BLUE\""), echoAs(ColourWriter.class).echo(Colour.DARK_BLUE));
        assertEquals(Colour.DARK_BLUE, echoAs(ColourEcho.class).echo(Colour.DARK_BLUE));
        ColourReader reader = echoAs(ColourReader.class);
        assertThrows(PluginException.class, () -> reader.echo(json("\"dark-blue\"")));
    }
}
