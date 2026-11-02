package io.github.intisy.ai.conformance;

import io.github.intisy.ai.api.ConfigSetOptions;
import io.github.intisy.ai.api.HostDescriptor;
import io.github.intisy.ai.api.PluginConfig;
import io.github.intisy.ai.api.PluginContext;
import io.github.intisy.ai.api.PluginPaths;
import io.github.intisy.ai.api.PluginSecrets;
import io.github.intisy.ai.api.SettingsLayer;
import io.github.intisy.ai.api.seam.Logger;
import io.github.intisy.ai.engine.ManifestFacts;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginHost;
import io.github.intisy.ai.engine.PluginSession;
import io.github.intisy.ai.seam.jvm.context.ContextRuntime;
import io.github.intisy.ai.seam.jvm.context.JvmPluginContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * The {@code context} op: which optional members a plugin's context carries, and what a settings
 * write to a named layer does.
 *
 * @implNote The answer is read off the CONTEXT {@link JvmPluginContext} built, never off the runtime
 * handed to it, because a member the builder dropped or invented is exactly what the op asks about.
 */
final class ContextOp {

    private static final String PLUGIN_ID = "config-ledger";

    private final List<Map<String, Object>> written = new ArrayList<Map<String, Object>>();

    private ContextOp() {
    }

    static Map<String, Object> run(Map<String, Object> given) {
        return new ContextOp().answer(JavaDriver.map(given.get("host")), JavaDriver.map(given.get("write")));
    }

    private Map<String, Object> answer(Map<String, Object> host, Map<String, Object> write) {
        PluginHost engine = new PluginHost(null, new ArrayList<String>(), 2, new ArrayList<String>());
        PluginSession session = engine.sessionFor(new ManifestFacts(PLUGIN_ID, 2, null, null, null), JavaDriver.SILENT_BUS);
        PluginContext context = JvmPluginContext.contextFor(session, HOST, runtimeFor(host));
        Map<String, Object> refusal = refusalOf(writeThrough(context.config(), write));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("portable", context.paths().portable());
        result.put("secrets", context.secrets() != null);
        result.put("written", written.isEmpty() ? null : written.get(0));
        result.put("refusal", refusal);
        return result;
    }

    private ContextRuntime runtimeFor(Map<String, Object> host) {
        Object portable = host.get("portable");
        ContextRuntime runtime = ContextRuntime.of(recordingConfig(), SILENT_LOG,
            pathsWith(portable instanceof String ? (String) portable : null));
        return Boolean.TRUE.equals(host.get("secrets")) ? runtime.withSecrets(UNUSED_SECRETS) : runtime;
    }

    private static CompletionStage<Void> writeThrough(PluginConfig config, Map<String, Object> write) {
        String key = String.valueOf(write.get("key"));
        Object value = write.get("value");
        Object layer = write.get("layer");
        if ("portable".equals(layer)) {
            return config.set(key, value, optionsFor(SettingsLayer.PORTABLE));
        }
        if ("application".equals(layer)) {
            return config.set(key, value, optionsFor(SettingsLayer.APPLICATION));
        }
        return config.set(key, value);
    }

    private static Map<String, Object> refusalOf(CompletionStage<Void> writing) {
        try {
            writing.toCompletableFuture().get();
            return null;
        } catch (ExecutionException failure) {
            return describe(failure.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return describe(interrupted);
        }
    }

    private static Map<String, Object> describe(Throwable failure) {
        Map<String, Object> refusal = new LinkedHashMap<String, Object>();
        if (failure instanceof PluginException) {
            refusal.put("detail", ((PluginException) failure).getDetail());
            refusal.put("fix", ((PluginException) failure).getFix());
        } else {
            refusal.put("message", String.valueOf(failure));
        }
        return refusal;
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
                return record(key, null);
            }

            @Override
            public CompletionStage<Void> set(String key, Object value, ConfigSetOptions options) {
                SettingsLayer layer = options == null ? null : options.layer();
                return record(key, layer == null ? null : layer == SettingsLayer.PORTABLE ? "portable" : "application");
            }
        };
    }

    private CompletionStage<Void> record(String key, String layer) {
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("key", key);
        entry.put("layer", layer);
        written.add(entry);
        return CompletableFuture.completedFuture(null);
    }

    private static ConfigSetOptions optionsFor(final SettingsLayer layer) {
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
                return "";
            }

            @Override
            public String repos() {
                return "";
            }

            @Override
            public String plugin() {
                return "";
            }

            @Override
            public String cache() {
                return "";
            }

            @Override
            public String config() {
                return "";
            }

            @Override
            public String portable() {
                return portable;
            }
        };
    }

    private static final HostDescriptor HOST = new HostDescriptor() {
        @Override
        public String app() {
            return "";
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

    private static final PluginSecrets UNUSED_SECRETS = new PluginSecrets() {
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
    };

    private static final Logger SILENT_LOG = new Logger() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Object cause) {
        }
    };
}
