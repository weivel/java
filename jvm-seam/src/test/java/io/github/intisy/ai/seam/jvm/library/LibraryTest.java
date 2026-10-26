package io.github.intisy.ai.seam.jvm.library;

import static io.github.intisy.ai.seam.jvm.library.EchoFixture.ECHO_PIN;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshCache;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.freshHost;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.loadInto;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.pluginSession;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.sha256Of;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.stageRelease;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.withByteOrderMark;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.withMirrorService;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.withDigest;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.withOneDigitChanged;
import static io.github.intisy.ai.seam.jvm.library.EchoFixture.withoutDigest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginHost;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class LibraryTest {

    private static EchoService echo;
    private static LibraryBridge sharedBridge;
    private static Path binaryPath;
    private static LibraryDeclaration declaration;

    @BeforeAll
    static void startAHostOfferingTheEchoLibrary() throws IOException {
        Path cache = freshCache();
        PluginHost host = freshHost();
        sharedBridge = loadInto(new LibraryServices(host), ECHO_PIN, cache);
        echo = (EchoService) pluginSession(host, "consumer").getServices().get("echo");
        LibraryAcquirer.AcquiredLibrary acquired = new LibraryAcquirer().acquire(ECHO_PIN, cache);
        binaryPath = acquired.binaryPath();
        declaration = acquired.declaration();
    }

    @AfterAll
    static void closeTheSharedLibrary() {
        sharedBridge.close();
    }

    private static PluginException refusalOf(Executable work) {
        return assertThrows(PluginException.class, work);
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static List<String> filesUnder(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).collect(Collectors.toList());
        }
    }

    private static boolean isBinaryOrTemporary(String fileName) {
        return fileName.endsWith(".dll") || fileName.endsWith(".so") || fileName.endsWith(".dylib") || fileName.endsWith(".tmp");
    }

    private static boolean isBinaryUrl(String url) {
        return url.endsWith(".dll") || url.endsWith(".so");
    }

    @Test
    void offersEachDeclaredFunctionAsAMethodOfItsService() {
        assertEquals(json("{\"a\":[1,2]}"), echo.echo(json("{\"a\":[1,2]}")));
    }

    @Test
    void raisesALibraryFailureAsARefusalCarryingItsFix() {
        PluginException refusal = refusalOf(() -> echo.fail(new JsonObject()));
        assertEquals("call echo instead", refusal.getFix());
        assertTrue(refusal.getDetail().contains("echo refused on purpose"), refusal.getDetail());
    }

    @Test
    void returnsAOneMebibyteResultIntact() {
        assertEquals(1048576, echo.repeat(new EchoService.RepeatArgument(1048576)).length());
    }

    @Test
    void refusesInvalidUtf8FromALibraryAsARefusalNotAParserError() {
        PluginException refusal = refusalOf(() -> echo.garbage(new JsonObject()));
        assertEquals("echo", refusal.getPluginId());
        assertTrue(refusal.getDetail().contains("garbage"), refusal.getDetail());
        assertTrue(refusal.getDetail().contains("not UTF-8"), refusal.getDetail());
    }

    @Test
    void refusesABinaryWhoseDigestDiffersFromItsPin() throws IOException {
        Path cache = freshCache();
        String platformKey = LibraryAcquirer.currentPlatformKey();
        LibraryPin pin = withDigest(ECHO_PIN, platformKey, withOneDigitChanged(ECHO_PIN.sha256().get(platformKey)));

        PluginException refusal = refusalOf(() -> new LibraryAcquirer().acquire(pin, cache));

        assertTrue(refusal.getDetail().contains("SHA-256"), refusal.getDetail());
        assertEquals(Collections.emptyList(), filesUnder(cache).stream().filter(LibraryTest::isBinaryOrTemporary).collect(Collectors.toList()));
    }

    @Test
    void reVerifiesACachedFileInsteadOfTrustingIt() throws IOException {
        Path cache = freshCache();
        LibraryAcquirer.AcquiredLibrary first = new LibraryAcquirer().acquire(ECHO_PIN, cache);
        Files.write(first.binaryPath(), "not the library".getBytes(StandardCharsets.UTF_8));
        final List<String> urls = new ArrayList<String>();
        final LibraryAcquirer.Download network = LibraryAcquirer.overHttps();
        LibraryAcquirer counting = new LibraryAcquirer(LibraryAcquirer.currentPlatformKey(), url -> {
            urls.add(url);
            return network.fetch(url);
        }, LibraryAcquirer.atomicMove());

        LibraryAcquirer.AcquiredLibrary second = counting.acquire(ECHO_PIN, cache);
        LibraryBridge bridge = LibraryBridge.open(second.binaryPath(), second.declaration());

        try {
            assertEquals(1, urls.stream().filter(LibraryTest::isBinaryUrl).count(), urls.toString());
            assertFalse(urls.stream().anyMatch(url -> url.endsWith("library.json")), urls.toString());
            assertEquals(json("{\"again\":true}"), json(bridge.call("echo_echo", "{\"again\":true}")));
        } finally {
            bridge.close();
        }
    }

    @Test
    void refusesALibraryWithNoBinaryForThisPlatformAndStillStartsTheRest() throws IOException {
        String platformKey = LibraryAcquirer.currentPlatformKey();
        PluginHost host = freshHost();
        Object greeting = new Object();
        host.provideService("greeting", greeting);
        Path cache = freshCache();

        PluginException refusal = refusalOf(() -> loadInto(new LibraryServices(host), withoutDigest(ECHO_PIN, platformKey), cache));

        assertTrue(refusal.getDetail().contains(platformKey), refusal.getDetail());
        assertNull(pluginSession(host, "consumer").getServices().get("echo"));
        assertSame(greeting, pluginSession(host, "bystander").getServices().get("greeting"));
    }

    @Test
    void namesThePlatformWhenNeitherThePinNorTheReleaseCarriesIt() throws IOException {
        Path cache = freshCache();
        PluginException refusal = refusalOf(() -> new LibraryAcquirer("darwin-arm64").acquire(ECHO_PIN, cache));
        assertTrue(refusal.getDetail().contains("darwin-arm64"), refusal.getDetail());
    }

    @Test
    void keepsTheEmbeddersOwnServiceWhenALibraryOffersTheSameIdRefusingTheLibrarysCopy() throws IOException {
        PluginHost host = freshHost();
        EchoService embedderEcho = new EchoService() {
            @Override
            public JsonElement echo(JsonElement argument) {
                return json("\"the embedder's\"");
            }

            @Override
            public JsonObject fail(JsonObject argument) {
                return argument;
            }

            @Override
            public String repeat(RepeatArgument argument) {
                return "";
            }

            @Override
            public String garbage(JsonObject argument) {
                return "";
            }
        };
        host.provideService("echo", embedderEcho);
        Path cache = freshCache();

        PluginException refusal = refusalOf(() -> loadInto(new LibraryServices(host), ECHO_PIN, cache));

        assertEquals("echo", refusal.getPluginId());
        assertTrue(refusal.getDetail().contains("both the embedding host and library echo (weivel/bindings@v0.1.1)"), refusal.getDetail());
        EchoService served = (EchoService) pluginSession(host, "consumer").getServices().get("echo");
        assertEquals(json("\"the embedder's\""), served.echo(new JsonObject()));
    }

    @Test
    void refusesTheSecondLibrarysCopyOfAServiceIdTheFirstLibraryAlreadyOffers() throws IOException {
        PluginHost host = freshHost();
        Path cache = freshCache();
        LibraryServices services = new LibraryServices(host);
        LibraryBridge first = loadInto(services, ECHO_PIN, cache);
        try {
            PluginException refusal = refusalOf(() -> loadInto(services, ECHO_PIN, cache));

            assertTrue(refusal.getDetail().contains("both library echo (weivel/bindings@v0.1.1) and library echo (weivel/bindings@v0.1.1)"), refusal.getDetail());
            EchoService served = (EchoService) pluginSession(host, "consumer").getServices().get("echo");
            assertEquals(json("{\"still\":\"served\"}"), served.echo(json("{\"still\":\"served\"}")));
        } finally {
            first.close();
        }
    }

    @Test
    void offersNoneOfALibrarysServicesWhenOneOfThemCollides() throws IOException {
        PluginHost host = freshHost();
        Object embedderEcho = new Object();
        host.provideService("echo", embedderEcho);
        Path cache = freshCache();
        byte[] declared = Files.readAllBytes(binaryPath.resolveSibling("library.json"));
        LibraryPin pin = stageRelease(cache, binaryPath, withMirrorService(declared));

        PluginException refusal = refusalOf(() -> loadInto(new LibraryServices(host), pin, cache));

        assertEquals("echo", refusal.getPluginId());
        assertTrue(refusal.getDetail().contains("both the embedding host and library echo (weivel/bindings@v0.1.1)"), refusal.getDetail());
        assertNull(host.service("mirror"));
        assertNull(pluginSession(host, "mirror-consumer").getServices().get("mirror"));
        assertSame(embedderEcho, host.service("echo"));
    }

    @Test
    void refusesALibraryJsonThatStartsWithAByteOrderMarkAsNotJson() throws IOException {
        Path cache = freshCache();
        byte[] declared = Files.readAllBytes(binaryPath.resolveSibling("library.json"));
        LibraryPin pin = stageRelease(cache, binaryPath, withByteOrderMark(declared));

        PluginException refusal = refusalOf(() -> new LibraryAcquirer().acquire(pin, cache));

        assertTrue(refusal.getDetail().contains("library.json is not JSON"), refusal.getDetail());
    }

    @Test
    void pinsTheModelsEchoDeclarationAsTheReleaseCarriesIt() throws IOException {
        byte[] workingCopy = Files.readAllBytes(Paths.get(System.getProperty("model.directory"), "test", "fixtures", "echo.library.json"));
        byte[] asCommitted = new String(workingCopy, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);

        assertEquals(ECHO_PIN.sha256().get("library.json"), sha256Of(asCommitted));
    }

    @Test
    void acceptsACachedFileAnotherHostRenamedIntoPlaceFirstWhenItMatchesThePin() throws IOException {
        Path cache = freshCache();
        LibraryAcquirer racing = new LibraryAcquirer(LibraryAcquirer.currentPlatformKey(), LibraryAcquirer.overHttps(), (from, to) -> {
            Path original = to.getFileName().toString().equals("library.json") ? binaryPath.resolveSibling("library.json") : binaryPath;
            Files.copy(original, to, StandardCopyOption.REPLACE_EXISTING);
            throw new AccessDeniedException(from.toString(), to.toString(), null);
        });

        LibraryAcquirer.AcquiredLibrary acquired = racing.acquire(ECHO_PIN, cache);
        LibraryBridge bridge = LibraryBridge.open(acquired.binaryPath(), acquired.declaration());

        try {
            assertEquals(json("{\"raced\":true}"), json(bridge.call("echo_echo", "{\"raced\":true}")));
            assertFalse(filesUnder(cache).stream().anyMatch(name -> name.endsWith(".tmp")), filesUnder(cache).toString());
        } finally {
            bridge.close();
        }
    }

    @Test
    void refusesAnAssetItCouldNotRenameIntoPlaceWhenNothingValidIsThere() throws IOException {
        Path cache = freshCache();
        LibraryAcquirer denied = new LibraryAcquirer(LibraryAcquirer.currentPlatformKey(), LibraryAcquirer.overHttps(), (from, to) -> {
            throw new AccessDeniedException(from.toString(), to.toString(), null);
        });

        PluginException refusal = refusalOf(() -> denied.acquire(ECHO_PIN, cache));

        assertTrue(refusal.getDetail().contains("AccessDeniedException"), refusal.getDetail());
        assertFalse(filesUnder(cache).stream().anyMatch(name -> name.endsWith(".tmp")), filesUnder(cache).toString());
    }

    @Test
    void refusesAtLoadADeclaredSymbolTheBinaryDoesNotExport() throws IOException {
        JsonObject declaring = json(new String(Files.readAllBytes(binaryPath.resolveSibling("library.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray functions = declaring.getAsJsonArray("services").get(0).getAsJsonObject().getAsJsonArray("functions");
        JsonObject missing = functions.get(0).getAsJsonObject().deepCopy();
        missing.addProperty("name", "missing");
        missing.addProperty("symbol", "echo_missing");
        functions.add(missing);
        LibraryDeclaration withMissing = LibraryDeclaration.parse(declaring.toString());

        PluginException refusal = refusalOf(() -> LibraryBridge.open(binaryPath, withMissing));

        assertTrue(refusal.getDetail().contains("echo_missing"), refusal.getDetail());
    }

    @Test
    void releasesEveryResultExactlyOnce() {
        AtomicInteger releases = new AtomicInteger();
        LibraryBridge bridge = LibraryBridge.open(binaryPath, declaration, releases::incrementAndGet);
        String[] symbols = {"echo_echo", "echo_fail", "echo_garbage", "echo_repeat"};
        for (int call = 0; call < 100; call++) {
            String symbol = symbols[call % symbols.length];
            try {
                bridge.call(symbol, symbol.equals("echo_repeat") ? "{\"count\":3}" : "{}");
            } catch (PluginException expected) {
                assertEquals("echo", expected.getPluginId());
            }
        }
        bridge.close();
        assertEquals(100, releases.get());
    }

    @Test
    void refusesAClosedLibraryRatherThanCallingIntoUnloadedCode() {
        LibraryBridge bridge = LibraryBridge.open(binaryPath, declaration);
        bridge.close();
        assertTrue(refusalOf(() -> bridge.call("echo_echo", "{}")).getDetail().contains("closed"));
    }

    @Test
    void keepsALibraryOpenForOneBridgeWhenAnotherOverTheSameBinaryCloses() {
        LibraryBridge staying = LibraryBridge.open(binaryPath, declaration);
        LibraryBridge.open(binaryPath, declaration).close();
        try {
            assertEquals(json("{\"open\":true}"), json(staying.call("echo_echo", "{\"open\":true}")));
        } finally {
            staying.close();
        }
    }

    @Test
    void answersObjectMethodsOnTheServiceWithoutCallingTheLibrary() {
        assertTrue(echo.equals(echo));
        assertFalse(echo.equals(new Object()));
        assertEquals(echo.hashCode(), echo.hashCode());
        assertTrue(echo.toString().contains("echo"), echo.toString());
    }

    /** A service shape that asks for a function the echo library does not declare. */
    public interface WiderThanTheLibrary {
        String echo(JsonElement argument);

        String translate(JsonElement argument);
    }

    @Test
    void refusesAnInterfaceMethodTheLibraryDoesNotDeclareBeforeAnythingRegisters() {
        PluginHost host = freshHost();
        LibraryBridge bridge = LibraryBridge.open(binaryPath, declaration);
        try {
            PluginException refusal = refusalOf(() -> new LibraryServices(host).provide(bridge, declaration,
                    Collections.<String, Class<?>>singletonMap("echo", WiderThanTheLibrary.class)));

            assertTrue(refusal.getDetail().contains("translate"), refusal.getDetail());
            assertNull(host.service("echo"));
        } finally {
            bridge.close();
        }
    }

    @Test
    void refusesAServiceTheEmbedderGaveNoInterfaceFor() {
        PluginHost host = freshHost();
        LibraryBridge bridge = LibraryBridge.open(binaryPath, declaration);
        try {
            PluginException refusal = refusalOf(() -> new LibraryServices(host).provide(bridge, declaration,
                    Collections.<String, Class<?>>emptyMap()));

            assertTrue(refusal.getDetail().contains("echo"), refusal.getDetail());
            assertNull(host.service("echo"));
        } finally {
            bridge.close();
        }
    }

    /** A service shape with a default method, which a JDK proxy would send to the library rather than run. */
    public interface WithADefaultMethod {
        JsonElement echo(JsonElement argument);

        default String shout(JsonElement argument) {
            return "never reached";
        }
    }

    @Test
    void refusesADefaultMethodBeforeAnythingRegisters() {
        PluginHost host = freshHost();
        LibraryBridge bridge = LibraryBridge.open(binaryPath, declaration);
        try {
            PluginException refusal = refusalOf(() -> new LibraryServices(host).provide(bridge, declaration,
                    Collections.<String, Class<?>>singletonMap("echo", WithADefaultMethod.class)));

            assertTrue(refusal.getDetail().contains("shout"), refusal.getDetail());
            assertNull(host.service("echo"));
        } finally {
            bridge.close();
        }
    }

    /** A service shape whose return type cannot hold what echo answers. */
    public interface Mistyped {
        int echo(JsonElement argument);
    }

    private static Mistyped mistypedOn(PluginHost host, LibraryBridge bridge) {
        new LibraryServices(host).provide(bridge, declaration, Collections.<String, Class<?>>singletonMap("echo", Mistyped.class));
        return (Mistyped) host.service("echo");
    }

    @Test
    void refusesAResultThatDoesNotFitTheReturnTypeNamingLibraryAndFunction() {
        LibraryBridge bridge = LibraryBridge.open(binaryPath, declaration);
        try {
            Mistyped mistyped = mistypedOn(freshHost(), bridge);

            PluginException refusal = refusalOf(() -> mistyped.echo(json("{\"a\":1}")));

            assertEquals("echo", refusal.getPluginId());
            assertTrue(refusal.getDetail().contains("echo.echo"), refusal.getDetail());
            assertTrue(refusal.getDetail().contains("does not fit int"), refusal.getDetail());
        } finally {
            bridge.close();
        }
    }

    @Test
    void refusesANullResultForAPrimitiveReturnType() {
        LibraryBridge bridge = LibraryBridge.open(binaryPath, declaration);
        try {
            Mistyped mistyped = mistypedOn(freshHost(), bridge);

            PluginException refusal = refusalOf(() -> mistyped.echo(JsonNull.INSTANCE));

            assertTrue(refusal.getDetail().contains("echo.echo"), refusal.getDetail());
            assertTrue(refusal.getDetail().contains("null"), refusal.getDetail());
        } finally {
            bridge.close();
        }
    }

    @Test
    void refusesAPlatformKeyThatIsNotOsDashArch() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> new LibraryAcquirer("../x64"));
        assertTrue(refusal.getMessage().contains("../x64"), refusal.getMessage());
    }
}
