package io.github.intisy.ai.seam.jvm.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.intisy.ai.api.ConfigSetOptions;
import io.github.intisy.ai.api.HostDescriptor;
import io.github.intisy.ai.api.PluginConfig;
import io.github.intisy.ai.api.PluginContext;
import io.github.intisy.ai.api.PluginPaths;
import io.github.intisy.ai.api.PluginSecrets;
import io.github.intisy.ai.api.ServiceType;
import io.github.intisy.ai.api.SettingsLayer;
import io.github.intisy.ai.engine.EventBus;
import io.github.intisy.ai.engine.ManifestFacts;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginHost;
import io.github.intisy.ai.engine.Scheduler;
import io.github.intisy.ai.seam.jvm.SimpleLoggerAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class JvmPluginContextTest {

    private static final EventBus QUIET_BUS = new EventBus() {
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
    };

    private static final HostDescriptor HOST = new HostDescriptor() {
        @Override
        public String app() {
            return "claude";
        }

        @Override
        public int api() {
            return 2;
        }

        @Override
        public List<String> surfaces() {
            return Collections.emptyList();
        }
    };

    private final List<String> layersWritten = new ArrayList<String>();

    @Test
    void leavesSecretsAndThePortablePathAbsentOnAHostThatOffersNeither() {
        PluginContext context = contextOver(ContextRuntime.of(recordingConfig(), new SimpleLoggerAdapter(), pathsWith(null)));
        assertNull(context.secrets());
        assertNull(context.paths().portable());
    }

    @Test
    void handsOverTheRuntimesSecretsAndPathsByIdentity() {
        PluginSecrets secrets = new NoSecrets();
        PluginPaths paths = pathsWith("/portable/a");
        PluginContext context = contextOver(
            ContextRuntime.of(recordingConfig(), new SimpleLoggerAdapter(), paths).withSecrets(secrets));
        assertSame(secrets, context.secrets());
        assertSame(paths, context.paths());
    }

    @Test
    void refusesAPortableWriteWithoutTheLayerAndHandsTheRuntimeNothing() throws Exception {
        PluginConfig config = contextOver(ContextRuntime.of(recordingConfig(), new SimpleLoggerAdapter(), pathsWith(null))).config();
        ExecutionException refused = assertThrows(ExecutionException.class,
            () -> config.set("theme", "dark", layer(SettingsLayer.PORTABLE)).toCompletableFuture().get());
        PluginException refusal = assertInstanceOf(PluginException.class, refused.getCause());
        assertEquals("this host has no portable settings layer", refusal.getDetail());
        config.set("theme", "dark").toCompletableFuture().get();
        config.set("theme", "dark", layer(SettingsLayer.APPLICATION)).toCompletableFuture().get();
        assertEquals(Arrays.asList("none", "APPLICATION"), layersWritten);
    }

    @Test
    void passesAPortableWriteThroughOnAHostWithTheLayer() throws Exception {
        PluginConfig config = contextOver(
            ContextRuntime.of(recordingConfig(), new SimpleLoggerAdapter(), pathsWith("/portable/a"))).config();
        config.set("theme", "dark", layer(SettingsLayer.PORTABLE)).toCompletableFuture().get();
        assertEquals(Collections.singletonList("PORTABLE"), layersWritten);
    }

    @Test
    void reachesTheSessionsRegistryThroughATypedKey() throws Exception {
        PluginContext context = contextOver(ContextRuntime.of(recordingConfig(), new SimpleLoggerAdapter(), pathsWith(null)));
        ServiceType<String> greeting = context.service("config-ledger:greeting");
        CompletionStage<String> wanted = context.services().want(greeting);
        context.services().register(greeting, "hello");
        assertEquals("hello", context.services().get(greeting));
        assertEquals("hello", wanted.toCompletableFuture().get());
    }

    private PluginContext contextOver(ContextRuntime runtime) {
        PluginHost host = new PluginHost(null, new ArrayList<String>(), 2, new ArrayList<String>());
        ManifestFacts facts = new ManifestFacts("config-ledger", 2, null, null, null)
            .withServices(new ArrayList<Object>(), Collections.singletonList("config-ledger:greeting"));
        return JvmPluginContext.contextFor(host.sessionFor(facts, QUIET_BUS), HOST, runtime);
    }

    private PluginConfig recordingConfig() {
        return new PluginConfig() {
            @Override
            public Map<String, Object> all() {
                return Collections.emptyMap();
            }

            @Override
            public <T> T get(String key) {
                return null;
            }

            @Override
            public CompletionStage<Void> set(String key, Object value) {
                layersWritten.add("none");
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> set(String key, Object value, ConfigSetOptions options) {
                layersWritten.add(String.valueOf(options.layer()));
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    private static ConfigSetOptions layer(final SettingsLayer layer) {
        return new ConfigSetOptions() {
            @Override
            public SettingsLayer layer() {
                return layer;
            }
        };
    }

    private static PluginPaths pathsWith(final String portable) {
        return new PluginPaths() {
            @Override
            public String home() {
                return "/home";
            }

            @Override
            public String repos() {
                return "/repos";
            }

            @Override
            public String plugin() {
                return "/plugin";
            }

            @Override
            public String cache() {
                return "/cache";
            }

            @Override
            public String config() {
                return "/config";
            }

            @Override
            public String portable() {
                return portable;
            }
        };
    }

    private static final class NoSecrets implements PluginSecrets {
        @Override
        public CompletionStage<String> get(String name) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> set(String name, String value) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> delete(String name) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
