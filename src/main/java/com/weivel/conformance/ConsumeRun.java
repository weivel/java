package com.weivel.conformance;

import com.weivel.context.CapabilityWatcher;
import com.weivel.context.PluginContext;
import com.weivel.context.PluginEvent;
import com.weivel.core.Answer;
import com.weivel.core.LedgerRow;
import com.weivel.core.Refusal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Drives a {@code consume} vector: its plugins built through the host and opened with no quarantine of the driver's
 * making, then its steps in order, each through the plugin's context or the host.
 */
final class ConsumeRun {
    private static final String WATCH = "watch";
    private static final String PROVIDE = "provide";
    private static final String VERIFY = "verify";
    private static final String RELEASE = "release";

    private final DriverWorld world;
    private final List<List<Object>> heard = new ArrayList<>();
    private final List<Object> reported = new ArrayList<>();
    private final List<Refusal> refusals = new ArrayList<>();

    private ConsumeRun(DriverWorld world) {
        this.world = world;
    }

    static Map<String, Observations.Observer> drive(LayerVector vector, DriverWorld world) {
        String where = "the vector " + vector.id() + "'s given";
        Shapes.refuseUnknownKeys(where, vector.given(), "plugins", "steps");
        ConsumeRun run = new ConsumeRun(world);
        run.build(where, Shapes.objects(where, vector.given(), "plugins"));
        List<Map<String, Object>> steps = Shapes.objects(where, vector.given(), "steps");
        for (int index = 0; index < steps.size(); index++) {
            run.perform(where + "'s step " + index, steps.get(index));
        }
        return run.observers();
    }

    private void build(String where, List<Map<String, Object>> plugins) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> plugin : plugins) {
            String id = Shapes.text(where, plugin, "id");
            String pluginWhere = where + "'s plugin " + id;
            Shapes.refuseUnknownKeys(pluginWhere, plugin, "id", "capabilities");
            Map<String, Object> capabilities = Shapes.object(pluginWhere, plugin, "capabilities");
            Shapes.refuseUnknownKeys(pluginWhere + "'s capabilities", capabilities, "provides", "consumes");
            world.register(Manifests.capabilities(id, Shapes.texts(pluginWhere, capabilities, "provides"),
                    Shapes.texts(pluginWhere, capabilities, "consumes")), DriverWorld.inFlight());
            names.add(id);
        }
        world.openInFlight(names);
        for (String name : names) {
            world.context(name).listen(event -> {
                if (event instanceof PluginEvent.ReachRefused) {
                    reported.add(Arrays.<Object>asList(name, ((PluginEvent.ReachRefused) event).id()));
                }
            });
        }
    }

    private void perform(String where, Map<String, Object> step) {
        String action = Shapes.theOneNamed(where, step, WATCH, PROVIDE, VERIFY, RELEASE);
        if (action.equals(WATCH)) {
            Shapes.refuseUnknownKeys(where, step, "plugin", WATCH, "throws");
        } else {
            Shapes.refuseUnknownKeys(where, step, "plugin", action);
        }
        String plugin = Shapes.text(where, step, "plugin");
        if (action.equals(WATCH)) {
            answered(watch(plugin, Shapes.text(where, step, WATCH), Shapes.flag(where, step, "throws", false)));
        } else if (action.equals(PROVIDE)) {
            answered(world.context(plugin).provide(Shapes.text(where, step, PROVIDE), new Implementation(plugin)));
        } else if (action.equals(VERIFY)) {
            answered(world.host.verify(world.plugin(plugin)));
        } else {
            CompletableFuture<Answer<Boolean>> release = world.host.release(world.plugin(plugin))
                    .toCompletableFuture();
            if (!release.isDone()) {
                throw new IllegalStateException("the release of " + plugin + " left something to run");
            }
            answered(release.join());
        }
    }

    /** A throwing watch records what it was handed, then throws on that call and every other. */
    private Answer<?> watch(String plugin, String capability, boolean throwing) {
        List<Object> changes = new ArrayList<>();
        heard.add(changes);
        PluginContext context = world.context(plugin);
        return context.watchCapability(capability, new CapabilityWatcher() {
            @Override
            public void provided(Object implementation) {
                hear(implementation, PROVIDE);
            }

            @Override
            public void withdrawn(Object implementation) {
                hear(implementation, "withdraw");
            }

            private void hear(Object implementation, String change) {
                String provider = ((Implementation) implementation).provider;
                changes.add(Arrays.<Object>asList(provider, change));
                if (throwing) {
                    throw new IllegalStateException("the throwing listener of " + capability + " was handed "
                            + provider + " " + change);
                }
            }
        });
    }

    /** Keeps the core's refusal of a step, and fails the run on an answer no well-made step can have. */
    private void answered(Answer<?> answer) {
        if (answer instanceof Answer.Refused) {
            refusals.add(KitRefusals.refusalOf(answer).orElseThrow(() ->
                    new IllegalStateException("the core refused a step and queued no refusal: " + answer)));
        } else if (!(answer instanceof Answer.Ok)) {
            throw new IllegalStateException("a step answered " + answer);
        }
    }

    private List<LedgerRow> quarantinedRows() {
        Answer<List<LedgerRow>> ledger = world.host.ledger();
        if (!(ledger instanceof Answer.Ok)) {
            throw new IllegalStateException("the host answered no ledger: " + ledger);
        }
        List<LedgerRow> quarantined = new ArrayList<>();
        for (LedgerRow row : ((Answer.Ok<List<LedgerRow>>) ledger).value()) {
            if (row.status() == LedgerRow.Status.QUARANTINED) {
                quarantined.add(row);
            }
        }
        return quarantined;
    }

    private Map<String, Observations.Observer> observers() {
        Map<String, Observations.Observer> observers = new HashMap<>();
        observers.put("heard", expectation -> copies(heard));
        observers.put("reported", expectation -> Collections.unmodifiableList(new ArrayList<>(reported)));
        observers.put("refusal", expectation -> refusals.isEmpty()
                ? StandardJson.NULL
                : KitRefusals.kitRefusal(refusals.get(0)));
        observers.put("quarantined", expectation -> {
            List<Object> names = new ArrayList<>();
            for (LedgerRow row : quarantinedRows()) {
                names.add(row.importName());
            }
            return names;
        });
        observers.put("quarantineReason", expectation -> {
            List<LedgerRow> quarantined = quarantinedRows();
            if (quarantined.isEmpty() || !quarantined.get(0).reason().isPresent()) {
                return StandardJson.NULL;
            }
            return KitRefusals.kitRefusal(quarantined.get(0).reason().get());
        });
        return observers;
    }

    private static List<Object> copies(List<List<Object>> lists) {
        List<Object> copied = new ArrayList<>();
        for (List<Object> list : lists) {
            copied.add(new ArrayList<>(list));
        }
        return copied;
    }

    /** The object a plugin provides: the kit names an implementation by its provider. */
    private static final class Implementation {
        private final String provider;

        private Implementation(String provider) {
            this.provider = provider;
        }
    }
}
