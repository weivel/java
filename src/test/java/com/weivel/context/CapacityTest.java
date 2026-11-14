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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
                    return List.of(new NamedNumber("latency", 5));
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
            assertEquals(List.of(List.of(new NamedNumber("latency", 10))), askedToServe);
            assertEquals(List.of(), fixture.failures);
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
                    return List.of();
                }

                @Override
                public boolean serves(List<NamedNumber> constraints) {
                    asked.add("serves");
                    return true;
                }
            }));

            consumer.want(MEASURED, WantLimits.polled(100));

            assertEquals(List.of(), asked);
            assertEquals(0, fixture.crossings.count("weivel_report_capacity"));
            assertEquals(1, fixture.failuresOf(FailureKind.EVENT_INCOMPLETE).size());
            assertEquals(1, fixture.failures.size());
            fixture.failures.clear();
        }
    }

    private static Manifest advertising(double latency) {
        return new Manifest("provider", 0, Optional.empty(), List.of(), List.of(), List.of(),
                List.of(new Manifest.ServiceOffer(MEASURED, List.of(new NamedNumber("latency", latency)))),
                List.of(), List.of(), Optional.empty());
    }
}
