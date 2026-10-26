package io.github.intisy.ai.seam.jvm.library;

import static io.github.intisy.ai.seam.jvm.library.EchoFixture.ECHO_PIN;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshCache;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshHost;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.pluginSession;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.stageRelease;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginHost;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LibraryContextTest {

    /** The test-only function {@code contextual}, which the declaration maps onto echo's {@code echo_echo}. */
    interface ContextualEcho {
        JsonElement contextual(JsonElement argument);
    }

    private static Path binaryPath;
    private static String declarationText;
    private static final List<LibraryBridge> OPENED = new ArrayList<LibraryBridge>();

    @BeforeAll
    static void acquireTheEchoLibrary() throws IOException {
        LibraryAcquirer.AcquiredLibrary acquired = new LibraryAcquirer().acquire(ECHO_PIN, freshCache());
        binaryPath = acquired.binaryPath();
        declarationText = new String(Files.readAllBytes(binaryPath.resolveSibling("library.json")), StandardCharsets.UTF_8);
    }

    @AfterAll
    static void closeEveryOpenedLibrary() {
        for (LibraryBridge bridge : OPENED) {
            bridge.close();
        }
    }

    private static LibraryContext facts() {
        return new LibraryContext(Collections.singletonMap("X", "1"), "/h", Arrays.asList("node", "x"));
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static String declaringContext(String... context) {
        JsonObject declaration = json(declarationText).getAsJsonObject();
        JsonArray functions = declaration.getAsJsonArray("services").get(0).getAsJsonObject().getAsJsonArray("functions");
        JsonObject contextual = functions.get(0).getAsJsonObject().deepCopy();
        contextual.addProperty("name", "contextual");
        contextual.addProperty("symbol", "echo_echo");
        JsonArray facts = new JsonArray();
        for (String fact : context) {
            facts.add(fact);
        }
        contextual.add("context", facts);
        functions.add(contextual);
        return declaration.toString();
    }

    private static LibraryBridge open(Supplier<LibraryContext> provider, String... context) {
        LibraryDeclaration declaration = LibraryDeclaration.parse(context.length == 0 ? declarationText : declaringContext(context));
        LibraryBridge bridge = provider == null
                ? LibraryBridge.open(binaryPath, declaration)
                : LibraryBridge.open(binaryPath, declaration, provider);
        OPENED.add(bridge);
        return bridge;
    }

    private static JsonElement call(LibraryBridge bridge, String argument) {
        return json(bridge.call("echo_echo", argument));
    }

    private static JsonElement envelope(String argument, String context) {
        return json("{\"argument\":" + argument + ",\"context\":" + context + "}");
    }

    private static PluginException refusalOf(LibraryBridge bridge) {
        return assertThrows(PluginException.class, () -> bridge.call("echo_echo", "{}"));
    }

    @Test
    void wrapsAContextualCallInTheArgumentAndTheDeclaredFacts() {
        LibraryBridge bridge = open(LibraryContextTest::facts, "environment", "home", "arguments");

        assertEquals(envelope("{\"a\":1}", "{\"environment\":{\"X\":\"1\"},\"home\":\"/h\",\"arguments\":[\"node\",\"x\"]}"),
                call(bridge, "{\"a\":1}"));
    }

    @Test
    void sendsOnlyTheFactsAFunctionDeclares() {
        LibraryBridge bridge = open(LibraryContextTest::facts, "home");

        assertEquals(envelope("{\"a\":1}", "{\"home\":\"/h\"}"), call(bridge, "{\"a\":1}"));
    }

    @Test
    void leavesAFunctionWithoutContextUnwrapped() {
        LibraryBridge bridge = open(LibraryContextTest::facts);

        assertEquals(json("{\"a\":1}"), call(bridge, "{\"a\":1}"));
    }

    @Test
    void gathersTheFactsAtEachCall() {
        final AtomicReference<String> value = new AtomicReference<String>("1");
        LibraryBridge bridge = open(() -> new LibraryContext(Collections.singletonMap("X", value.get()), "/h",
                Collections.<String>emptyList()), "environment");

        JsonElement first = call(bridge, "{}");
        value.set("2");
        JsonElement second = call(bridge, "{}");

        assertEquals(envelope("{}", "{\"environment\":{\"X\":\"1\"}}"), first);
        assertEquals(envelope("{}", "{\"environment\":{\"X\":\"2\"}}"), second);
    }

    @Test
    void defaultsToTheProcesssOwnFacts() {
        LibraryBridge bridge = open(null, "environment", "home", "arguments");

        JsonObject context = call(bridge, null).getAsJsonObject().getAsJsonObject("context");

        assertEquals(System.getProperty("user.home"), context.get("home").getAsString());
        assertEquals(new JsonArray(), context.get("arguments"));
        Map<String, String> environment = new HashMap<String, String>();
        for (Map.Entry<String, JsonElement> variable : context.getAsJsonObject("environment").entrySet()) {
            environment.put(variable.getKey(), variable.getValue().getAsString());
        }
        assertEquals(System.getenv(), environment);
    }

    @Test
    void takesTheArgumentsAnEmbedderHandsTheProcessContext() {
        LibraryContext context = LibraryContext.ofProcess(Arrays.asList("java", "-jar", "host.jar"));

        assertEquals(Arrays.asList("java", "-jar", "host.jar"), context.arguments());
        assertEquals(System.getProperty("user.home"), context.home());
        assertEquals(System.getenv(), context.environment());
    }

    @Test
    void refusesACallNamingTheLibraryAndFunctionWhenTheProviderThrows() {
        LibraryBridge bridge = open(() -> {
            throw new IllegalStateException("no home today");
        }, "home");

        PluginException refusal = refusalOf(bridge);

        assertEquals("echo", refusal.getPluginId());
        assertTrue(refusal.getDetail().contains("echo.contextual"), refusal.getDetail());
        assertTrue(refusal.getDetail().contains("no home today"), refusal.getDetail());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void refusesAContextWhoseFactIsMalformedNamingTheFact() {
        Map rawEnvironment = new HashMap();
        rawEnvironment.put("X", 1);
        Map rawKeyed = new HashMap();
        rawKeyed.put(1, "X");
        Map<String, String> nullValued = new HashMap<String, String>();
        nullValued.put("X", null);
        List rawArguments = new ArrayList(Arrays.asList("java", 2));
        Map<String, LibraryContext> malformed = new LinkedHashMap<String, LibraryContext>();
        malformed.put("environment holding a number", new LibraryContext(rawEnvironment, "/h", Collections.<String>emptyList()));
        malformed.put("environment keyed by a number", new LibraryContext(rawKeyed, "/h", Collections.<String>emptyList()));
        malformed.put("environment holding null", new LibraryContext(nullValued, "/h", Collections.<String>emptyList()));
        malformed.put("environment null", new LibraryContext(null, "/h", Collections.<String>emptyList()));
        malformed.put("home null", new LibraryContext(Collections.<String, String>emptyMap(), null, Collections.<String>emptyList()));
        malformed.put("arguments holding a number", new LibraryContext(Collections.<String, String>emptyMap(), "/h", rawArguments));
        malformed.put("arguments holding null", new LibraryContext(Collections.<String, String>emptyMap(), "/h", Arrays.asList("java", null)));
        malformed.put("arguments null", new LibraryContext(Collections.<String, String>emptyMap(), "/h", null));
        for (Map.Entry<String, LibraryContext> context : malformed.entrySet()) {
            String fact = context.getKey().split(" ")[0];
            final LibraryContext given = context.getValue();
            LibraryBridge bridge = open(() -> given, fact);

            PluginException refusal = refusalOf(bridge);

            assertTrue(refusal.getDetail().contains(fact + " is malformed"), context.getKey() + ": " + refusal.getDetail());
            assertTrue(refusal.getDetail().contains("echo.contextual"), refusal.getDetail());
        }
    }

    @Test
    void refusesAProviderThatAnswersNoContext() {
        LibraryBridge bridge = open(() -> null, "home");

        PluginException refusal = refusalOf(bridge);

        assertTrue(refusal.getDetail().contains("echo.contextual"), refusal.getDetail());
        assertTrue(refusal.getDetail().contains("is malformed"), refusal.getDetail());
    }

    @Test
    void doesNotGatherFactsForAFunctionThatDeclaresNone() {
        final AtomicInteger gathered = new AtomicInteger();
        LibraryBridge bridge = open(() -> {
            gathered.incrementAndGet();
            throw new IllegalStateException("must not be called");
        });

        assertEquals(json("{\"a\":1}"), call(bridge, "{\"a\":1}"));
        assertEquals(0, gathered.get());
    }

    @Test
    void gathersTheFactsOncePerContextualCall() {
        final AtomicInteger gathered = new AtomicInteger();
        LibraryBridge bridge = open(() -> {
            gathered.incrementAndGet();
            return facts();
        }, "environment", "home");

        call(bridge, "{}");
        call(bridge, "{}");

        assertEquals(2, gathered.get());
    }

    @Test
    void reachesALibraryThroughTheContextGivenToItsServices() throws IOException {
        Path cache = freshCache();
        LibraryPin pin = stageRelease(cache, binaryPath, declaringContext("home").getBytes(StandardCharsets.UTF_8));
        PluginHost host = freshHost();
        Map<String, Class<?>> interfaces = Collections.<String, Class<?>>singletonMap("echo", ContextualEcho.class);

        LibraryBridge bridge = new LibraryServices(host, LibraryContextTest::facts).load(pin, cache, interfaces);
        OPENED.add(bridge);
        ContextualEcho echo = (ContextualEcho) pluginSession(host, "consumer").getServices().get("echo");

        assertEquals(envelope("{\"a\":1}", "{\"home\":\"/h\"}"), echo.contextual(json("{\"a\":1}")));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> RuntimeException sneakyThrow(Exception checked) throws E {
        throw (E) checked;
    }

    @Test
    void refusesACallWhenTheProviderThrowsACheckedException() {
        LibraryBridge bridge = open(() -> {
            throw LibraryContextTest.<RuntimeException>sneakyThrow(new IOException("home unreadable"));
        }, "home");

        PluginException refusal = refusalOf(bridge);

        assertTrue(refusal.getDetail().contains("echo.contextual"), refusal.getDetail());
        assertTrue(refusal.getDetail().contains("home unreadable"), refusal.getDetail());
    }
}
