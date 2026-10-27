package io.github.intisy.ai.conformance;

import io.github.intisy.ai.engine.Diagnostics;
import io.github.intisy.ai.engine.ManifestFacts;
import io.github.intisy.ai.engine.PluginHost;
import io.github.intisy.ai.engine.PluginSession;
import io.github.intisy.ai.engine.ServiceHub;
import io.github.intisy.ai.engine.ServiceRefusal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code reach} op: which services a plugin's own session lets it reach.
 *
 * @implNote Driven through a {@link PluginHost}, never the hub, because the refusal lives in the
 * fence the host builds from each manifest. Whether a get or a watch was refused is read from the
 * ledger's structured refusals, never from diagnostic text, which only supplies {@code refusal}.
 */
final class Reach {

    /** What one step answers once the run is over, and the refusal text it produced, if any. */
    private interface StepAnswer {
        Object outcome();

        Map<String, Object> refusal();
    }

    private final PluginHost host = new PluginHost(null, new ArrayList<String>(), 1, new ArrayList<String>(),
        new ManualScheduler());
    private final List<String> diagnostics = new ArrayList<String>();
    private final Map<String, PluginSession> sessions = new LinkedHashMap<String, PluginSession>();

    private Reach() {
    }

    static Map<String, Object> run(Map<String, Object> given) {
        final Reach reach = new Reach();
        Diagnostics.setSink(new Diagnostics.Sink() {
            @Override
            public void accept(String message) {
                reach.diagnostics.add(message);
            }
        });
        try {
            return reach.answer(given);
        } finally {
            Diagnostics.setSink(null);
        }
    }

    private Map<String, Object> answer(Map<String, Object> given) {
        for (Object each : JavaDriver.list(given.get("plugins"))) {
            Map<String, Object> plugin = JavaDriver.map(each);
            String pluginId = String.valueOf(plugin.get("id"));
            ManifestFacts facts = new ManifestFacts(pluginId, 1, null, null, null)
                .withServices(JavaDriver.list(plugin.get("consumes")),JavaDriver.strings(plugin.get("provides")));
            sessions.put(pluginId, host.sessionFor(facts, JavaDriver.SILENT_BUS));
        }
        for (String id : JavaDriver.strings(given.get("hostProvided"))) {
            host.provideService(id, service(id));
        }
        List<StepAnswer> answers = new ArrayList<StepAnswer>();
        for (Object each : JavaDriver.list(given.get("steps"))) {
            StepAnswer answer = step(JavaDriver.map(each));
            if (answer != null) {
                answers.add(answer);
            }
        }
        List<Object> outcomes = new ArrayList<Object>();
        Map<String, Object> firstRefusal = null;
        for (StepAnswer answer : answers) {
            outcomes.add(answer.outcome());
            if (firstRefusal == null) {
                firstRefusal = answer.refusal();
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("outcomes", outcomes);
        result.put("reported", reported());
        result.put("refusal", firstRefusal);
        return result;
    }

    private StepAnswer step(Map<String, Object> step) {
        final String pluginId = String.valueOf(step.get("plugin"));
        PluginSession session = sessions.get(pluginId);
        if (session == null) {
            throw new IllegalArgumentException("step names plugin '" + pluginId + "', which the vector does not declare");
        }
        ServiceHub.Registry services = session.getServices();
        if (step.get("register") instanceof String) {
            String id = (String) step.get("register");
            services.register(id, service(id));
            return null;
        }
        if (step.get("get") instanceof String) {
            return get(pluginId, services, (String) step.get("get"));
        }
        if (step.get("want") instanceof String) {
            return want(services, (String) step.get("want"));
        }
        if (step.get("watch") instanceof String) {
            return watch(pluginId, services, (String) step.get("watch"));
        }
        if (Boolean.TRUE.equals(step.get("ids"))) {
            List<String> listed = new ArrayList<String>(services.ids());
            Collections.sort(listed);
            return answered(listed, null);
        }
        throw new IllegalArgumentException("unknown reach step " + step);
    }

    private StepAnswer get(String pluginId, ServiceHub.Registry services, String id) {
        int emitted = diagnostics.size();
        Object found = services.get(id);
        String outcome = found != null ? "served" : reported(pluginId, id) ? "refused" : "absent";
        return answered(outcome, messageAt(emitted));
    }

    private StepAnswer want(ServiceHub.Registry services, String id) {
        final Settlement settlement = new Settlement();
        services.want(id).then(settlement);
        return new StepAnswer() {
            @Override
            public Object outcome() {
                return settlement.value != null ? "served" : settlement.failure != null ? "refused" : "absent";
            }

            @Override
            public Map<String, Object> refusal() {
                if (settlement.failure == null) {
                    return null;
                }
                Map<String, Object> refusal = new LinkedHashMap<String, Object>();
                refusal.put("detail", settlement.failure.getDetail());
                refusal.put("fix", settlement.failure.getFix());
                return refusal;
            }
        };
    }

    private StepAnswer watch(final String pluginId, ServiceHub.Registry services, final String id) {
        int emitted = diagnostics.size();
        final boolean[] heard = new boolean[1];
        services.watch(id, new ServiceHub.Listener() {
            @Override
            public void changed(Object service, boolean registered) {
                if (registered) {
                    heard[0] = true;
                }
            }
        });
        final Map<String, Object> refusal = messageAt(emitted);
        return new StepAnswer() {
            @Override
            public Object outcome() {
                return heard[0] ? "served" : reported(pluginId, id) ? "refused" : "absent";
            }

            @Override
            public Map<String, Object> refusal() {
                return refusal;
            }
        };
    }

    private boolean reported(String pluginId, String serviceId) {
        for (ServiceRefusal refusal : host.getLedger().refusals()) {
            if (refusal.getPluginId().equals(pluginId) && refusal.getServiceId().equals(serviceId)) {
                return true;
            }
        }
        return false;
    }

    private List<List<String>> reported() {
        List<List<String>> pairs = new ArrayList<List<String>>();
        for (ServiceRefusal refusal : host.getLedger().refusals()) {
            pairs.add(Arrays.asList(refusal.getPluginId(), refusal.getServiceId()));
        }
        return pairs;
    }

    private Map<String, Object> messageAt(int index) {
        if (index >= diagnostics.size()) {
            return null;
        }
        Map<String, Object> refusal = new LinkedHashMap<String, Object>();
        refusal.put("message", diagnostics.get(index));
        return refusal;
    }

    private static StepAnswer answered(final Object outcome, final Map<String, Object> refusal) {
        return new StepAnswer() {
            @Override
            public Object outcome() {
                return outcome;
            }

            @Override
            public Map<String, Object> refusal() {
                return refusal;
            }
        };
    }

    private static ConformanceService service(final String id) {
        return new ConformanceService() {
            @Override
            public String name() {
                return id;
            }
        };
    }
}
