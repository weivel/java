package com.weivel.conformance;

import com.weivel.context.PendingWant;
import com.weivel.context.PluginContext;
import com.weivel.context.WantLimits;
import com.weivel.core.Answer;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreOptions;
import com.weivel.core.Refusal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Drives a {@code want} vector on the suite's own time: the consumer asks at 0, the provider registers at
 * {@code registerAtMillis}, and time runs to the horizon. The scheduler runs every task due on the way to the
 * registration unless {@code schedulerRunsBeforeRegistering} is false, when only the clock moves, so the registration
 * meets a deadline the layer's timer has not fired.
 */
final class WantRun {
    private static final String CONSUMER = "consumer";
    private static final String PROVIDER = "provider";

    private final DriverWorld world;
    private boolean served;
    private boolean timedOut;
    private Object atMillis = StandardJson.NULL;
    private Object refusal = StandardJson.NULL;

    private WantRun(DriverWorld world) {
        this.world = world;
    }

    /** A want vector's core options: its service is well-known, so the provider registers it under its own name. */
    static CoreOptions coreOptions(LayerVector vector) {
        Given given = Given.of(vector);
        CoreOptions defaults = CoreOptions.defaults();
        return new CoreOptions(defaults.initialEventCapacity(), defaults.minimumLogLevel(), defaults.api(),
                defaults.applications(), Collections.singletonList(given.service), defaults.constraintVocabulary());
    }

    static Map<String, Observations.Observer> drive(LayerVector vector, DriverWorld world) {
        Given given = Given.of(vector);
        WantRun run = new WantRun(world);
        world.register(Manifests.services(CONSUMER, Collections.<String>emptyList(),
                Collections.singletonList(given.service)), DriverWorld.inFlight());
        world.register(Manifests.services(PROVIDER, Collections.singletonList(given.service),
                Collections.<String>emptyList()), DriverWorld.inFlight());
        world.openInFlight(world.names());
        run.follow(world.context(CONSUMER).want(given.service, given.timeout.isPresent()
                ? WantLimits.timeout(given.timeout.getAsLong())
                : WantLimits.unbounded()));
        world.noteTimer();
        if (given.registerAt.isPresent()) {
            run.registerAt(given, given.registerAt.getAsLong());
        }
        world.runUntil(given.horizon);
        return run.observers();
    }

    private void registerAt(Given given, long at) {
        if (given.schedulerRunsBeforeRegistering) {
            world.runUntil(at);
        } else {
            world.time.moveClockTo(at);
        }
        PluginContext provider = world.context(PROVIDER);
        Answer<?> registered = provider.register(given.service, new Object());
        if (!(registered instanceof Answer.Ok)) {
            throw new IllegalStateException("the provider's registration answered " + registered);
        }
        world.noteTimer();
    }

    private void follow(PendingWant want) {
        want.settlement().thenAccept(this::settled);
    }

    private void settled(Answer<Object> settlement) {
        atMillis = world.time.now();
        if (settlement instanceof Answer.Ok) {
            served = true;
            return;
        }
        Optional<Refusal> refused = KitRefusals.refusalOf(settlement);
        if (!refused.isPresent()) {
            throw new IllegalStateException("the want settled with " + settlement + ", neither served nor refused");
        }
        timedOut = refused.get().code() == CoreConstants.REFUSAL_TIMED_OUT;
        refusal = KitRefusals.kitRefusal(refused.get());
    }

    private Map<String, Observations.Observer> observers() {
        Map<String, Observations.Observer> observers = new HashMap<>();
        observers.put("settled", expectation -> served);
        observers.put("timedOut", expectation -> timedOut);
        observers.put("atMillis", expectation -> atMillis);
        observers.put("refusal", expectation -> refusal);
        observers.put("timer", expectation -> world.timerMoves());
        observers.put("fired", expectation -> {
            List<Long> fired = world.time.fired();
            return Collections.<Object>unmodifiableList(fired);
        });
        return observers;
    }

    /** A want vector's {@code given}, every part checked. */
    private static final class Given {
        private final String service;
        private final OptionalLong registerAt;
        private final OptionalLong timeout;
        private final long horizon;
        private final boolean schedulerRunsBeforeRegistering;

        private Given(String service, OptionalLong registerAt, OptionalLong timeout, long horizon,
                      boolean schedulerRunsBeforeRegistering) {
            this.service = service;
            this.registerAt = registerAt;
            this.timeout = timeout;
            this.horizon = horizon;
            this.schedulerRunsBeforeRegistering = schedulerRunsBeforeRegistering;
        }

        static Given of(LayerVector vector) {
            String where = "the vector " + vector.id() + "'s given";
            Map<String, Object> given = vector.given();
            Shapes.refuseUnknownKeys(where, given, "service", "registerAtMillis", "timeoutMillis", "horizonMillis",
                    "schedulerRunsBeforeRegistering");
            Map<String, Object> service = Shapes.object(where, given, "service");
            Shapes.refuseUnknownKeys(where + "'s service", service, "id");
            return new Given(Shapes.text(where + "'s service", service, "id"),
                    Shapes.numberOrNull(where, given, "registerAtMillis"),
                    Shapes.numberOrNull(where, given, "timeoutMillis"),
                    Shapes.number(where, given, "horizonMillis"),
                    Shapes.flag(where, given, "schedulerRunsBeforeRegistering", true));
        }
    }
}
