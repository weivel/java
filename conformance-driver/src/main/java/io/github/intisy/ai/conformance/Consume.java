package io.github.intisy.ai.conformance;

import io.github.intisy.ai.engine.ConsumedCapabilities;
import io.github.intisy.ai.engine.DeclarationKind;
import io.github.intisy.ai.engine.Diagnostics;
import io.github.intisy.ai.engine.ManifestFacts;
import io.github.intisy.ai.engine.PluginException;
import io.github.intisy.ai.engine.PluginHost;
import io.github.intisy.ai.engine.PluginSession;
import io.github.intisy.ai.engine.ServiceRefusal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code consume} op: which verified implementations of a capability a plugin's own session
 * answers it.
 *
 * @implNote Driven through a {@link PluginHost}, never a bare registry, because the fence and the
 * verified boundary both live in the host. A refused activation is the engine's own: {@code verify}
 * runs {@link PluginHost#verifyActivation} and quarantines on its refusal, as a host would. The
 * provider ids answered are read off the objects the engine handed back, never off the steps run.
 */
final class Consume {

    /** What a {@code provide} step hands the engine: an implementation that names its provider. */
    static final class Implementation {
        private final String providerId;

        Implementation(String providerId) {
            this.providerId = providerId;
        }

        String providerId() {
            return providerId;
        }
    }

    private final PluginHost host = new PluginHost(null, new ArrayList<String>(), 1, new ArrayList<String>(),
        new ManualScheduler());
    private final List<String> diagnostics = new ArrayList<String>();
    private final Map<String, PluginSession> sessions = new LinkedHashMap<String, PluginSession>();
    private final List<Object> outcomes = new ArrayList<Object>();
    private final List<List<List<String>>> heard = new ArrayList<List<List<String>>>();
    private Map<String, Object> firstRefusal;

    private Consume() {
    }

    static Map<String, Object> run(Map<String, Object> given) {
        final Consume consume = new Consume();
        Diagnostics.setSink(new Diagnostics.Sink() {
            @Override
            public void accept(String message) {
                consume.diagnostics.add(message);
            }
        });
        try {
            return consume.answer(given);
        } finally {
            Diagnostics.setSink(null);
        }
    }

    private Map<String, Object> answer(Map<String, Object> given) {
        for (Object each : JavaDriver.list(given.get("plugins"))) {
            Map<String, Object> plugin = JavaDriver.map(each);
            String pluginId = String.valueOf(plugin.get("id"));
            Map<String, Object> capabilities = plugin.get("capabilities") instanceof Map
                ? JavaDriver.map(plugin.get("capabilities"))
                : new LinkedHashMap<String, Object>();
            ManifestFacts facts = new ManifestFacts(pluginId, 1, JavaDriver.strings(capabilities.get("provides")), null, null)
                .withCapabilitiesConsumed(JavaDriver.strings(capabilities.get("consumes")));
            sessions.put(pluginId, host.sessionFor(facts, JavaDriver.SILENT_BUS));
        }
        for (Object each : JavaDriver.list(given.get("steps"))) {
            step(JavaDriver.map(each));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("outcomes", outcomes);
        result.put("heard", heard);
        result.put("reported", reported());
        result.put("refusal", firstRefusal);
        return result;
    }

    private void step(Map<String, Object> step) {
        String pluginId = String.valueOf(step.get("plugin"));
        PluginSession session = sessions.get(pluginId);
        if (session == null) {
            throw new IllegalArgumentException("step names plugin '" + pluginId + "', which the vector does not declare");
        }
        if (step.get("provide") instanceof String) {
            session.provide((String) step.get("provide"), new Implementation(pluginId));
        } else if (Boolean.TRUE.equals(step.get("verify"))) {
            verify(session);
        } else if (step.get("all") instanceof String) {
            all(session.getCapabilities(), (String) step.get("all"));
        } else if (step.get("watch") instanceof String) {
            watch(session.getCapabilities(), (String) step.get("watch"), Boolean.TRUE.equals(step.get("throws")));
        } else if (Boolean.TRUE.equals(step.get("quarantine"))) {
            host.markBroken(pluginId, new PluginException(pluginId, "failed at run time", "fix the plugin"));
        } else if (Boolean.TRUE.equals(step.get("release"))) {
            host.release(pluginId);
        } else {
            throw new IllegalArgumentException("unknown consume step " + step);
        }
    }

    private void verify(PluginSession session) {
        ManifestFacts facts = session.getFacts();
        PluginException refusal = host.verifyActivation(facts);
        if (refusal != null) {
            host.markBroken(facts.getId(), refusal);
        }
    }

    private void all(ConsumedCapabilities capabilities, String id) {
        int emitted = diagnostics.size();
        List<String> providers = new ArrayList<String>();
        for (Object implementation : capabilities.all(id)) {
            providers.add(((Implementation) implementation).providerId());
        }
        outcomes.add(providers);
        noteRefusal(emitted);
    }

    private void watch(ConsumedCapabilities capabilities, String id, final boolean throwing) {
        int emitted = diagnostics.size();
        final List<List<String>> events = new ArrayList<List<String>>();
        heard.add(events);
        capabilities.watch(id, new ConsumedCapabilities.Listener() {
            @Override
            public void changed(Object implementation, boolean provided) {
                events.add(Arrays.asList(((Implementation) implementation).providerId(), provided ? "provide" : "withdraw"));
                if (throwing) {
                    throw new IllegalStateException("this listener throws on every call");
                }
            }
        });
        noteRefusal(emitted);
    }

    private void noteRefusal(int emitted) {
        if (firstRefusal != null || emitted >= diagnostics.size()) {
            return;
        }
        firstRefusal = new LinkedHashMap<String, Object>();
        firstRefusal.put("message", diagnostics.get(emitted));
    }

    private List<List<String>> reported() {
        List<List<String>> pairs = new ArrayList<List<String>>();
        for (ServiceRefusal refusal : host.getLedger().refusals()) {
            if (refusal.getKind() == DeclarationKind.CAPABILITY) {
                pairs.add(Arrays.asList(refusal.getPluginId(), refusal.getServiceId()));
            }
        }
        return pairs;
    }
}
