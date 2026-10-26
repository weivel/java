package io.github.intisy.ai.seam.jvm.library;

import io.github.intisy.ai.engine.EventBus;
import io.github.intisy.ai.engine.ManifestFacts;
import io.github.intisy.ai.engine.PluginHost;
import io.github.intisy.ai.engine.PluginSession;
import io.github.intisy.ai.engine.Scheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The neutral fixture library as released by {@code weivel/bindings}, and the host scaffolding its tests share. */
final class EchoFixture {

    static final LibraryPin ECHO_PIN = new LibraryPin("weivel/bindings", "v0.1.1", digests());

    private EchoFixture() {
    }

    private static Map<String, String> digests() {
        Map<String, String> sha256 = new LinkedHashMap<String, String>();
        sha256.put("library.json", "60c5c20c4fee91b1a6b220d792f6aecab8c9ddea8582ecabd9acec8172d2a2c7");
        sha256.put("linux-x64", "98cfd1f23800a3335a71cbf93f720baf8507e6ae1b257ef5654d7296852c41b9");
        sha256.put("win32-x64", "ced2da158d9b52486ad5112c2f5d8a286f21a807f612309b7dcb2c12b1d273e3");
        return sha256;
    }

    static Path freshCache() throws IOException {
        return Files.createTempDirectory("weivel-library-");
    }

    static PluginHost freshHost() {
        return new PluginHost("test", 1, null);
    }

    static Map<String, Class<?>> echoInterfaces() {
        return Collections.<String, Class<?>>singletonMap("echo", EchoService.class);
    }

    /** What an embedder does with one pin: acquire, open, offer, and close the library again if it offers nothing. */
    static LibraryBridge loadInto(LibraryServices services, LibraryPin pin, Path cache) {
        LibraryAcquirer.AcquiredLibrary acquired = new LibraryAcquirer().acquire(pin, cache);
        LibraryBridge bridge = LibraryBridge.open(acquired.binaryPath(), acquired.declaration());
        try {
            services.provide(bridge, acquired.declaration(), echoInterfaces());
        } catch (RuntimeException refused) {
            bridge.close();
            throw refused;
        }
        return bridge;
    }

    /** A plugin's session on the host, which is how a plugin reaches what the host offers. */
    static PluginSession pluginSession(PluginHost host, String pluginId) {
        return host.sessionFor(new ManifestFacts(pluginId, 1, null, null, null), new SilentBus());
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
