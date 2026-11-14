package com.weivel.context;

import static com.weivel.context.ContextFixture.consuming;
import static com.weivel.context.ContextFixture.providing;
import static com.weivel.context.ContextFixture.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.CoreConstants;
import com.weivel.core.Manifest;
import com.weivel.core.RefusalStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import com.weivel.core.CoreCalls;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** A plugin's services, each sentence of the contract's {@code context} section about them, against the real core. */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ServicesTest {
    private static final String GREETER = "provider:greeter";
    private static final long UNKNOWN_HANDLE = 424242L;

    private final ContextFixture fixture = new ContextFixture();
    private final PluginContext provider = fixture.opened(providing("provider", GREETER));
    private final PluginContext consumer = fixture.opened(consuming("consumer", GREETER, "unregistered"));

    @AfterEach
    void close() {
        fixture.close();
        assertEquals(Collections.emptyList(), fixture.failures);
    }

    @Test
    void aGetAnswersTheProvidersOwnObject() {
        Object greeter = new Object();
        value(provider.register(GREETER, greeter));

        assertSame(greeter, value(consumer.get(GREETER)));
    }

    @Test
    void aGetFindingNothingRegisteredIsAnAbsenceNotARefusal() {
        assertEquals(new Answer.Absent<Object>(), consumer.get("unregistered"));
    }

    @Test
    void aGetTheCoreRefusesAnswersThatRefusalNeverAnAbsence() {
        Answer.Refused<Object> refused = assertInstanceOf(Answer.Refused.class, consumer.get("undeclared"));

        assertEquals(CoreConstants.REFUSAL_UNDECLARED, refused.refusal().get().code());
        assertEquals(Optional.of("undeclared"), refused.refusal().get().subject());
    }

    @Test
    void theIdsAreTheReachableIdsRegisteredNow() {
        value(provider.register(GREETER, new Object()));

        assertEquals(Collections.singletonList(GREETER), value(consumer.ids()));
    }

    @Test
    void theIdsTheCoreRefusesAreThatRefusalNeverAnEmptyList() {
        PluginContext unopened = fixture.registered(Manifest.named("unopened"));

        assertEquals(RefusalStatus.REFUSED, assertInstanceOf(Answer.Refused.class, unopened.ids()).status());
    }

    @Test
    void aWatchHearsEveryLaterRegistrationWithTheObjectAndUnregistrationWithNothingUntilStopped() {
        List<Object> heard = new ArrayList<>();
        Watching watching = value(consumer.watch(GREETER, recording(heard)));
        Object first = new Object();
        Object second = new Object();

        ServiceRegistration registration = value(provider.register(GREETER, first));
        value(registration.unregister());
        ServiceRegistration again = value(provider.register(GREETER, second));
        value(watching.stop());
        value(again.unregister());

        assertEquals(3, heard.size());
        assertSame(first, heard.get(0));
        assertEquals("unregistered", heard.get(1));
        assertSame(second, heard.get(2));
    }

    @Test
    void aWatchTheCoreRefusesAnswersThatRefusalNeverAWatchThatHearsNothing() {
        Answer.Refused<Watching> refused = assertInstanceOf(Answer.Refused.class,
                consumer.watch("undeclared", recording(new ArrayList<>())));

        assertEquals(CoreConstants.REFUSAL_UNDECLARED, refused.refusal().get().code());
    }

    @Test
    void aRegistrationLastsUntilThePluginUnregistersIt() {
        ServiceRegistration registration = value(provider.register(GREETER, new Object()));

        value(registration.unregister());

        assertEquals(new Answer.Absent<Object>(), consumer.get(GREETER));
    }

    @Test
    void noAnswerIsCachedSoEveryGetAsksTheCore() {
        value(provider.register(GREETER, new Object()));
        long before = fixture.crossings.count("weivel_get_service");

        value(consumer.get(GREETER));
        value(consumer.get(GREETER));

        assertEquals(before + 2, fixture.crossings.count("weivel_get_service"));
    }

    @Test
    void aCallThroughAResolvedObjectCrossesNothing() {
        value(provider.register(GREETER, (Supplier<String>) () -> "hello"));
        @SuppressWarnings("unchecked")
        Supplier<String> greeter = (Supplier<String>) value(consumer.get(GREETER));
        long before = fixture.crossings.total();

        String greeting = greeter.get();

        assertEquals("hello", greeting);
        assertEquals(before, fixture.crossings.total());
    }

    @Test
    void aRegistrationsIdTheCoreCallsMalformedIsTheCallersMistakeAndNoSinkHearsOfIt() {
        Answer.Refused<ServiceRegistration> refused = assertInstanceOf(Answer.Refused.class,
                provider.register("", new Object()));

        assertEquals(RefusalStatus.MALFORMED, refused.status());
        assertTrue(fixture.failures.isEmpty());
    }

    static ServiceWatcher recording(List<Object> heard) {
        return new ServiceWatcher() {
            @Override
            public void registered(Object service) {
                heard.add(service);
            }

            @Override
            public void unregistered() {
                heard.add("unregistered");
            }
        };
    }

    @Test
    void aWantSettledBeforeAnUnregistrationMadeInsideADispatchStillAnswersTheProvidersObject() {
        CompletableFuture<Answer<Object>> settlement = consumer.want(GREETER, WantLimits.unbounded()).settlement();
        Object greeter = new Object();
        AtomicBoolean first = new AtomicBoolean(true);
        fixture.core.addListener(event -> {
            if (first.getAndSet(false)) {
                value(value(provider.register(GREETER, greeter)).unregister());
            }
        });

        fixture.core.call(CoreCalls.unregister(UNKNOWN_HANDLE));

        assertSame(greeter, value(settlement.join()));
    }

    @Test
    void aWatchHearsARegistrationUnregisteredInsideADispatchWithTheProvidersObject() {
        List<Object> heard = new ArrayList<>();
        value(consumer.watch(GREETER, recording(heard)));
        Object greeter = new Object();
        AtomicBoolean first = new AtomicBoolean(true);
        fixture.core.addListener(event -> {
            if (first.getAndSet(false)) {
                value(value(provider.register(GREETER, greeter)).unregister());
            }
        });

        fixture.core.call(CoreCalls.unregister(UNKNOWN_HANDLE));

        assertEquals(Arrays.asList(greeter, "unregistered"), heard);
        assertSame(greeter, heard.get(0));
    }
}
