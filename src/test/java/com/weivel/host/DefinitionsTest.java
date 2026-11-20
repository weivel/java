package com.weivel.host;

import static com.weivel.host.HostFixture.consuming;
import static com.weivel.host.HostFixture.value;
import static com.weivel.host.LibrariesTest.assertMistakeNaming;
import static com.weivel.host.LibrariesTest.echoBinary;
import static com.weivel.host.LibrariesTest.returned;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.context.PluginEvent;
import com.weivel.context.ServiceRegistration;
import com.weivel.context.ServiceWatcher;
import com.weivel.core.Answer;
import com.weivel.core.ForeignLibrary;
import com.weivel.core.ForeignMemory;
import com.weivel.core.NativeType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Definition services, against the real core and the core's echo library: what the language registers, when the
 * binary closes, and how a holder lets go, beyond what the kit's {@code library} vectors witness.
 */
class DefinitionsTest {
    private static final String LIBRARY = "echo-test";

    @Test
    void aDefinitionIsTheBackendsOwnObjectCarryingTheAddressItsSymbolAnswered() throws Exception {
        long answered = answeredBy("echo_service_definition");
        try (HostFixture fixture = new HostFixture()) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), definitions("echo")));
            TestEntry consumer = activated(fixture, "consumer", "echo");

            Object definition = library.definitions().get("echo");
            assertSame(definition, value(consumer.context.get("echo")));
            assertEquals(answered, ForeignMemory.addressOf(definition));
            assertEquals(nativeObjectClass(), nativeObjectClassOf(definition));
            assertEquals(Collections.emptyList(), library.services());
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void aBytesCallStillRunningKeepsTheBinaryOpenPastTheLastDefinitionRelease() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        pauses.holdCalls = new Gates.Gate();
        try (HostFixture fixture = new HostFixture(pauses)) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), bytesAndDefinition()));
            TestEntry consumer = activated(fixture, "consumer", "echo-bytes", "echo");
            List<PluginEvent.Revoked> revoked = revocationsHeardBy(consumer);
            LibraryService bytes = (LibraryService) value(consumer.context.get("echo-bytes"));
            value(consumer.context.get("echo"));
            CompletableFuture<LibraryAnswer> running = CompletableFuture.supplyAsync(
                    () -> bytes.call("echo", "still running".getBytes(StandardCharsets.UTF_8)));
            pauses.holdCalls.awaitReached();

            value(fixture.host.unloadLibrary(library));
            assertEquals(true, value(consumer.context.releaseHolding(awaiting(revoked))));
            List<String> closedWhileTheCallRan = pauses.closed();
            pauses.holdCalls.open();
            LibraryAnswer answer = running.get(10, TimeUnit.SECONDS);

            assertEquals(Collections.emptyList(), closedWhileTheCallRan);
            assertArrayEquals("still running".getBytes(StandardCharsets.UTF_8), returned(answer));
            assertEquals(Collections.singletonList(LIBRARY), pauses.closed());
        }
    }

    @Test
    void aHoldingByAGetAndAWatchIsLetGoOfOnce() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses)) {
            TestEntry consumer = activated(fixture, "consumer", "echo");
            List<Object> heard = Collections.synchronizedList(new ArrayList<>());
            value(consumer.context.watch("echo", new ServiceWatcher() {
                @Override
                public void registered(Object service) {
                    heard.add(service);
                }

                @Override
                public void unregistered() {
                }
            }));
            List<PluginEvent.Revoked> revoked = revocationsHeardBy(consumer);
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), definitions("echo")));
            Object definition = library.definitions().get("echo");
            assertSame(definition, value(consumer.context.get("echo")));

            value(fixture.host.unloadLibrary(library));
            List<String> closedAtTheUnload = pauses.closed();
            boolean released = value(consumer.context.releaseHolding(awaiting(revoked)));
            boolean releasedAgain = value(consumer.context.releaseHolding(awaiting(revoked)));

            assertEquals(Collections.singletonList(definition), heard);
            assertEquals(1, revoked.size());
            assertSame(definition, revoked.get(0).object());
            assertEquals(Collections.emptyList(), closedAtTheUnload);
            assertTrue(released);
            assertFalse(releasedAgain);
            assertEquals(Collections.singletonList(LIBRARY), pauses.closed());
        }
    }

    @Test
    void anOrdinaryServiceIsRevokedAwaitingNoRelease() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            Object own = new Object();
            HostService service = value(fixture.host.registerHostService("plain", own));
            TestEntry consumer = activated(fixture, "consumer", "plain");
            List<PluginEvent.Revoked> revoked = revocationsHeardBy(consumer);
            assertSame(own, value(consumer.context.get("plain")));

            value(service.unregister());

            assertEquals(1, revoked.size());
            assertSame(own, revoked.get(0).object());
            assertFalse(revoked.get(0).awaitsRelease());
            assertEquals(false, value(consumer.context.releaseHolding(revoked.get(0))));
        }
    }

    @Test
    void aPluginHearsWhenNothingHoldsWhatItRegisteredAwaitingReleases() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            TestEntry provider = fixture.entry("provider");
            long providerPlugin = fixture.register(HostFixture.services("provider",
                    Collections.singletonList("provider:held"), Collections.<String>emptyList()), provider);
            TestEntry consumer = fixture.entry("consumer");
            long consumerPlugin = fixture.register(consuming("consumer", "provider:held"), consumer);
            fixture.activate(providerPlugin, consumerPlugin);
            List<PluginEvent> heard = Collections.synchronizedList(new ArrayList<>());
            provider.context.listen(heard::add);
            List<PluginEvent.Revoked> revoked = revocationsHeardBy(consumer);
            Object held = new Object();
            ServiceRegistration registration = value(provider.context.registerAwaitingReleases("provider:held",
                    held));
            assertSame(held, value(consumer.context.get("provider:held")));

            value(registration.unregister());
            List<PluginEvent> heardAtTheUnregistration = new ArrayList<>(heard);
            value(consumer.context.releaseHolding(awaiting(revoked)));

            assertEquals(Collections.emptyList(), heardAtTheUnregistration);
            assertEquals(Collections.<PluginEvent>singletonList(new PluginEvent.HoldingsReleased("provider:held", held)),
                    heard);
        }
    }

    @Test
    void aHolderTheDestroyReleasesEndsItsRevokedHolding() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses)) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), definitions("echo")));
            TestEntry consumer = activated(fixture, "consumer", "echo");
            value(consumer.context.get("echo"));
            value(fixture.host.unloadLibrary(library));
            List<String> closedAtTheUnload = pauses.closed();

            fixture.host.destroy().toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertEquals(Collections.emptyList(), closedAtTheUnload);
            assertEquals(Collections.singletonList(LIBRARY), pauses.closed());
        }
    }

    @Test
    void aDefinitionTheDestroyFindsRegisteredClosesItsBinaryOnceTheCoreIsDestroyed() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses)) {
            value(fixture.host.loadLibrary(echoBinary(), definitions("echo")));
            TestEntry consumer = activated(fixture, "consumer", "echo");
            value(consumer.context.get("echo"));
            List<String> closedBeforeTheDestroy = pauses.closed();

            fixture.host.destroy().toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertEquals(Collections.emptyList(), closedBeforeTheDestroy);
            assertEquals(Collections.singletonList(LIBRARY), pauses.closed());
        }
    }

    @Test
    void aLibraryOfDefinitionsAloneNeedsNoReleaseSymbol() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), definitions("echo", "other")));

            assertEquals(Arrays.asList("echo", "other"), new ArrayList<>(library.definitions().keySet()));
            assertEquals(Optional.empty(), library.declaration().release());
        }
    }

    @Test
    void aCallerBuiltDeclarationMisusingADefinitionIsTheCallersMistake() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LibraryDeclaration beside = declaration(Optional.of("echo_release"), new LibraryDeclaration.Service(
                    "echo", Arrays.asList(LibraryDeclaration.Function.definition("definition",
                            "echo_service_definition"), LibraryDeclaration.Function.bytes("echo", "echo_echo"))));
            LibraryDeclaration facts = declaration(Optional.<String>empty(), new LibraryDeclaration.Service("echo",
                    Collections.singletonList(new LibraryDeclaration.Function("definition", "echo_service_definition",
                            LibraryDeclaration.DEFINITION, 2))));
            LibraryDeclaration unreleased = declaration(Optional.<String>empty(), new LibraryDeclaration.Service(
                    "echo", Collections.singletonList(LibraryDeclaration.Function.bytes("echo", "echo_echo"))));
            LibraryDeclaration otherKind = declaration(Optional.<String>empty(), new LibraryDeclaration.Service(
                    "echo", Collections.singletonList(new LibraryDeclaration.Function("definition",
                            "echo_service_definition", 4, 0))));

            assertMistakeNaming("beside another function", fixture.host.loadLibrary(echoBinary(), beside));
            assertMistakeNaming("context facts", fixture.host.loadLibrary(echoBinary(), facts));
            assertMistakeNaming("no release symbol", fixture.host.loadLibrary(echoBinary(), unreleased));
            assertMistakeNaming("of the kind 4", fixture.host.loadLibrary(echoBinary(), otherKind));
            assertEquals(Collections.emptyList(), fixture.failures);
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("echo", new Object()));
        }
    }

    /** Every revocation the plugin hears from now on, in order. */
    static List<PluginEvent.Revoked> revocationsHeardBy(TestEntry plugin) {
        List<PluginEvent.Revoked> revoked = Collections.synchronizedList(new ArrayList<>());
        plugin.context.listen(event -> {
            if (event instanceof PluginEvent.Revoked) {
                revoked.add((PluginEvent.Revoked) event);
            }
        });
        return revoked;
    }

    /** The one revocation heard that awaits the plugin's release. */
    static PluginEvent.Revoked awaiting(List<PluginEvent.Revoked> revoked) {
        List<PluginEvent.Revoked> awaiting = new ArrayList<>();
        synchronized (revoked) {
            for (PluginEvent.Revoked heard : revoked) {
                if (heard.awaitsRelease()) {
                    awaiting.add(heard);
                }
            }
        }
        assertEquals(1, awaiting.size(), String.valueOf(revoked));
        return awaiting.get(0);
    }

    private static TestEntry activated(HostFixture fixture, String name, String... consumes) {
        TestEntry entry = fixture.entry(name);
        fixture.activate(fixture.register(consuming(name, consumes), entry));
        return entry;
    }

    /** The echo library declaring each id as the definition {@code echo_service_definition} or its sibling. */
    static LibraryDeclaration definitions(String... ids) {
        List<LibraryDeclaration.Service> services = new ArrayList<>();
        String[] symbols = {"echo_service_definition", "echo_other_service_definition"};
        for (int index = 0; index < ids.length; index++) {
            services.add(new LibraryDeclaration.Service(ids[index], Collections.singletonList(
                    LibraryDeclaration.Function.definition("definition", symbols[index]))));
        }
        return new LibraryDeclaration(LIBRARY, "1.0.0", Optional.<String>empty(), services);
    }

    private static LibraryDeclaration bytesAndDefinition() {
        return declaration(Optional.of("echo_release"),
                new LibraryDeclaration.Service("echo-bytes", Collections.singletonList(
                        LibraryDeclaration.Function.bytes("echo", "echo_echo"))),
                new LibraryDeclaration.Service("echo", Collections.singletonList(
                        LibraryDeclaration.Function.definition("definition", "echo_service_definition"))));
    }

    private static LibraryDeclaration declaration(Optional<String> release, LibraryDeclaration.Service... services) {
        return new LibraryDeclaration(LIBRARY, "1.0.0", release, Arrays.asList(services));
    }

    private static long answeredBy(String symbol) {
        Answer<ForeignLibrary> opened = ForeignLibrary.open(echoBinary(), Collections.singletonList(symbol));
        try (ForeignLibrary library = value(opened)) {
            return library.function(symbol, NativeType.ADDRESS).call();
        }
    }

    private static String nativeObjectClass() {
        return System.getProperty("weivel.test.backend").equals("ffm") ? "java.lang.foreign.MemorySegment"
                : "com.sun.jna.Pointer";
    }

    private static String nativeObjectClassOf(Object definition) {
        for (Class<?> type = definition.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("com.sun.jna.Pointer")) {
                return type.getName();
            }
            for (Class<?> implemented : type.getInterfaces()) {
                if (implemented.getName().equals("java.lang.foreign.MemorySegment")) {
                    return implemented.getName();
                }
            }
        }
        return definition.getClass().getName();
    }
}
