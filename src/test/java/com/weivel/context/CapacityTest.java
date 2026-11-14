package com.weivel.context;

import static com.weivel.context.ContextFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.weivel.core.Answer;
import com.weivel.core.Choice;
import com.weivel.core.FailureKind;
import com.weivel.core.Manifest;
import com.weivel.core.NamedNumber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.weivel.core.CoreCalls;
import com.weivel.core.WantOptions;
import com.weivel.timer.Scheduler;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Capacity answering, opt-in at registration, against the real core's constrained wants. */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class CapacityTest {
    private static final String MEASURED = "provider:measured";

    @Test
    void anAnswererOfferedAtRegistrationAnswersEachQuestionAndItsYesSettlesTheWant() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY)) {
            PluginContext provider = fixture.opened(advertising(50));
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", MEASURED, 10, Choice.NO));
            List<List<NamedNumber>> askedToServe = new ArrayList<>();
            Object measured = new Object();
            value(provider.register(MEASURED, measured, new CapacityAnswerer() {
                @Override
                public List<NamedNumber> attributes() {
                    return Collections.singletonList(new NamedNumber("latency", 5));
                }

                @Override
                public boolean serves(List<NamedNumber> constraints) {
                    askedToServe.add(constraints);
                    return true;
                }
            }));

            CompletableFuture<Answer<Object>> settlement = consumer.want(MEASURED, WantLimits.polled(100))
                    .settlement();

            assertSame(measured, value(settlement.join()));
            assertEquals(Collections.singletonList(Collections.singletonList(new NamedNumber("latency", 10))),
                    askedToServe);
            assertEquals(Collections.emptyList(), fixture.failures);
        }
    }

    @Test
    void aRegistrationThatOfferedNoAnswererAddsNoLiveAttributesAndServesEveryConsumer() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY)) {
            PluginContext provider = fixture.opened(advertising(5));
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", MEASURED, 10, Choice.NO));
            Object measured = new Object();
            value(provider.register(MEASURED, measured));

            CompletableFuture<Answer<Object>> settlement = consumer.want(MEASURED, WantLimits.polled(100))
                    .settlement();

            assertSame(measured, value(settlement.join()));
            assertEquals(2, fixture.crossings.count("weivel_report_capacity"));
        }
    }

    @Test
    void anAdvertisementThatMissesTheConstraintAndNoLiveAttributeLeavesTheWantWaiting() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY)) {
            PluginContext provider = fixture.opened(advertising(50));
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", MEASURED, 10, Choice.NO));
            value(provider.register(MEASURED, new Object()));

            CompletableFuture<Answer<Object>> settlement = consumer.want(MEASURED, WantLimits.polled(100))
                    .settlement();

            assertFalse(settlement.isDone());
            assertEquals(1, fixture.crossings.count("weivel_report_capacity"));
        }
    }

    @Test
    void anAnswererThatThrowsGoesToTheSinkAndItsQuestionIsLeftUnanswered() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY)) {
            PluginContext provider = fixture.opened(advertising(5));
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", MEASURED, 10, Choice.NO));
            value(provider.register(MEASURED, new Object(), new CapacityAnswerer() {
                @Override
                public List<NamedNumber> attributes() {
                    throw new IllegalStateException("an answerer failing on purpose");
                }

                @Override
                public boolean serves(List<NamedNumber> constraints) {
                    return true;
                }
            }));

            CompletableFuture<Answer<Object>> settlement = consumer.want(MEASURED, WantLimits.polled(100))
                    .settlement();

            assertFalse(settlement.isDone());
            assertEquals(0, fixture.crossings.count("weivel_report_capacity"));
            assertEquals(1, fixture.failuresOf(FailureKind.PLUGIN_CALLBACK).size());
            fixture.failures.clear();
        }
    }

    @Test
    void anIncompleteQuestionIsLeftUnansweredAndPutToNoAnswerer() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY, true, null)) {
            PluginContext provider = fixture.opened(advertising(5));
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", MEASURED, 10, Choice.NO));
            List<String> asked = new ArrayList<>();
            value(provider.register(MEASURED, new Object(), new CapacityAnswerer() {
                @Override
                public List<NamedNumber> attributes() {
                    asked.add("attributes");
                    return Collections.emptyList();
                }

                @Override
                public boolean serves(List<NamedNumber> constraints) {
                    asked.add("serves");
                    return true;
                }
            }));

            consumer.want(MEASURED, WantLimits.polled(100));

            assertEquals(Collections.emptyList(), asked);
            assertEquals(0, fixture.crossings.count("weivel_report_capacity"));
            assertEquals(1, fixture.failuresOf(FailureKind.EVENT_INCOMPLETE).size());
            assertEquals(1, fixture.failures.size());
            fixture.failures.clear();
        }
    }

    private static Manifest advertising(double latency) {
        return new Manifest("provider", 0, Optional.empty(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(new Manifest.ServiceOffer(MEASURED,
                        Collections.singletonList(new NamedNumber("latency", latency)))),
                Collections.emptyList(), Collections.emptyList(), Optional.empty());
    }

    @Test
    void anAnswerTheCoreDoesNotTakeGoesToTheSink() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY)) {
            PluginContext provider = fixture.opened(advertising(50));
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", MEASURED, 10, Choice.NO));
            value(provider.register(MEASURED, new Object(), new CapacityAnswerer() {
                @Override
                public List<NamedNumber> attributes() {
                    return Arrays.asList(new NamedNumber("latency", 5), new NamedNumber("latency", 6));
                }

                @Override
                public boolean serves(List<NamedNumber> constraints) {
                    return true;
                }
            }));

            CompletableFuture<Answer<Object>> settlement = consumer.want(MEASURED, WantLimits.polled(100))
                    .settlement();

            assertFalse(settlement.isDone());
            assertEquals(1, fixture.failuresOf(FailureKind.CAPACITY_UNANSWERED).size());
            fixture.failures.clear();
        }
    }

    @Test
    void aQuestionWithNoClockToAnswerItByGoesToTheSink() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY, false,
                Scheduler.none())) {
            PluginContext provider = fixture.opened(advertising(5));
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", MEASURED, 10, Choice.NO));
            value(provider.register(MEASURED, new Object()));

            value(fixture.core.call(CoreCalls.wantService(fixture.plugin(consumer), MEASURED,
                    new WantOptions(Choice.DEFAULT, 0, 0, Choice.DEFAULT, 0, 100))));

            assertEquals(0, fixture.crossings.count("weivel_report_capacity"));
            assertEquals(1, fixture.failuresOf(FailureKind.CAPACITY_UNANSWERED).size());
            fixture.failures.clear();
        }
    }

    @Test
    void anAttributesAnswerLeavesServesAtItsDefault() {
        try (ContextFixture fixture = new ContextFixture(ContextFixture.LATENCY_VOCABULARY)) {
            PluginContext provider = fixture.opened(advertising(5));
            PluginContext consumer = fixture.opened(ContextFixture.constrained("consumer", MEASURED, 10, Choice.NO));
            value(provider.register(MEASURED, new Object()));

            value(consumer.want(MEASURED, WantLimits.polled(100)).settlement().join());

            assertEquals(Arrays.asList(Choice.DEFAULT, Choice.YES), fixture.crossings.capacityServes());
        }
    }
}
