package com.weivel.host;

import static com.weivel.host.HostFixture.consuming;
import static com.weivel.host.HostFixture.done;
import static com.weivel.host.HostFixture.named;
import static com.weivel.host.HostFixture.value;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.CoreProbes;
import com.weivel.core.FailureKind;
import com.weivel.core.RefusalStatus;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** C libraries as host services, against the real core and the core's echo library. */
class LibrariesTest {
    private static final String ECHO = "echo_echo";

    @Test
    void aDeclarationLoadsAndEachServiceIsRegisteredAsTheHostsObject() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), declaring("echo", "other")));

            assertEquals(Arrays.asList("echo", "other"), ids(library));
            TestEntry consumer = fixture.entry("consumer");
            long plugin = fixture.register(consuming("consumer", "echo", "other"), consumer);
            Outcome outcome = done(fixture.activate(plugin).outcomes().get(0));
            Object resolved = value(consumer.context.get("echo"));
            assertEquals(ActivationState.ACTIVE, outcome.state());
            assertSame(library.services().get(0), resolved);
            assertSame(library.services().get(1), value(consumer.context.get("other")));
            LibraryAnswer answer = ((LibraryService) resolved).call("echo", bytes("plain java call"));
            assertArrayEquals(bytes("plain java call"), returned(answer));
        }
    }

    @Test
    void aHeapBufferIsReadFromItsPositionWhetherWritableOrReadOnly() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LibraryService echo = loadedEcho(fixture);
            ByteBuffer slice = ByteBuffer.wrap(bytes("..sliced.."), 2, 6);

            assertArrayEquals(bytes("sliced"), returned(echo.call("echo", slice)));
            assertArrayEquals(bytes("sliced"), returned(echo.call("echo", slice.asReadOnlyBuffer())));
            assertEquals(2, slice.position());
        }
    }

    @Test
    void theOutputIsCopiedOnceAndReleasedOnceBeforeTheCallAnswers() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LibraryService echo = loadedEcho(fixture);
            long before = countIn(echo.call("released", new byte[0]));

            LibraryAnswer answer = echo.call("echo", bytes("once"));
            long after = countIn(echo.call("released", new byte[0]));

            assertEquals(2, after - before, "the reading's own body and the echo's, each released once");
            LibraryAnswer.Returned result = (LibraryAnswer.Returned) answer;
            assertSame(result.bytes(), result.bytes(), "the one copy is handed out, never copied again");
        }
    }

    @Test
    void aFailureCarriesItsStatusAndItsBodyAsTextOnlyWhenItIsUtf8() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LibraryService echo = loadedEcho(fixture);

            LibraryAnswer.Failed failed = (LibraryAnswer.Failed) echo.call("fail", bytes("anything"));
            LibraryAnswer garbage = echo.call("garbage", new byte[0]);

            assertEquals(1, failed.status());
            assertEquals(Optional.of("echo refused on purpose"), failed.text());
            assertArrayEquals(bytes("echo refused on purpose"), failed.body());
            assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFE}, returned(garbage));
        }
    }

    /**
     * The echo library answers no failure whose body is not UTF-8, so the reading is driven with the bytes its
     * {@code echo_garbage} answers, through the same path a call's failure takes.
     */
    @Test
    void aFailureBodyThatIsNotUtf8IsOnlyBytesAndAnEmptyOneIsAnEmptyText() {
        LibraryAnswer.Failed notText = (LibraryAnswer.Failed) LibraryAnswer.of(3,
                new byte[] {(byte) 0xFF, (byte) 0xFE});
        LibraryAnswer.Failed empty = (LibraryAnswer.Failed) LibraryAnswer.of(2, new byte[0]);

        assertEquals(Optional.empty(), notText.text());
        assertEquals(3, notText.status());
        assertEquals(Optional.of(""), empty.text());
    }

    @Test
    void anUnloadUnregistersEveryServiceAndRefusesLaterCalls() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), declaring("echo")));
            TestEntry consumer = fixture.entry("consumer");
            fixture.activate(fixture.register(consuming("consumer", "echo"), consumer));
            LibraryService held = (LibraryService) value(consumer.context.get("echo"));

            assertEquals(true, value(fixture.host.unloadLibrary(library)));

            assertInstanceOf(LibraryAnswer.Unloaded.class, held.call("echo", bytes("late")));
            assertInstanceOf(Answer.Absent.class, consumer.context.get("echo"));
            assertEquals(false, value(fixture.host.unloadLibrary(library)));
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("echo", new Object()));
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void theBinaryIsClosedOnlyOnceNoCallRunsInIt() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        pauses.holdCalls = new Gates.Gate();
        try (HostFixture fixture = new HostFixture(pauses)) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), declaring("echo")));
            LibraryService echo = library.services().get(0);
            CompletableFuture<LibraryAnswer> running = CompletableFuture.supplyAsync(
                    () -> echo.call("echo", bytes("still running")));
            pauses.holdCalls.awaitReached();

            assertEquals(true, value(fixture.host.unloadLibrary(library)));
            List<String> closedWhileTheCallRan = pauses.closed();
            assertInstanceOf(LibraryAnswer.Unloaded.class, echo.call("echo", bytes("after")));
            pauses.holdCalls.open();
            LibraryAnswer answer = running.get(10, TimeUnit.SECONDS);

            assertEquals(Collections.emptyList(), closedWhileTheCallRan);
            assertArrayEquals(bytes("still running"), returned(answer));
            assertEquals(Collections.singletonList("echo-test"), pauses.closed());
        }
    }

    @Test
    void aLoadThatFailsPartwayIsUndoneAsAnUnload() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses)) {
            Answer<LoadedLibrary> load = fixture.host.loadLibrary(echoBinary(), declaring("echo", ""));

            assertInstanceOf(Answer.Refused.class, load);
            assertEquals(RefusalStatus.MALFORMED, ((Answer.Refused<LoadedLibrary>) load).status());
            assertEquals(Collections.singletonList("echo-test"), pauses.closed());
            TestEntry consumer = fixture.entry("consumer");
            fixture.activate(fixture.register(consuming("consumer", "echo"), consumer));
            assertInstanceOf(Answer.Absent.class, consumer.context.get("echo"));
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("echo", new Object()));
        }
    }

    @Test
    void anIdListedTwiceIsTheCallersMistakeToldToNoSink() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses)) {
            Answer<LoadedLibrary> load = fixture.host.loadLibrary(echoBinary(), declaring("echo", "echo"));

            assertMistakeNaming("echo", load);
            assertEquals(Collections.emptyList(), fixture.failures);
            assertEquals(Collections.emptyList(), pauses.closed(), "nothing was opened");
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("echo", new Object()));
        }
    }

    @Test
    void anIdHeldByTheHostAnotherLiveLibraryOrAPluginIsRefused() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            value(fixture.host.registerHostService("held", new Object()));
            value(fixture.host.loadLibrary(echoBinary(), declaring("other")));
            TestEntry provider = fixture.entry("provider");
            fixture.activate(fixture.register(named("provider"), provider));
            value(provider.context.register("provider:service", new Object()));

            assertMistakeNaming("held", fixture.host.loadLibrary(echoBinary(), declaring("held")));
            assertMistakeNaming("other", fixture.host.loadLibrary(echoBinary(), declaring("other")));
            assertMistakeNaming("provider:service",
                    fixture.host.loadLibrary(echoBinary(), declaring("provider:service")));
            assertEquals(Collections.emptyList(), fixture.failures);
        }
    }

    @Test
    void theHostsOwnRegistrationOfAnIdALiveLibraryDeclaresIsRefused() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), declaring("echo")));
            Object own = new Object();

            assertMistakeNaming("echo", fixture.host.registerHostService("echo", own));
            TestEntry consumer = fixture.entry("consumer");
            fixture.activate(fixture.register(consuming("consumer", "echo"), consumer));
            assertSame(library.services().get(0), value(consumer.context.get("echo")));
            value(fixture.host.unloadLibrary(library));
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("echo", own));
            assertEquals(Collections.emptyList(), fixture.failures);
        }
    }

    @Test
    void aCallerBuiltDeclarationOfAnotherKindIsTheCallersMistake() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LibraryDeclaration retired = new LibraryDeclaration("echo-test", "1.0.0", Optional.of("echo_release"),
                    Collections.singletonList(new LibraryDeclaration.Service("echo", Collections.singletonList(
                            new LibraryDeclaration.Function("echo", ECHO, 1, 0)))));

            assertMistakeNaming("echo", fixture.host.loadLibrary(echoBinary(), retired));
            assertEquals(Collections.emptyList(), fixture.failures);
        }
    }

    @Test
    void aPathOfNoLibraryOrALibraryLackingASymbolIsTheCallersMistake() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            LibraryDeclaration missing = new LibraryDeclaration("echo-test", "1.0.0", Optional.of("echo_release"),
                    Collections.singletonList(new LibraryDeclaration.Service("echo", Collections.singletonList(
                            LibraryDeclaration.Function.bytes("echo", "echo_nothing")))));

            assertMistakeNaming("echo_nothing", fixture.host.loadLibrary(echoBinary(), missing));
            assertInstanceOf(Answer.Mistake.class,
                    fixture.host.loadLibrary(echoBinary().resolveSibling("no-such-library"), declaring("echo")));
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("echo", new Object()));
        }
    }

    @Test
    void theDestroyUnloadsEveryLibraryAndReportsAFailingOneWhileTheRestStillUnload() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        pauses.refuseToClose = "first";
        HostFixture fixture = new HostFixture(pauses);
        LibraryService first = value(fixture.host.loadLibrary(echoBinary(), declaringAs("first", "one"))).services()
                .get(0);
        LibraryService second = value(fixture.host.loadLibrary(echoBinary(), declaringAs("second", "two")))
                .services().get(0);

        fixture.host.destroy().toCompletableFuture().get(10, TimeUnit.SECONDS);
        fixture.host.close();

        assertInstanceOf(LibraryAnswer.Unloaded.class, first.call("echo", bytes("late")));
        assertInstanceOf(LibraryAnswer.Unloaded.class, second.call("echo", bytes("late")));
        assertEquals(Arrays.asList("first", "second"), pauses.closingAsked());
        assertEquals(Collections.singletonList("second"), pauses.closed());
        assertEquals(1, fixture.failuresOf(FailureKind.LIBRARY_UNLOAD).size());
        assertInstanceOf(Answer.Destroyed.class, fixture.host.loadLibrary(echoBinary(), declaring("late", "x")));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void aCloseRefusedOnceTheDestroyHasFinishedIsReportedNowhere() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        pauses.holdCalls = new Gates.Gate();
        pauses.refuseToClose = "echo-test";
        HostFixture fixture = new HostFixture(pauses);
        LibraryService echo = loadedEcho(fixture);
        CompletableFuture<LibraryAnswer> running = CompletableFuture.supplyAsync(
                () -> echo.call("echo", bytes("outlives the destroy")));
        pauses.holdCalls.awaitReached();

        fixture.host.destroy().toCompletableFuture().get(10, TimeUnit.SECONDS);
        List<String> askedWhileTheCallRan = pauses.closingAsked();
        pauses.holdCalls.open();
        running.get(10, TimeUnit.SECONDS);
        fixture.host.close();

        assertEquals(Collections.emptyList(), askedWhileTheCallRan);
        assertEquals(Collections.singletonList("echo-test"), pauses.closingAsked());
        assertEquals(Collections.emptyList(), fixture.failures);
    }

    @Test
    void theHostServiceCountsALibrarysServicesForThePlan() throws Exception {
        try (HostFixture fixture = new HostFixture()) {
            value(fixture.host.loadLibrary(echoBinary(), declaring("echo")));
            TestEntry consumer = fixture.entry("consumer");
            long plugin = fixture.register(HostFixture.services("consumer", Collections.<String>emptyList(),
                    Collections.singletonList("echo")), consumer);

            Activation activation = fixture.activate(plugin);

            assertEquals(Collections.singletonList(plugin), activation.plan().order());
            assertEquals(ActivationState.ACTIVE, done(activation.outcomes().get(0)).state());
        }
    }

    @Test
    void anUnloadKeepsItsIdsClaimedUntilItsServicesAreUnregistered() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        AtomicReference<Answer<HostService>> slippedIn = new AtomicReference<>();
        try (HostFixture fixture = new HostFixture(pauses)) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), declaring("echo")));
            pauses.whileUnregistering = () -> slippedIn.set(fixture.host.registerHostService("echo", new Object()));

            value(fixture.host.unloadLibrary(library));

            assertMistakeNaming("echo", slippedIn.get());
            assertEquals(Collections.emptyList(), fixture.failures);
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("echo", new Object()));
        }
    }

    @Test
    void anUnloadedLibraryIsNoLongerKeptByTheHost() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses)) {
            LoadedLibrary first = value(fixture.host.loadLibrary(echoBinary(), declaringAs("first", "one")));
            value(fixture.host.loadLibrary(echoBinary(), declaringAs("second", "two")));
            fixture.host.loadLibrary(echoBinary(), declaringAs("third", "three", ""));

            value(fixture.host.unloadLibrary(first));

            assertEquals(1, fixture.host.libraryCount());
            assertEquals(Arrays.asList("third", "first"), pauses.closed());
        }
    }

    @Test
    void aLoadWhoseRegistrationThrowsIsUndoneAsAnUnload() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        pauses.failAfterRegistering = "echo";
        try (HostFixture fixture = new HostFixture(pauses)) {
            assertThrows(IllegalStateException.class,
                    () -> fixture.host.loadLibrary(echoBinary(), declaring("echo", "other")));

            assertEquals(0, fixture.host.libraryCount());
            assertEquals(Collections.singletonList("echo-test"), pauses.closed());
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("echo", new Object()));
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("other", new Object()));
        }
    }

    @Test
    void aDeclarationOfContractsAloneLoadsAndRegistersNothing() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses)) {
            LibraryDeclaration contractsAlone = new LibraryDeclaration("echo-test", "1.0.0",
                    Optional.of("echo_release"),
                    Collections.<LibraryDeclaration.Service>emptyList(),
                    Collections.singletonList(new LibraryDeclaration.Contract("echo.provider", "echo_description")));

            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), contractsAlone));

            assertEquals(Collections.emptyList(), library.services());
            assertEquals(Collections.emptyMap(), library.definitions());
            assertEquals(1, fixture.host.libraryCount());
            assertInstanceOf(Answer.Ok.class, fixture.host.registerHostService("echo.provider", new Object()));
            value(fixture.host.unloadLibrary(library));
            assertEquals(Collections.singletonList("echo-test"), pauses.closed());
            assertEquals(Collections.emptyList(), fixture.failures);
        }
    }

    @Test
    void aDeclarationOfNeitherAServiceNorAContractIsTheCallersMistake() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses)) {
            assertMistakeNaming("neither a service nor a contract",
                    fixture.host.loadLibrary(echoBinary(), declaring()));
            assertEquals(Collections.emptyList(), pauses.closed(), "nothing was opened");
            assertEquals(Collections.emptyList(), fixture.failures);
        }
    }

    static LibraryService loadedEcho(HostFixture fixture) {
        return value(fixture.host.loadLibrary(echoBinary(), declaring("echo"))).services().get(0);
    }

    /** The echo library, imported as {@code echo-test}, declaring each id as a service offering every echo function. */
    static LibraryDeclaration declaring(String... ids) {
        return declaringAs("echo-test", ids);
    }

    static LibraryDeclaration declaringAs(String importName, String... ids) {
        List<LibraryDeclaration.Service> services = new ArrayList<>();
        for (String id : ids) {
            services.add(new LibraryDeclaration.Service(id, Arrays.asList(
                    LibraryDeclaration.Function.bytes("echo", ECHO),
                    LibraryDeclaration.Function.bytes("fail", "echo_fail"),
                    LibraryDeclaration.Function.bytes("garbage", "echo_garbage"),
                    LibraryDeclaration.Function.bytes("where", "echo_where"),
                    LibraryDeclaration.Function.bytes("released", "echo_released"))));
        }
        return new LibraryDeclaration(importName, "1.0.0", Optional.of("echo_release"), services);
    }

    private static List<String> ids(LoadedLibrary library) {
        List<String> ids = new ArrayList<>();
        library.services().forEach(service -> ids.add(service.id()));
        return ids;
    }

    static Path echoBinary() {
        return CoreProbes.echoBinary();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] returned(LibraryAnswer answer) {
        assertInstanceOf(LibraryAnswer.Returned.class, answer, String.valueOf(answer));
        return ((LibraryAnswer.Returned) answer).bytes();
    }

    static long addressIn(LibraryAnswer answer) {
        return ByteBuffer.wrap(returned(answer)).order(ByteOrder.nativeOrder()).getLong();
    }

    private static long countIn(LibraryAnswer answer) {
        return addressIn(answer);
    }

    static void assertMistakeNaming(String text, Answer<?> answer) {
        assertInstanceOf(Answer.Mistake.class, answer, String.valueOf(answer));
        String detail = ((Answer.Mistake<?>) answer).detail();
        assertTrue(detail.contains(text), detail);
    }
}
