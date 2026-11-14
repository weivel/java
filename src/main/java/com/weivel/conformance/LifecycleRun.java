package com.weivel.conformance;

import com.weivel.context.PluginContext;
import com.weivel.context.WantLimits;
import com.weivel.core.Answer;
import com.weivel.core.EdgeSource;
import com.weivel.core.LedgerRow;
import com.weivel.core.Refusal;
import com.weivel.host.Activation;
import com.weivel.host.Deactivating;
import com.weivel.host.Entry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Drives a {@code lifecycle} vector: each plugin's entry runs the actions the vector gives it and records them, and
 * the host's steps run in order through its public surface. Every call runs on the suite's thread, so once a step
 * returns the layer has nothing left to run but what waits on a want no step can settle.
 */
final class LifecycleRun {
    private static final String REGISTER = "register";
    private static final String WANT = "want";
    private static final String GET = "get";

    private final DriverWorld world;
    private final List<String> trace = Collections.synchronizedList(new ArrayList<String>());
    private final List<Object> plans = new ArrayList<>();
    private final List<Object> verifies = new ArrayList<>();
    private final TreeSet<String> pending = new TreeSet<>();
    private final Map<String, PluginContext> contexts = new HashMap<>();
    private boolean destroying;
    private int loudBeforeTheDestroy;
    private int thrownSinceTheDestroy;

    private LifecycleRun(DriverWorld world) {
        this.world = world;
    }

    static Map<String, Observations.Observer> drive(LayerVector vector, DriverWorld world) {
        String where = "the vector " + vector.id() + "'s given";
        Map<String, Object> given = vector.given();
        Shapes.refuseUnknownKeys(where, given, "edgeSource", "plugins", "steps");
        EdgeSource source = edgeSource(where, Shapes.text(where, given, "edgeSource"));
        LifecycleRun run = new LifecycleRun(world);
        for (Map<String, Object> plugin : Shapes.objects(where, given, "plugins")) {
            run.build(where, plugin);
        }
        List<Map<String, Object>> steps = Shapes.objects(where, given, "steps");
        for (int index = 0; index < steps.size(); index++) {
            run.step(where + "'s step " + index, steps.get(index), source);
        }
        return run.observers();
    }

    private static EdgeSource edgeSource(String where, String named) {
        if (named.equals("capabilities")) {
            return EdgeSource.CAPABILITIES;
        }
        if (named.equals("services")) {
            return EdgeSource.SERVICES;
        }
        throw new IllegalArgumentException(where + " names the edge source " + named + ", which no driver knows");
    }

    private void build(String where, Map<String, Object> plugin) {
        String id = Shapes.text(where, plugin, "id");
        String pluginWhere = where + "'s plugin " + id;
        Shapes.refuseUnknownKeys(pluginWhere, plugin, "id", "provides", "consumes", "activate", "deactivate");
        List<Map<String, Object>> activate = actions(pluginWhere + "'s activate", plugin, "activate");
        Optional<List<Map<String, Object>>> deactivate = plugin.containsKey("deactivate")
                ? Optional.of(actions(pluginWhere + "'s deactivate", plugin, "deactivate"))
                : Optional.<List<Map<String, Object>>>empty();
        world.register(Manifests.services(id, Shapes.texts(pluginWhere, plugin, "provides"),
                Shapes.texts(pluginWhere, plugin, "consumes")), entryOf(id, activate, deactivate));
    }

    private static List<Map<String, Object>> actions(String where, Map<String, Object> plugin, String key) {
        List<Map<String, Object>> actions = Shapes.objects(where, plugin, key);
        for (int index = 0; index < actions.size(); index++) {
            String actionWhere = where + " action " + index;
            Map<String, Object> action = actions.get(index);
            String named = Shapes.theOneNamed(actionWhere, action, REGISTER, WANT, GET);
            if (named.equals(WANT)) {
                Shapes.refuseUnknownKeys(actionWhere, action, WANT, "await");
            } else {
                Shapes.refuseUnknownKeys(actionWhere, action, named);
            }
            Shapes.text(actionWhere, action, named);
        }
        return actions;
    }

    private Entry entryOf(String name, List<Map<String, Object>> activate,
                          Optional<List<Map<String, Object>>> deactivate) {
        Entry activating = context -> {
            trace.add("activate " + name);
            contexts.put(name, context);
            return runActions(name, context, activate, 0);
        };
        if (!deactivate.isPresent()) {
            return activating;
        }
        return new DeactivatingEntry(activating, () -> {
            trace.add("deactivate " + name);
            return runActions(name, contextOf(name), deactivate.get(), 0);
        });
    }

    /** Runs {@code actions} from {@code index} on, waiting only where an action waits for its want to end. */
    private CompletionStage<Void> runActions(String name, PluginContext context, List<Map<String, Object>> actions,
                                             int index) {
        for (int next = index; next < actions.size(); next++) {
            Map<String, Object> action = actions.get(next);
            if (action.containsKey(REGISTER)) {
                register(name, context, (String) action.get(REGISTER));
            } else if (action.containsKey(WANT)) {
                CompletableFuture<Void> ended = want(name, context, (String) action.get(WANT));
                if (Shapes.flag("an action", action, "await", true)) {
                    int after = next + 1;
                    return ended.thenCompose(nothing -> runActions(name, context, actions, after));
                }
            } else {
                get(name, context, (String) action.get(GET));
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private void register(String name, PluginContext context, String id) {
        Answer<?> registered = context.register(id, new Object());
        if (!(registered instanceof Answer.Ok)) {
            throw new IllegalStateException(name + "'s registration of " + id + " answered " + registered);
        }
        trace.add("registered " + name + " " + id);
    }

    private CompletableFuture<Void> want(String name, PluginContext context, String id) {
        String wanted = name + " " + id;
        pending.add(wanted);
        return context.want(id, WantLimits.unbounded()).settlement().thenAccept(settlement -> {
            pending.remove(wanted);
            trace.add(settlement instanceof Answer.Ok
                    ? "settled " + wanted
                    : "rejected " + wanted + " " + rejection(settlement));
        });
    }

    private static String rejection(Answer<Object> settlement) {
        Optional<Refusal> refusal = KitRefusals.refusalOf(settlement);
        return refusal.isPresent() ? KitRefusals.codeName(refusal.get().code()) : settlement.toString();
    }

    /** Traces a get that answered no object, absent or refused, as missed. */
    private void get(String name, PluginContext context, String id) {
        trace.add((context.get(id) instanceof Answer.Ok ? "got " : "missed ") + name + " " + id);
    }

    private PluginContext contextOf(String name) {
        PluginContext context = contexts.get(name);
        if (context == null) {
            throw new IllegalStateException("a step names " + name + ", whose activate was never called");
        }
        return context;
    }

    private void step(String where, Map<String, Object> step, EdgeSource source) {
        String named = Shapes.theOneNamed(where, step, "activate", "release", "destroy", "verify", GET);
        Shapes.refuseUnknownKeys(where, step, named.equals(GET) ? new String[] {"plugin", GET} : new String[] {named});
        try {
            perform(where, named, step, source);
        } catch (RuntimeException thrown) {
            if (!destroying) {
                throw thrown;
            }
            thrownSinceTheDestroy++;
        }
    }

    private void perform(String where, String named, Map<String, Object> step, EdgeSource source) {
        if (named.equals("activate")) {
            activate(Shapes.texts(where, step, "activate"), source);
        } else if (named.equals("release")) {
            world.host.release(world.plugin(Shapes.text(where, step, "release")));
        } else if (named.equals("destroy")) {
            destroying = true;
            loudBeforeTheDestroy = world.written(true);
            world.host.destroy();
        } else if (named.equals("verify")) {
            verify(Shapes.text(where, step, "verify"));
        } else {
            String plugin = Shapes.text(where, step, "plugin");
            get(plugin, contextOf(plugin), Shapes.text(where, step, GET));
        }
    }

    private void activate(List<String> names, EdgeSource source) {
        List<Long> handles = new ArrayList<>();
        for (String name : names) {
            handles.add(world.plugin(name));
        }
        Answer<Activation> activated = world.host.activate(handles, source);
        if (!(activated instanceof Answer.Ok)) {
            throw new IllegalStateException("the host answered the activation " + activated);
        }
        List<Object> plan = new ArrayList<>();
        for (long plugin : ((Answer.Ok<Activation>) activated).value().plan().order()) {
            plan.add(world.nameOf(plugin));
        }
        plans.add(plan);
    }

    /**
     * Records {@code quiet} when the verify neither threw to the suite nor wrote to the log or the error sink.
     *
     * @implNote Java's calls answer values, so a verify throws to the suite when it answers anything but an answer
     * to a well-made call: success, the core's refusal, nothing to answer, or that the host is destroyed.
     */
    private void verify(String name) {
        int written = world.written(false);
        Answer<?> answer;
        try {
            answer = world.host.verify(world.plugin(name));
        } catch (RuntimeException thrown) {
            answer = null;
        }
        boolean wellMade = answer instanceof Answer.Ok || answer instanceof Answer.Refused
                || answer instanceof Answer.Absent || answer instanceof Answer.Destroyed;
        if (!wellMade && destroying) {
            thrownSinceTheDestroy++;
        }
        verifies.add(wellMade && world.written(false) == written ? "quiet" : "loud");
    }

    private List<Object> ledgerNames(LedgerRow.Status status) {
        Answer<List<LedgerRow>> ledger = world.host.ledger();
        List<Object> names = new ArrayList<>();
        if (ledger instanceof Answer.Ok) {
            TreeSet<String> sorted = new TreeSet<>();
            for (LedgerRow row : ((Answer.Ok<List<LedgerRow>>) ledger).value()) {
                if (row.status() == status) {
                    sorted.add(row.importName());
                }
            }
            names.addAll(sorted);
        } else if (!(ledger instanceof Answer.Destroyed)) {
            throw new IllegalStateException("the host answered no ledger: " + ledger);
        }
        return names;
    }

    private List<Object> forgotten() {
        TreeSet<String> forgotten = new TreeSet<>();
        for (String name : world.names()) {
            if (!world.host.manifest(world.plugin(name)).isPresent()) {
                forgotten.add(name);
            }
        }
        return new ArrayList<Object>(forgotten);
    }

    private Map<String, Observations.Observer> observers() {
        Map<String, Observations.Observer> observers = new HashMap<>();
        observers.put("plans", expectation -> new ArrayList<>(plans));
        observers.put("trace", expectation -> Observations.before(expectation, new ArrayList<>(trace)));
        observers.put("active", expectation -> ledgerNames(LedgerRow.Status.ACTIVE));
        observers.put("forgotten", expectation -> forgotten());
        observers.put("pending", expectation -> new ArrayList<Object>(pending));
        observers.put("verifies", expectation -> new ArrayList<>(verifies));
        observers.put("loud", expectation -> destroying
                ? Long.valueOf(world.written(true) - loudBeforeTheDestroy + thrownSinceTheDestroy)
                : Long.valueOf(thrownSinceTheDestroy));
        return observers;
    }

    /** An entry that offers a deactivate. */
    private static final class DeactivatingEntry implements Entry, Deactivating {
        private final Entry activating;
        private final Deactivating deactivating;

        private DeactivatingEntry(Entry activating, Deactivating deactivating) {
            this.activating = activating;
            this.deactivating = deactivating;
        }

        @Override
        public CompletionStage<?> activate(PluginContext context) {
            return activating.activate(context);
        }

        @Override
        public CompletionStage<?> deactivate() {
            return deactivating.deactivate();
        }
    }
}
