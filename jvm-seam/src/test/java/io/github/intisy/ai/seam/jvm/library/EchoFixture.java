package io.github.intisy.ai.seam.jvm.library;

import io.github.intisy.ai.engine.EventBus;
import io.github.intisy.ai.engine.ManifestFacts;
import io.github.intisy.ai.engine.PluginHost;
import io.github.intisy.ai.engine.PluginSession;
import io.github.intisy.ai.engine.Scheduler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The neutral fixture library as released by {@code weivel/bindings}, and the host scaffolding its tests share.
 *
 * @implNote The v0.1.1 release still carries a declaration in the old shape, which no host now
 * accepts, so the declaration is pinned from the model's fixture and staged by {@link #freshCache}.
 * Only the binaries are downloaded from the release; pin the next release's own library.json here.
 */
final class EchoFixture {

    private static final byte[] ECHO_DECLARATION = echoDeclaration();

    static final LibraryPin ECHO_PIN = new LibraryPin("weivel/bindings", "v0.1.1", digests());

    private EchoFixture() {
    }

    private static Map<String, String> digests() {
        Map<String, String> sha256 = new LinkedHashMap<String, String>();
        sha256.put("library.json", sha256Of(ECHO_DECLARATION));
        sha256.put("linux-x64", "98cfd1f23800a3335a71cbf93f720baf8507e6ae1b257ef5654d7296852c41b9");
        sha256.put("win32-x64", "ced2da158d9b52486ad5112c2f5d8a286f21a807f612309b7dcb2c12b1d273e3");
        return sha256;
    }

    private static byte[] echoDeclaration() {
        try {
            return Files.readAllBytes(Paths.get(System.getProperty("model.directory"), "test", "fixtures", "echo.library.json"));
        } catch (IOException unreadable) {
            throw new IllegalStateException("the model's echo declaration cannot be read: " + unreadable.getMessage(), unreadable);
        }
    }

    /** A fresh cache already holding the echo declaration {@link #ECHO_PIN} names, so acquiring it downloads only the binary. */
    static Path freshCache() throws IOException {
        Path cache = Files.createTempDirectory("weivel-library-");
        Path directory = cache.resolve(ECHO_PIN.repository()).resolve(ECHO_PIN.tag());
        Files.createDirectories(directory);
        Files.write(directory.resolve("library.json"), ECHO_DECLARATION);
        return cache;
    }

    static PluginHost freshHost() {
        return new PluginHost("test", 1, null);
    }

    static Map<String, Class<?>> echoInterfaces() {
        Map<String, Class<?>> interfaces = new LinkedHashMap<String, Class<?>>();
        interfaces.put("echo", EchoService.class);
        interfaces.put("mirror", EchoService.class);
        return Collections.unmodifiableMap(interfaces);
    }

    static LibraryBridge loadInto(LibraryServices services, LibraryPin pin, Path cache) {
        return services.load(pin, cache, echoInterfaces());
    }

    /** Puts a release's assets where the cache keeps them and pins exactly these declaration bytes, so nothing is downloaded. */
    static LibraryPin stageRelease(Path cache, Path binaryPath, byte[] declarationBytes) throws IOException {
        Path directory = cache.resolve(ECHO_PIN.repository()).resolve(ECHO_PIN.tag());
        Files.createDirectories(directory);
        Files.write(directory.resolve("library.json"), declarationBytes);
        Files.copy(binaryPath, directory.resolve(binaryPath.getFileName()));
        return withDigest(ECHO_PIN, "library.json", sha256Of(declarationBytes));
    }

    /** The echo declaration with a second service, {@code mirror}, made of the same functions. */
    static byte[] withMirrorService(byte[] declarationBytes) {
        JsonObject declaration = JsonParser.parseString(new String(declarationBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray services = declaration.getAsJsonArray("services");
        JsonObject mirror = services.get(0).getAsJsonObject().deepCopy();
        mirror.addProperty("id", "mirror");
        services.add(mirror);
        return declaration.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] withByteOrderMark(byte[] bytes) {
        byte[] marked = new byte[bytes.length + 3];
        marked[0] = (byte) 0xef;
        marked[1] = (byte) 0xbb;
        marked[2] = (byte) 0xbf;
        System.arraycopy(bytes, 0, marked, 3, bytes.length);
        return marked;
    }

    static String sha256Of(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte octet : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(String.format("%02x", octet & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JVM provides SHA-256", impossible);
        }
    }

    /** A plugin's session on the host, declaring the one service it consumes so it may reach it. */
    static PluginSession pluginSession(PluginHost host, String pluginId, String consumed) {
        ManifestFacts facts = new ManifestFacts(pluginId, 1, null, null, null)
                .withServices(Collections.singletonList(consumed), null);
        return host.sessionFor(facts, new SilentBus());
    }

    static LibraryPin withDigest(LibraryPin pin, String key, String digest) {
        Map<String, String> sha256 = new LinkedHashMap<String, String>(pin.sha256());
        sha256.put(key, digest);
        return new LibraryPin(pin.repository(), pin.tag(), sha256);
    }

    static LibraryPin withoutDigest(LibraryPin pin, String key) {
        Map<String, String> sha256 = new LinkedHashMap<String, String>(pin.sha256());
        sha256.remove(key);
        return new LibraryPin(pin.repository(), pin.tag(), sha256);
    }

    static String withOneDigitChanged(String digest) {
        return (digest.charAt(0) == '0' ? "1" : "0") + digest.substring(1);
    }

    private static final class SilentBus implements EventBus {
        @Override
        public void publish(String topic, Object payload) {
        }

        @Override
        public Scheduler.Cancellable subscribe(String topic, Listener listener) {
            return new Scheduler.Cancellable() {
                @Override
                public void cancel() {
                }
            };
        }
    }
}
