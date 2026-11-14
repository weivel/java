package com.weivel.context;

import static com.weivel.context.ContextFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.Choice;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreProbes;
import com.weivel.core.Manifest;
import com.weivel.core.NamedNumber;
import com.weivel.handles.HandlesProbes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Contexts under several threads. A context operation takes the layer lock, through the wrapper, and inside it the
 * table's own lock, never the other way, and no callback of a plugin's or of the host's runs under either.
 */
class ContextThreadsTest {
    private static final long GATE_SECONDS = 20;

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void noCallbackOfAPluginsOrOfTheHostsRunsUnderAnyLockOfTheLayers() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY)) {
            List<String> locked = Collections.synchronizedList(new ArrayList<>());
            List<String> called = Collections.synchronizedList(new ArrayList<>());
            Runnable noting = () -> { };
            fixture.onReport = () -> note(fixture, "sink", called, locked);
            fixture.onLog = () -> note(fixture, "log", called, locked);
            PluginContext provider = fixture.opened(new Manifest("provider", 0, Optional.empty(),
                    Collections.emptyList(),
                    Collections.singletonList("screens"), Collections.emptyList(),
                            Collections.singletonList(new Manifest.ServiceOffer("provider:measured",
                    Collections.singletonList(new NamedNumber("latency", 50)))), Collections.emptyList(),
                            Collections.emptyList(), Optional.empty()));
            Manifest.ServiceWant measured = new Manifest.ServiceWant("provider:measured", Optional.empty(),
                    Choice.YES, Choice.NO, Collections.singletonList(new NamedNumber("latency", 10)));
            PluginContext consumer = fixture.opened(new Manifest("consumer", 0, Optional.empty(),
                    Collections.emptyList(),
                    Collections.emptyList(), Collections.singletonList("screens"), Collections.emptyList(),
                            Collections.singletonList(measured), Collections.emptyList(), Optional.empty()));
            consumer.listen(event -> note(fixture, "listener", called, locked));
            value(consumer.watch("provider:measured", new ServiceWatcher() {
                @Override
                public void registered(Object service) {
                    note(fixture, "registered", called, locked);
                    throw new IllegalStateException("a watcher failing so the sink is told");
                }

                @Override
                public void unregistered() {
                    note(fixture, "unregistered", called, locked);
                }
            }));
            value(consumer.watchCapability("screens", new CapabilityWatcher() {
                @Override
                public void provided(Object implementation) {
                    note(fixture, "provided", called, locked);
                }

                @Override
                public void withdrawn(Object implementation) {
                    note(fixture, "withdrawn", called, locked);
                }
            }));
            value(provider.register("provider:measured", new Object(), new CapacityAnswerer() {
                @Override
                public List<NamedNumber> attributes() {
                    note(fixture, "attributes", called, locked);
                    return Collections.singletonList(new NamedNumber("latency", 5));
                }

                @Override
                public boolean serves(List<NamedNumber> constraints) {
                    note(fixture, "serves", called, locked);
                    return true;
                }
            }));
            value(provider.provide("screens", new Object()));
            fixture.verify(provider);
            consumer.want("provider:measured", WantLimits.polled(100)).settlement()
                    .thenRun(() -> note(fixture, "settled", called, locked));
            consumer.get("undeclared");
            consumer.log().write(CoreConstants.LOG_INFO, "logged");
            fixture.release(provider);
            noting.run();

            assertTrue(called.containsAll(Arrays.asList("sink", "log", "listener", "registered", "unregistered",
                    "provided", "withdrawn", "attributes", "serves", "settled")), called.toString());
            assertEquals(Collections.emptyList(), locked);
        }
    }

    @Test
    @Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void pluginsOnSeveralThreadsWantingAndWatchingEachOthersServicesSettleEveryWantOnceWithTheRightObject()
            throws Exception {
        int pluginCount = 6;
        try (ContextFixture fixture = new ContextFixture()) {
            List<String> ids = IntStream.range(0, pluginCount).mapToObj(index -> "plugin" + index + ":service")
                    .collect(Collectors.toList());
            List<PluginContext> plugins = new ArrayList<>();
            for (int index = 0; index < pluginCount; index++) {
                plugins.add(fixture.opened(new Manifest("plugin" + index, 0, Optional.empty(),
                        Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), Collections.singletonList(new Manifest.ServiceOffer(ids.get(index),
                                Collections.emptyList())),
                        ids.stream().map(ContextFixture::wanted).collect(Collectors.toList()),
                                Collections.emptyList(), Optional.empty())));
            }
            Map<String, Object> objects = new ConcurrentHashMap<>();
            Map<String, CompletableFuture<Answer<Object>>> settlements = new ConcurrentHashMap<>();
            Map<String, AtomicInteger> completions = new ConcurrentHashMap<>();
            Map<String, List<Object>> watched = new ConcurrentHashMap<>();
            CyclicBarrier start = new CyclicBarrier(pluginCount);
            ExecutorService threads = Executors.newFixedThreadPool(pluginCount);
            try {
                List<CompletableFuture<Void>> running = new ArrayList<>();
                for (int index = 0; index < pluginCount; index++) {
                    int own = index;
                    running.add(CompletableFuture.runAsync(() -> {
                        awaitAll(start);
                        PluginContext context = plugins.get(own);
                        for (int other = 0; other < pluginCount; other++) {
                            String key = own + "->" + other;
                            List<Object> heard = Collections.synchronizedList(new ArrayList<>());
                            watched.put(key, heard);
                            value(context.watch(ids.get(other), ServicesTest.recording(heard)));
                            CompletableFuture<Answer<Object>> settlement = context.want(ids.get(other),
                                    WantLimits.unbounded()).settlement();
                            AtomicInteger count = new AtomicInteger();
                            completions.put(key, count);
                            settlement.thenRun(count::incrementAndGet);
                            settlements.put(key, settlement);
                        }
                        Object object = new Object();
                        objects.put(ids.get(own), object);
                        value(context.register(ids.get(own), object));
                    }, threads));
                }
                CompletableFuture.allOf(running.toArray(new CompletableFuture[0])).get(100, TimeUnit.SECONDS);
            } finally {
                threads.shutdownNow();
            }

            assertEquals(pluginCount * pluginCount, settlements.size());
            settlements.forEach((key, settlement) -> {
                int other = Integer.parseInt(key.substring(key.indexOf("->") + 2));
                assertSame(objects.get(ids.get(other)), value(settlement.getNow(null)), key);
                assertEquals(1, completions.get(key).get(), key);
            });
            assertEquals(Collections.emptyList(), fixture.failures);
        }
    }

    private static void note(ContextFixture fixture, String what, List<String> called, List<String> locked) {
        called.add(what);
        if (CoreProbes.isLockHeldByCurrentThread(fixture.core)
                || HandlesProbes.isLockHeldByCurrentThread(fixture.handles)) {
            locked.add(what);
        }
    }

    private static void awaitAll(CyclicBarrier barrier) {
        try {
            barrier.await(GATE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception interrupted) {
            throw new IllegalStateException(interrupted);
        }
    }
}
