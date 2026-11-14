package com.weivel.host;

import static com.weivel.host.HostFixture.consuming;
import static com.weivel.host.HostFixture.value;
import static com.weivel.host.LibrariesTest.assertMistakeNaming;
import static com.weivel.host.LibrariesTest.declaring;
import static com.weivel.host.LibrariesTest.echoBinary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** A library's load, calls and unload interleaved with the host's own registrations and destroy, by gates. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class LibraryRacesTest {
    private static final long WAIT_SECONDS = Gates.WAIT_SECONDS;

    @Test
    void aHostRegistrationMadeAfterALoadsCheckAndBeforeItsKeepingRefusesTheLoad() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        pauses.holdBeforeKept = new Gates.Gate();
        try (HostFixture fixture = new HostFixture(pauses)) {
            CompletableFuture<Answer<LoadedLibrary>> load = CompletableFuture.supplyAsync(
                    () -> fixture.host.loadLibrary(echoBinary(), declaring("echo")));
            pauses.holdBeforeKept.awaitReached();
            Object own = new Object();
            value(fixture.host.registerHostService("echo", own));
            pauses.holdBeforeKept.open();

            assertMistakeNaming("echo", load.get(WAIT_SECONDS, TimeUnit.SECONDS));
            assertSame(own, resolvedEcho(fixture));
            assertEquals(0, fixture.host.libraryCount());
        }
    }

    @Test
    void aHostRegistrationClaimedAndUnansweredRefusesALoadKeptMeanwhile() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        pauses.holdBeforeKept = new Gates.Gate();
        pauses.holdAfterClaim = new Gates.Gate();
        try (HostFixture fixture = new HostFixture(pauses)) {
            CompletableFuture<Answer<LoadedLibrary>> load = CompletableFuture.supplyAsync(
                    () -> fixture.host.loadLibrary(echoBinary(), declaring("echo")));
            pauses.holdBeforeKept.awaitReached();
            Object own = new Object();
            CompletableFuture<Answer<HostService>> registration = CompletableFuture.supplyAsync(
                    () -> fixture.host.registerHostService("echo", own));
            pauses.holdAfterClaim.awaitReached();
            pauses.holdBeforeKept.open();
            Answer<LoadedLibrary> loaded;
            try {
                loaded = load.get(WAIT_SECONDS, TimeUnit.SECONDS);
            } finally {
                pauses.holdAfterClaim.open();
            }

            assertMistakeNaming("echo", loaded);
            assertInstanceOf(Answer.Ok.class, registration.get(WAIT_SECONDS, TimeUnit.SECONDS));
            assertSame(own, resolvedEcho(fixture));
        }
    }

    @Test
    void aDestroyMadeWhileALoadRegistersAnswersTheLoadDestroyed() throws Exception {
        LibraryPauses pauses = new LibraryPauses();
        pauses.holdAfterRegistered = new Gates.Gate();
        HostFixture fixture = new HostFixture(pauses);
        CompletableFuture<Answer<LoadedLibrary>> load = CompletableFuture.supplyAsync(
                () -> fixture.host.loadLibrary(echoBinary(), declaring("echo")));
        pauses.holdAfterRegistered.awaitReached();

        fixture.host.destroy().toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        pauses.holdAfterRegistered.open();

        assertInstanceOf(Answer.Destroyed.class, load.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(Collections.singletonList("echo-test"), pauses.closed());
        fixture.host.close();
    }

    @Test
    void manyThreadsCallingOneLibraryWhileItIsUnloadedEachHearItsOwnBytesOrUnloaded() throws Exception {
        int threads = 8;
        int callsEach = 300;
        LibraryPauses pauses = new LibraryPauses();
        try (HostFixture fixture = new HostFixture(pauses)) {
            LoadedLibrary library = value(fixture.host.loadLibrary(echoBinary(), declaring("echo")));
            LibraryService echo = library.services().get(0);
            CyclicBarrier start = new CyclicBarrier(threads + 1);
            CountDownLatch halfway = new CountDownLatch(threads * callsEach / 2);
            List<String> wrong = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger unloaded = new AtomicInteger();
            List<Thread> callers = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                int caller = index;
                Thread thread = new Thread(() -> callRepeatedly(echo, caller, callsEach, start, halfway, wrong,
                        unloaded));
                thread.start();
                callers.add(thread);
            }
            start.await(WAIT_SECONDS, TimeUnit.SECONDS);
            Gates.await(halfway);

            assertEquals(true, value(fixture.host.unloadLibrary(library)));
            for (Thread thread : callers) {
                thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
            }

            assertEquals(Collections.emptyList(), wrong);
            assertTrue(unloaded.get() > 0, "no call came after the unload");
            assertEquals(Collections.singletonList("echo-test"), pauses.closed());
            assertEquals(Collections.emptyList(), fixture.failures);
        }
    }

    private static void callRepeatedly(LibraryService echo, int caller, int calls, CyclicBarrier start,
                                       CountDownLatch halfway, List<String> wrong, AtomicInteger unloaded) {
        try {
            start.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception interrupted) {
            wrong.add("caller " + caller + " never started: " + interrupted);
            return;
        }
        boolean sawUnloaded = false;
        for (int call = 0; call < calls; call++) {
            byte[] input = ("caller " + caller + " call " + call).getBytes(StandardCharsets.UTF_8);
            LibraryAnswer answer = echo.call("echo", input);
            if (answer instanceof LibraryAnswer.Unloaded) {
                sawUnloaded = true;
                unloaded.incrementAndGet();
            } else if (sawUnloaded) {
                wrong.add("caller " + caller + " was answered after the library was unloaded to it");
            } else if (!(answer instanceof LibraryAnswer.Returned)) {
                wrong.add("caller " + caller + " heard " + answer);
            } else if (!Arrays.equals(input, ((LibraryAnswer.Returned) answer).bytes())) {
                wrong.add("caller " + caller + " heard another call's bytes");
            }
            halfway.countDown();
        }
    }

    private static Object resolvedEcho(HostFixture fixture) {
        TestEntry consumer = fixture.entry("consumer");
        fixture.activate(fixture.register(consuming("consumer", "echo"), consumer));
        return value(consumer.context.get("echo"));
    }
}
