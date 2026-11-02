package io.github.intisy.ai.seam.jvm.context;

import io.github.intisy.ai.api.Events;
import io.github.intisy.ai.api.TopicType;
import io.github.intisy.ai.engine.EventBus;
import java.util.function.Consumer;

/** The contract's typed events over a session's bus, which works in topic ids. */
final class SessionEvents implements Events {

    private final EventBus bus;

    SessionEvents(EventBus bus) {
        this.bus = bus;
    }

    @Override
    public <T> void publish(TopicType<T> topic, T payload) {
        bus.publish(topic.id(), payload);
    }

    @Override
    public <T> Runnable subscribe(TopicType<T> topic, final Consumer<T> listener) {
        return SessionServices.cancelling(bus.subscribe(topic.id(), new EventBus.Listener() {
            @Override
            @SuppressWarnings("unchecked")
            public void received(Object payload) {
                listener.accept((T) payload);
            }
        }));
    }
}
