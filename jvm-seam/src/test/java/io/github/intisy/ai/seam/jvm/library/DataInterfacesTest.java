package io.github.intisy.ai.seam.jvm.library;

import static io.github.intisy.ai.seam.jvm.library.EchoFixture.ECHO_PIN;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshCache;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshHost;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.pluginSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        return serviceInterface.cast(pluginSession(host, "consumer").getServices().get("echo"));
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
    void refusesAnAbsentPrimitiveProperty() {
        AgedEcho echo = echoAs(AgedEcho.class);

        PluginException refusal = assertThrows(PluginException.class, () -> echo.echo(json("{\"name\":\"Ada\"}")));

        assertEquals("echo", refusal.getPluginId());
        assertTrue(refusal.getDetail().contains("age"), refusal.getDetail());
    }

    @Test
    void refusesANullPrimitiveProperty() {
        AgedEcho echo = echoAs(AgedEcho.class);

        PluginException refusal = assertThrows(PluginException.class, () -> echo.echo(json("{\"name\":\"Ada\",\"age\":null}")));

        assertTrue(refusal.getDetail().contains("age"), refusal.getDetail());
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
    void refusesAnIntegrationNoConstantCarries() {
        DescriptorEcho echo = echoAs(DescriptorEcho.class);

        PluginException refusal = assertThrows(PluginException.class, () -> echo.echo(json(ALPHA.replace("env-baseurl", "smoke-signal"))));

        assertTrue(refusal.getDetail().contains("smoke-signal"), refusal.getDetail());
    }
}
