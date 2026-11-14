package com.weivel.host;

import static com.weivel.host.HostFixture.done;
import static com.weivel.host.HostFixture.named;
import static com.weivel.host.HostFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.context.PluginContext;
import com.weivel.core.Answer;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/** A plugin's entry, loaded by its class name from the caller's class loader or from a JAR the host opens. */
class EntriesTest {
    private static final String JAR_ENTRY = "com.weivel.host.jar.GreetingEntry";

    @Test
    void anEntryIsLoadedByItsClassNameFromTheCallersLoader() {
        Entry loaded = value(Entries.load(NamedEntry.class.getName(), getClass().getClassLoader()));

        assertInstanceOf(NamedEntry.class, loaded);
    }

    @Test
    void aClassThatIsNoEntryOrCannotBeFoundOrMadeIsTheCallersMistake() {
        ClassLoader loader = getClass().getClassLoader();

        assertInstanceOf(Answer.Mistake.class, Entries.load(String.class.getName(), loader));
        assertInstanceOf(Answer.Mistake.class, Entries.load("com.weivel.host.Absent", loader));
        assertInstanceOf(Answer.Mistake.class, Entries.load(UnmadeEntry.class.getName(), loader));
    }

    @Test
    void anEntryIsLoadedFromAJarOffTheTestClassPathAndRunsAsAPlugin() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(JAR_ENTRY));
        try (HostFixture fixture = new HostFixture()) {
            Entry loaded = value(fixture.host.loadEntry(pluginsJar(), JAR_ENTRY));
            long plugin = fixture.register(named("greeting"), loaded);
            long reader = fixture.register(HostFixture.consuming("reader", "greeting:hello"),
                    fixture.entry("reader"));

            Activation activation = fixture.activate(plugin, reader);

            assertEquals(ActivationState.ACTIVE, done(activation.outcomes().get(0)).state());
            PluginContext context = fixture.host.context(reader).get();
            assertEquals("hello from a jar", value(context.get("greeting:hello")));
            assertTrue(loaded.getClass().getClassLoader() instanceof URLClassLoader);
            assertSame(Entry.class.getClassLoader(), loaded.getClass().getClassLoader().getParent());
        }
    }

    @Test
    void aClassInTheJarThatIsNoEntryIsTheCallersMistake() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            assertInstanceOf(Answer.Mistake.class,
                    fixture.host.loadEntry(pluginsJar(), "com.weivel.host.jar.Untouched"));
        }
    }

    @Test
    void theJarsClassLoaderStaysOpenThroughTheDestroyAndClosesOnlyWithTheHost() throws Exception {
        HostFixture fixture = new HostFixture();
        Entry loaded = value(fixture.host.loadEntry(pluginsJar(), JAR_ENTRY));
        ClassLoader loader = loaded.getClass().getClassLoader();
        fixture.activate(fixture.register(named("greeting"), loaded));

        done(fixture.host.destroy());
        assertNotNull(loader.loadClass("com.weivel.host.jar.Untouched"));
        fixture.host.close();

        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.weivel.host.jar.Unloaded"));
    }

    @Test
    void aJarsClassLoaderHasTheLoaderOfEntryAsItsParentEvenWhereThatIsNotTheSystemLoader() throws Exception {
        try (URLClassLoader isolated = new URLClassLoader(classPath(), ClassLoader.getSystemClassLoader().getParent())) {
            Class<?> entries = Class.forName(Entries.class.getName(), true, isolated);
            Method jarLoader = entries.getDeclaredMethod("jarLoader", URL.class);
            jarLoader.setAccessible(true);

            try (URLClassLoader opened = (URLClassLoader) jarLoader.invoke(null, pluginsJar().toUri().toURL())) {
                assertSame(isolated, Class.forName(Entry.class.getName(), false, isolated).getClassLoader());
                assertSame(isolated, opened.getParent());
            }
        }
    }

    private static URL[] classPath() throws Exception {
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int index = 0; index < entries.length; index++) {
            urls[index] = Paths.get(entries[index]).toUri().toURL();
        }
        return urls;
    }

    private static Path pluginsJar() {
        Path jar = Paths.get(System.getProperty("weivel.test.pluginsJar"));
        assertTrue(Files.isRegularFile(jar), jar + " is no file");
        return jar;
    }

    /** An entry the caller's own class loader finds. */
    public static final class NamedEntry implements Entry {
        @Override
        public CompletionStage<?> activate(PluginContext context) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    /** An entry with no constructor taking nothing. */
    public static final class UnmadeEntry implements Entry {
        public UnmadeEntry(String unused) {
        }

        @Override
        public CompletionStage<?> activate(PluginContext context) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
