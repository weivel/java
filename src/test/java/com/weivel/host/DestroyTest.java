package com.weivel.host;

import static com.weivel.host.HostFixture.consuming;
import static com.weivel.host.HostFixture.done;
import static com.weivel.host.HostFixture.named;
import static com.weivel.host.HostFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.context.ServiceWatcher;
import com.weivel.context.WantLimits;
import com.weivel.core.Answer;
import com.weivel.core.CoreConstants;
import com.weivel.core.EdgeSource;
import com.weivel.core.Refusal;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The host's destroy and free, step by step, against the real core. */
class DestroyTest {
    @Test
    void destroyReleasesEveryPluginOpenedInTheReverseOrderAndDestroysTheCore() throws Exception {
        HostFixture fixture = new HostFixture();
        Host host = fixture.host;
        long first = fixture.register(named("first"), fixture.entry("first"));
        long second = fixture.register(named("second"), fixture.entry("second"));
        long third = fixture.register(named("third"), fixture.entry("third"));
        fixture.activate(first, second, third);

        DestroyResult result = done(host.destroy());

        assertEquals(Arrays.asList(third, second, first), result.released());
        assertEquals(Arrays.asList("deactivate third", "deactivate second", "deactivate first"),
                fixture.trace().subList(3, 6));
        assertTrue(fixture.failures.isEmpty());
        host.close();
    }

    @Test
    void destroyReleasesAPluginInFlightAndAQuarantinedOneEachWithItsDeactivate() throws Exception {
        HostFixture fixture = new HostFixture();
        Host host = fixture.host;
        TestEntry agenda = fixture.entry("agenda");
        AtomicReference<CompletableFuture<Answer<Object>>> pending = new AtomicReference<>();
        agenda.onActivate = context -> {
            pending.set(context.want("absent", WantLimits.unbounded()).settlement());
            return pending.get();
        };
        agenda.onDeactivate = () -> {
            fixture.trace.add("want waiting at deactivate " + !pending.get().isDone());
            return CompletableFuture.completedFuture(null);
        };
        TestEntry flaky = fixture.entry("flaky");
        flaky.onActivate = context -> {
            throw new IllegalStateException("broken");
        };
        long flakyPlugin = fixture.register(named("flaky"), flaky);
        long agendaPlugin = fixture.register(consuming("agenda", "absent"), agenda);
        Activation activation = fixture.activate(flakyPlugin, agendaPlugin);
        assertEquals(ActivationState.QUARANTINED, done(activation.outcomes().get(0)).state());

        DestroyResult result = done(host.destroy());

        assertEquals(Arrays.asList(agendaPlugin, flakyPlugin), result.released());
        assertEquals(Arrays.asList("deactivate agenda", "want waiting at deactivate true", "deactivate flaky"),
                fixture.trace().subList(2, 5));
        assertEquals(ActivationState.RELEASED, done(activation.outcomes().get(1)).state());
        assertEquals(CoreConstants.REFUSAL_STOPPED, ((Refusal) assertInstanceOf(Answer.Refused.class,
                done(pending.get())).refusal().get()).code());
        host.close();
    }

    @Test
    void destroyCalledInsideADispatchRunsBehindWhatIsQueuedAlready() throws Exception {
        HostFixture fixture = new HostFixture();
        Host host = fixture.host;
        TestEntry watcher = fixture.entry("watcher");
        long plugin = fixture.register(consuming("watcher", "signal"), watcher);
        fixture.activate(plugin);
        AtomicReference<CompletionStage<DestroyResult>> destroyed = new AtomicReference<>();
        value(watcher.context.watch("signal", new ServiceWatcher() {
            @Override
            public void registered(Object service) {
                destroyed.set(host.destroy());
                fixture.trace.add("inside the dispatch, done " + destroyed.get().toCompletableFuture().isDone()
                        + ", deactivated " + fixture.trace.contains("deactivate watcher"));
            }

            @Override
            public void unregistered() {
            }
        }));

        value(host.registerHostService("signal", new Object()));

        assertEquals(Arrays.asList("activate watcher", "inside the dispatch, done false, deactivated false",
                "deactivate watcher"), fixture.trace());
        assertEquals(Collections.singletonList(plugin), done(destroyed.get()).released());
        host.close();
    }

    @Test
    void aPluginOfAPlanNotYetStartedWhenTheDestroyIsCalledIsNotOpenedAndIsAnsweredReleased() throws Exception {
        HostFixture fixture = new HostFixture();
        Host host = fixture.host;
        TestEntry destroying = fixture.entry("destroying");
        AtomicReference<CompletionStage<DestroyResult>> destroyed = new AtomicReference<>();
        destroying.onActivate = context -> {
            destroyed.set(host.destroy());
            return CompletableFuture.completedFuture(null);
        };
        long first = fixture.register(named("destroying"), destroying);
        long second = fixture.register(named("later"), fixture.entry("later"));

        Activation activation = fixture.activate(first, second);

        assertEquals(ActivationState.RELEASED, done(activation.outcomes().get(0)).state());
        assertEquals(ActivationState.RELEASED, done(activation.outcomes().get(1)).state());
        assertEquals(Arrays.asList("activate destroying", "deactivate destroying"), fixture.trace());
        assertEquals(Collections.singletonList(first), done(destroyed.get()).released());
        host.close();
    }

    @Test
    void aPluginOpenedAsTheDestroyBeganIsReleasedWithoutItsActivateBeingCalled() throws Exception {
        AtomicReference<Host> created = new AtomicReference<>();
        HostFixture fixture = new HostFixture((contexts, context, opened) -> {
            Answer<Void> answer = contexts.open(context, opened);
            created.get().destroy();
            return answer;
        });
        Host host = fixture.host;
        created.set(host);
        long plugin = fixture.register(named("opened"), fixture.entry("opened"));

        Activation activation = fixture.activate(plugin);

        assertEquals(ActivationState.RELEASED, done(activation.outcomes().get(0)).state());
        assertTrue(fixture.trace().isEmpty());
        assertTrue(done(host.destroy()).released().isEmpty());
        assertFalse(host.manifest(plugin).isPresent());
        host.close();
    }

    @Test
    void everyOtherCallIsQuietOnceTheDestroyIsCalledAndASecondDestroyAnswersTheFirst() throws Exception {
        HostFixture fixture = new HostFixture();
        Host host = fixture.host;
        TestEntry hanging = fixture.entry("hanging");
        CompletableFuture<Void> activated = new CompletableFuture<>();
        hanging.onActivate = context -> activated;
        long plugin = fixture.register(named("hanging"), hanging);
        fixture.activate(plugin);

        CompletionStage<DestroyResult> destroyed = host.destroy();
        int logged = fixture.logs.size();

        assertSame(destroyed, host.destroy());
        assertInstanceOf(Answer.Destroyed.class, host.verify(plugin));
        assertInstanceOf(Answer.Destroyed.class, host.register(named("late"), fixture.entry("late")));
        assertInstanceOf(Answer.Destroyed.class, host.activate(Collections.singletonList(plugin),
                EdgeSource.SERVICES));
        assertInstanceOf(Answer.Destroyed.class, done(host.release(plugin)));
        assertInstanceOf(Answer.Destroyed.class, host.registerHostService("late", new Object()));
        assertInstanceOf(Answer.Destroyed.class, host.loadEntry(Paths.get("absent.jar"), "absent.Entry"));
        activated.complete(null);
        assertEquals(logged, fixture.logs.size());
        assertTrue(fixture.failures.isEmpty());
        done(destroyed);
        host.close();
    }

    @Test
    void everyCallMadeWhileTheDestroyIsStillPendingIsAnsweredDestroyedBeforeAnyMistakeIsJudged() throws Exception {
        HostFixture fixture = new HostFixture();
        HostFixture elsewhere = new HostFixture();
        Host host = fixture.host;
        LoadedLibrary othersLibrary = value(elsewhere.host.loadLibrary(LibrariesTest.echoBinary(),
                LibrariesTest.declaring("elsewhere")));
        LoadedLibrary library = value(host.loadLibrary(LibrariesTest.echoBinary(), LibrariesTest.declaring("echo")));
        TestEntry slow = fixture.entry("slow");
        CompletableFuture<Void> deactivated = new CompletableFuture<>();
        slow.onDeactivate = () -> deactivated;
        long plugin = fixture.register(named("slow"), slow);
        fixture.activate(plugin);

        CompletionStage<DestroyResult> destroyed = host.destroy();
        int logged = fixture.logs.size();

        assertFalse(destroyed.toCompletableFuture().isDone(), "the destroy waits for the deactivate");
        assertSame(destroyed, host.destroy());
        assertInstanceOf(Answer.Destroyed.class, host.register(named("late"), fixture.entry("late")));
        assertInstanceOf(Answer.Destroyed.class, host.ledger());
        assertInstanceOf(Answer.Destroyed.class, host.registerHostService("late", new Object()));
        assertInstanceOf(Answer.Destroyed.class, host.loadLibrary(LibrariesTest.echoBinary(),
                LibrariesTest.declaring("late")));
        assertInstanceOf(Answer.Destroyed.class, host.loadLibrary(LibrariesTest.echoBinary(),
                LibrariesTest.declaring("twice", "twice")));
        assertInstanceOf(Answer.Destroyed.class, host.unloadLibrary(library));
        assertInstanceOf(Answer.Destroyed.class, host.unloadLibrary(othersLibrary));
        assertInstanceOf(Answer.Destroyed.class, host.loadEntry(Paths.get("absent.jar"), "absent.Entry"));
        assertInstanceOf(Answer.Destroyed.class, host.loadEntry(pathOfNoUrl(), "absent.Entry"));
        assertInstanceOf(Answer.Destroyed.class, host.activate(Collections.singletonList(plugin),
                EdgeSource.SERVICES));
        assertInstanceOf(Answer.Destroyed.class, host.verify(plugin));
        assertInstanceOf(Answer.Destroyed.class, done(host.release(plugin)));
        assertEquals(logged, fixture.logs.size());
        assertTrue(fixture.failures.isEmpty());
        deactivated.complete(null);
        assertEquals(Collections.singletonList(plugin), done(destroyed).released());
        host.close();
        elsewhere.close();
    }

    /** A path whose URI names a scheme no URL handler knows, so it is no URL. */
    private static Path pathOfNoUrl() {
        return (Path) Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[] {Path.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("toUri")) {
                        return URI.create("nowhere:plugin.jar");
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void closeIsRefusedUntilTheDestroyHasFinished() throws Exception {
        HostFixture fixture = new HostFixture();
        Host host = fixture.host;
        TestEntry slow = fixture.entry("slow");
        CompletableFuture<Void> deactivated = new CompletableFuture<>();
        slow.onDeactivate = () -> deactivated;
        fixture.activate(fixture.register(named("slow"), slow));
        assertThrows(IllegalStateException.class, host::close);

        CompletionStage<DestroyResult> destroyed = host.destroy();

        assertThrows(IllegalStateException.class, host::close);
        deactivated.complete(null);
        done(destroyed);
        host.close();
        host.close();
    }
}
