package com.weivel.core;

import static com.weivel.core.CoreLayouts.offsetOf;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The layer's own copy of a drained {@code weivel_event} into a {@link CoreEvent}. */
final class EventCopy implements EventCopying {
    static final EventCopy INSTANCE = new EventCopy();

    private static final long KIND = offsetOf(CoreLayouts.EVENT, "kind");
    private static final long PAYLOAD = offsetOf(CoreLayouts.EVENT, "data");

    private EventCopy() {
    }

    static int kindOf(MemorySegment event) {
        return event.get(JAVA_INT, KIND);
    }

    static int kindOf(CoreEvent event) {
        return switch (event) {
            case CoreEvent.LogEvent log -> CoreConstants.EVENT_LOG;
            case CoreEvent.RefusalEvent refusal -> CoreConstants.EVENT_REFUSAL;
            case CoreEvent.WantSettledEvent want -> CoreConstants.EVENT_WANT_SETTLED;
            case CoreEvent.WatchEvent watch -> CoreConstants.EVENT_WATCH;
            case CoreEvent.RevokedEvent revoked -> CoreConstants.EVENT_REVOKED;
            case CoreEvent.ReachRefusedEvent reach -> CoreConstants.EVENT_REACH_REFUSED;
            case CoreEvent.CapacityWantedEvent capacity -> CoreConstants.EVENT_CAPACITY_WANTED;
            case CoreEvent.CapabilityEvent capability -> CoreConstants.EVENT_CAPABILITY;
            case CoreEvent.DeadlineEvent deadline -> CoreConstants.EVENT_DEADLINE;
        };
    }

    static boolean isKnownKind(int kind) {
        return kind >= CoreConstants.EVENT_LOG && kind <= CoreConstants.EVENT_DEADLINE;
    }

    static long deadlineAt(MemorySegment event) {
        return event.get(JAVA_LONG, PAYLOAD + offsetOf(CoreLayouts.DEADLINE_EVENT, "at"));
    }

    static boolean deadlineSet(MemorySegment event) {
        return event.get(JAVA_INT, PAYLOAD + offsetOf(CoreLayouts.DEADLINE_EVENT, "set")) != 0;
    }

    @Override
    public CoreEvent whole(MemorySegment event, long sequence) {
        return copy(event, sequence, Texts.WHOLE);
    }

    @Override
    public CoreEvent fixedPart(MemorySegment event, long sequence) {
        return copy(event, sequence, Texts.FIXED);
    }

    private static CoreEvent copy(MemorySegment event, long sequence, Texts texts) {
        MemorySegment payload = event.asSlice(PAYLOAD, CoreLayouts.EVENT_DATA.byteSize());
        return switch (kindOf(event)) {
            case CoreConstants.EVENT_LOG -> log(payload, sequence, texts);
            case CoreConstants.EVENT_REFUSAL -> refusal(payload, sequence, texts);
            case CoreConstants.EVENT_WANT_SETTLED -> wantSettled(payload, sequence, texts);
            case CoreConstants.EVENT_WATCH -> watch(payload, sequence);
            case CoreConstants.EVENT_REVOKED -> revoked(payload, sequence);
            case CoreConstants.EVENT_REACH_REFUSED -> reachRefused(payload, sequence, texts);
            case CoreConstants.EVENT_CAPACITY_WANTED -> capacityWanted(payload, sequence, texts);
            case CoreConstants.EVENT_CAPABILITY -> capability(payload, sequence);
            case CoreConstants.EVENT_DEADLINE -> deadline(event, sequence);
            default -> throw new IllegalArgumentException("an event of a kind the layer does not know");
        };
    }

    private static CoreEvent log(MemorySegment payload, long sequence, Texts texts) {
        StructLayout layout = CoreLayouts.LOG_EVENT;
        return new CoreEvent.LogEvent(sequence, texts.incomplete(), payload.get(JAVA_INT, offsetOf(layout, "level")),
                texts.given(payload.get(ADDRESS, offsetOf(layout, "text"))));
    }

    private static CoreEvent refusal(MemorySegment payload, long sequence, Texts texts) {
        StructLayout layout = CoreLayouts.REFUSAL_EVENT;
        return new CoreEvent.RefusalEvent(sequence, texts.incomplete(), refusalAt(payload, offsetOf(layout, "refusal"),
                offsetOf(layout, "subject"), offsetOf(layout, "has_fix"), texts));
    }

    private static CoreEvent wantSettled(MemorySegment payload, long sequence, Texts texts) {
        StructLayout layout = CoreLayouts.WANT_EVENT;
        long refusalOffset = offsetOf(layout, "refusal");
        boolean rejected = payload.get(JAVA_INT, refusalOffset + offsetOf(CoreLayouts.REFUSAL, "code"))
                != CoreConstants.REFUSAL_NONE;
        Optional<String> subject = texts.optional(payload.get(ADDRESS, offsetOf(layout, "subject")));
        Optional<Refusal> refusal = rejected
                ? Optional.of(refusalAt(payload, refusalOffset, offsetOf(layout, "subject"),
                        offsetOf(layout, "has_fix"), texts))
                : Optional.empty();
        return new CoreEvent.WantSettledEvent(sequence, texts.incomplete(),
                payload.get(JAVA_LONG, offsetOf(layout, "want")), payload.get(JAVA_LONG, offsetOf(layout, "plugin")),
                payload.get(JAVA_LONG, offsetOf(layout, "implementation")), refusal,
                payload.get(JAVA_INT, offsetOf(layout, "degraded")) != 0, rejected ? Optional.empty() : subject);
    }

    private static CoreEvent watch(MemorySegment payload, long sequence) {
        StructLayout layout = CoreLayouts.WATCH_EVENT;
        return new CoreEvent.WatchEvent(sequence, false, payload.get(JAVA_LONG, offsetOf(layout, "watch")),
                payload.get(JAVA_LONG, offsetOf(layout, "plugin")),
                payload.get(JAVA_LONG, offsetOf(layout, "implementation")),
                payload.get(JAVA_INT, offsetOf(layout, "change")));
    }

    private static CoreEvent revoked(MemorySegment payload, long sequence) {
        StructLayout layout = CoreLayouts.REVOKED_EVENT;
        return new CoreEvent.RevokedEvent(sequence, false, payload.get(JAVA_LONG, offsetOf(layout, "plugin")),
                payload.get(JAVA_LONG, offsetOf(layout, "implementation")));
    }

    private static CoreEvent reachRefused(MemorySegment payload, long sequence, Texts texts) {
        StructLayout layout = CoreLayouts.REACH_REFUSAL;
        return new CoreEvent.ReachRefusedEvent(sequence, texts.incomplete(),
                payload.get(JAVA_INT, offsetOf(layout, "kind")), payload.get(JAVA_LONG, offsetOf(layout, "plugin")),
                texts.given(payload.get(ADDRESS, offsetOf(layout, "id"))),
                refusalAt(payload, offsetOf(layout, "refusal"), offsetOf(layout, "subject"),
                        offsetOf(layout, "has_fix"), texts));
    }

    private static CoreEvent capacityWanted(MemorySegment payload, long sequence, Texts texts) {
        StructLayout layout = CoreLayouts.CAPACITY_EVENT;
        return new CoreEvent.CapacityWantedEvent(sequence, texts.incomplete(),
                payload.get(JAVA_LONG, offsetOf(layout, "want")), payload.get(JAVA_LONG, offsetOf(layout, "plugin")),
                payload.get(JAVA_LONG, offsetOf(layout, "registration")),
                payload.get(JAVA_LONG, offsetOf(layout, "request")),
                payload.get(JAVA_INT, offsetOf(layout, "question")),
                texts.numbers(payload, offsetOf(layout, "constraints")));
    }

    private static CoreEvent capability(MemorySegment payload, long sequence) {
        StructLayout layout = CoreLayouts.CAPABILITY_EVENT;
        return new CoreEvent.CapabilityEvent(sequence, false, payload.get(JAVA_LONG, offsetOf(layout, "watch")),
                payload.get(JAVA_LONG, offsetOf(layout, "plugin")),
                payload.get(JAVA_LONG, offsetOf(layout, "implementation")),
                payload.get(JAVA_LONG, offsetOf(layout, "provider")),
                payload.get(JAVA_INT, offsetOf(layout, "change")));
    }

    private static CoreEvent deadline(MemorySegment event, long sequence) {
        return new CoreEvent.DeadlineEvent(sequence, false, deadlineAt(event), deadlineSet(event));
    }

    private static Refusal refusalAt(MemorySegment payload, long refusalOffset, long subjectOffset,
                                     long hasFixOffset, Texts texts) {
        StructLayout layout = CoreLayouts.REFUSAL;
        return new Refusal(payload.get(JAVA_INT, refusalOffset + offsetOf(layout, "code")),
                texts.given(payload.get(ADDRESS, refusalOffset + offsetOf(layout, "detail"))),
                texts.given(payload.get(ADDRESS, refusalOffset + offsetOf(layout, "fix"))),
                texts.optional(payload.get(ADDRESS, subjectOffset)),
                payload.get(JAVA_INT, hasFixOffset) != 0);
    }

    /** How a copy reads the texts and arrays of an event: whole, or with the layer's fixed sentence in their place. */
    private enum Texts {
        WHOLE {
            @Override
            String given(MemorySegment address) {
                return address.equals(MemorySegment.NULL) ? "" : read(address);
            }

            @Override
            Optional<String> optional(MemorySegment address) {
                return address.equals(MemorySegment.NULL) ? Optional.empty() : Optional.of(read(address));
            }

            @Override
            List<NamedNumber> numbers(MemorySegment payload, long offset) {
                return readNumbers(payload, offset);
            }
        },
        FIXED {
            @Override
            String given(MemorySegment address) {
                return address.equals(MemorySegment.NULL) ? "" : CoreEvent.TEXT_NOT_COPIED;
            }

            @Override
            Optional<String> optional(MemorySegment address) {
                return address.equals(MemorySegment.NULL) ? Optional.empty() : Optional.of(CoreEvent.TEXT_NOT_COPIED);
            }

            @Override
            List<NamedNumber> numbers(MemorySegment payload, long offset) {
                return List.of();
            }
        };

        abstract String given(MemorySegment address);

        abstract Optional<String> optional(MemorySegment address);

        abstract List<NamedNumber> numbers(MemorySegment payload, long offset);

        boolean incomplete() {
            return this == FIXED;
        }
    }

    private static String read(MemorySegment address) {
        return address.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
    }

    /** @implNote Stepped at the first element's {@code size}, the core's, reading only the fields both know. */
    private static List<NamedNumber> readNumbers(MemorySegment payload, long offset) {
        StructLayout list = CoreLayouts.NUMBERS;
        StructLayout element = CoreLayouts.NUMBER;
        int count = payload.get(JAVA_INT, offset + offsetOf(list, "count"));
        if (count == 0) {
            return List.of();
        }
        MemorySegment first = payload.get(ADDRESS, offset + offsetOf(list, "items")).reinterpret(element.byteSize());
        long stride = Integer.toUnsignedLong(first.get(JAVA_INT, offsetOf(element, "size")));
        if (stride < element.byteSize()) {
            throw new IllegalStateException("a number element smaller than the fields the layer reads");
        }
        MemorySegment items = first.reinterpret(stride * count);
        List<NamedNumber> numbers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long base = index * stride;
            numbers.add(new NamedNumber(read(items.get(ADDRESS, base + offsetOf(element, "name"))),
                    items.get(JAVA_DOUBLE, base + offsetOf(element, "value"))));
        }
        return List.copyOf(numbers);
    }
}
