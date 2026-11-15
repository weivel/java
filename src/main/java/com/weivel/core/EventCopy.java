package com.weivel.core;

import static com.weivel.core.CoreLayouts.offsetOf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** The layer's own copy of a drained {@code weivel_event} into a {@link CoreEvent}. */
final class EventCopy implements EventCopying {
    static final EventCopy INSTANCE = new EventCopy();

    private static final long KIND = offsetOf(CoreLayouts.EVENT, "kind");
    private static final long PAYLOAD = offsetOf(CoreLayouts.EVENT, "data");

    private EventCopy() {
    }

    static int kindOf(NativeRegion event) {
        return event.getInt(KIND);
    }

    static int kindOf(CoreEvent event) {
        if (event instanceof CoreEvent.LogEvent) {
            return CoreConstants.EVENT_LOG;
        }
        if (event instanceof CoreEvent.RefusalEvent) {
            return CoreConstants.EVENT_REFUSAL;
        }
        if (event instanceof CoreEvent.WantSettledEvent) {
            return CoreConstants.EVENT_WANT_SETTLED;
        }
        if (event instanceof CoreEvent.WatchEvent) {
            return CoreConstants.EVENT_WATCH;
        }
        if (event instanceof CoreEvent.RevokedEvent) {
            return CoreConstants.EVENT_REVOKED;
        }
        if (event instanceof CoreEvent.ReachRefusedEvent) {
            return CoreConstants.EVENT_REACH_REFUSED;
        }
        if (event instanceof CoreEvent.CapacityWantedEvent) {
            return CoreConstants.EVENT_CAPACITY_WANTED;
        }
        if (event instanceof CoreEvent.CapabilityEvent) {
            return CoreConstants.EVENT_CAPABILITY;
        }
        if (event instanceof CoreEvent.DeadlineEvent) {
            return CoreConstants.EVENT_DEADLINE;
        }
        if (event instanceof CoreEvent.HoldingsReleasedEvent) {
            return CoreConstants.EVENT_HOLDINGS_RELEASED;
        }
        throw new IllegalArgumentException("an event of a kind the layer does not know");
    }

    static boolean isKnownKind(int kind) {
        return kind >= CoreConstants.EVENT_LOG && kind <= CoreConstants.EVENT_HOLDINGS_RELEASED;
    }

    static long deadlineAt(NativeRegion event) {
        return event.getLong(PAYLOAD + offsetOf(CoreLayouts.DEADLINE_EVENT, "at"));
    }

    static boolean deadlineSet(NativeRegion event) {
        return event.getInt(PAYLOAD + offsetOf(CoreLayouts.DEADLINE_EVENT, "set")) != 0;
    }

    @Override
    public CoreEvent whole(NativeRegion event, long sequence) {
        return copy(event, sequence, Texts.WHOLE);
    }

    @Override
    public CoreEvent fixedPart(NativeRegion event, long sequence) {
        return copy(event, sequence, Texts.FIXED);
    }

    private static CoreEvent copy(NativeRegion event, long sequence, Texts texts) {
        NativeRegion payload = event.slice(PAYLOAD, CoreLayouts.EVENT_DATA.byteSize());
        switch (kindOf(event)) {
            case CoreConstants.EVENT_LOG:
                return log(payload, sequence, texts);
            case CoreConstants.EVENT_REFUSAL:
                return refusal(payload, sequence, texts);
            case CoreConstants.EVENT_WANT_SETTLED:
                return wantSettled(payload, sequence, texts);
            case CoreConstants.EVENT_WATCH:
                return watch(payload, sequence);
            case CoreConstants.EVENT_REVOKED:
                return revoked(payload, sequence);
            case CoreConstants.EVENT_REACH_REFUSED:
                return reachRefused(payload, sequence, texts);
            case CoreConstants.EVENT_CAPACITY_WANTED:
                return capacityWanted(payload, sequence, texts);
            case CoreConstants.EVENT_CAPABILITY:
                return capability(payload, sequence);
            case CoreConstants.EVENT_DEADLINE:
                return deadline(event, sequence);
            case CoreConstants.EVENT_HOLDINGS_RELEASED:
                return holdingsReleased(payload, sequence);
            default:
                throw new IllegalArgumentException("an event of a kind the layer does not know");
        }
    }

    private static CoreEvent log(NativeRegion payload, long sequence, Texts texts) {
        Layout layout = CoreLayouts.LOG_EVENT;
        return new CoreEvent.LogEvent(sequence, texts.incomplete(), payload.getInt(offsetOf(layout, "level")),
                texts.given(payload, payload.getAddress(offsetOf(layout, "text"))));
    }

    private static CoreEvent refusal(NativeRegion payload, long sequence, Texts texts) {
        Layout layout = CoreLayouts.REFUSAL_EVENT;
        return new CoreEvent.RefusalEvent(sequence, texts.incomplete(), refusalAt(payload, offsetOf(layout, "refusal"),
                offsetOf(layout, "subject"), offsetOf(layout, "has_fix"), texts));
    }

    private static CoreEvent wantSettled(NativeRegion payload, long sequence, Texts texts) {
        Layout layout = CoreLayouts.WANT_EVENT;
        long refusalOffset = offsetOf(layout, "refusal");
        boolean rejected = payload.getInt(refusalOffset + offsetOf(CoreLayouts.REFUSAL, "code"))
                != CoreConstants.REFUSAL_NONE;
        Optional<String> subject = texts.optional(payload, payload.getAddress(offsetOf(layout, "subject")));
        Optional<Refusal> refusal = rejected
                ? Optional.of(refusalAt(payload, refusalOffset, offsetOf(layout, "subject"),
                        offsetOf(layout, "has_fix"), texts))
                : Optional.<Refusal>empty();
        return new CoreEvent.WantSettledEvent(sequence, texts.incomplete(),
                payload.getLong(offsetOf(layout, "want")), payload.getLong(offsetOf(layout, "plugin")),
                payload.getLong(offsetOf(layout, "implementation")), refusal,
                payload.getInt(offsetOf(layout, "degraded")) != 0, rejected ? Optional.<String>empty() : subject);
    }

    private static CoreEvent watch(NativeRegion payload, long sequence) {
        Layout layout = CoreLayouts.WATCH_EVENT;
        return new CoreEvent.WatchEvent(sequence, false, payload.getLong(offsetOf(layout, "watch")),
                payload.getLong(offsetOf(layout, "plugin")),
                payload.getLong(offsetOf(layout, "implementation")),
                payload.getInt(offsetOf(layout, "change")));
    }

    private static CoreEvent revoked(NativeRegion payload, long sequence) {
        Layout layout = CoreLayouts.REVOKED_EVENT;
        return new CoreEvent.RevokedEvent(sequence, false, payload.getLong(offsetOf(layout, "plugin")),
                payload.getLong(offsetOf(layout, "implementation")));
    }

    private static CoreEvent reachRefused(NativeRegion payload, long sequence, Texts texts) {
        Layout layout = CoreLayouts.REACH_REFUSAL;
        return new CoreEvent.ReachRefusedEvent(sequence, texts.incomplete(),
                payload.getInt(offsetOf(layout, "kind")), payload.getLong(offsetOf(layout, "plugin")),
                texts.given(payload, payload.getAddress(offsetOf(layout, "id"))),
                refusalAt(payload, offsetOf(layout, "refusal"), offsetOf(layout, "subject"),
                        offsetOf(layout, "has_fix"), texts));
    }

    private static CoreEvent capacityWanted(NativeRegion payload, long sequence, Texts texts) {
        Layout layout = CoreLayouts.CAPACITY_EVENT;
        return new CoreEvent.CapacityWantedEvent(sequence, texts.incomplete(),
                payload.getLong(offsetOf(layout, "want")), payload.getLong(offsetOf(layout, "plugin")),
                payload.getLong(offsetOf(layout, "registration")),
                payload.getLong(offsetOf(layout, "request")),
                payload.getInt(offsetOf(layout, "question")),
                texts.numbers(payload, offsetOf(layout, "constraints")));
    }

    private static CoreEvent capability(NativeRegion payload, long sequence) {
        Layout layout = CoreLayouts.CAPABILITY_EVENT;
        return new CoreEvent.CapabilityEvent(sequence, false, payload.getLong(offsetOf(layout, "watch")),
                payload.getLong(offsetOf(layout, "plugin")),
                payload.getLong(offsetOf(layout, "implementation")),
                payload.getLong(offsetOf(layout, "provider")),
                payload.getInt(offsetOf(layout, "change")));
    }

    private static CoreEvent deadline(NativeRegion event, long sequence) {
        return new CoreEvent.DeadlineEvent(sequence, false, deadlineAt(event), deadlineSet(event));
    }

    private static CoreEvent holdingsReleased(NativeRegion payload, long sequence) {
        Layout layout = CoreLayouts.HOLDINGS_RELEASED_EVENT;
        return new CoreEvent.HoldingsReleasedEvent(sequence, false, payload.getLong(offsetOf(layout, "owner")),
                payload.getLong(offsetOf(layout, "registration")));
    }

    private static Refusal refusalAt(NativeRegion payload, long refusalOffset, long subjectOffset,
                                     long hasFixOffset, Texts texts) {
        Layout layout = CoreLayouts.REFUSAL;
        return new Refusal(payload.getInt(refusalOffset + offsetOf(layout, "code")),
                texts.given(payload, payload.getAddress(refusalOffset + offsetOf(layout, "detail"))),
                texts.given(payload, payload.getAddress(refusalOffset + offsetOf(layout, "fix"))),
                texts.optional(payload, payload.getAddress(subjectOffset)),
                payload.getInt(hasFixOffset) != 0);
    }

    /** How a copy reads the texts and arrays of an event: whole, or with the layer's fixed sentence in their place. */
    private enum Texts {
        WHOLE {
            @Override
            String given(NativeRegion memory, long address) {
                return address == 0 ? "" : memory.textAt(address);
            }

            @Override
            Optional<String> optional(NativeRegion memory, long address) {
                return address == 0 ? Optional.<String>empty() : Optional.of(memory.textAt(address));
            }

            @Override
            List<NamedNumber> numbers(NativeRegion payload, long offset) {
                return readNumbers(payload, offset);
            }
        },
        FIXED {
            @Override
            String given(NativeRegion memory, long address) {
                return address == 0 ? "" : CoreEvent.TEXT_NOT_COPIED;
            }

            @Override
            Optional<String> optional(NativeRegion memory, long address) {
                return address == 0 ? Optional.<String>empty() : Optional.of(CoreEvent.TEXT_NOT_COPIED);
            }

            @Override
            List<NamedNumber> numbers(NativeRegion payload, long offset) {
                return Collections.emptyList();
            }
        };

        abstract String given(NativeRegion memory, long address);

        abstract Optional<String> optional(NativeRegion memory, long address);

        abstract List<NamedNumber> numbers(NativeRegion payload, long offset);

        boolean incomplete() {
            return this == FIXED;
        }
    }

    /** @implNote Stepped at the first element's {@code size}, the core's, reading only the fields both know. */
    private static List<NamedNumber> readNumbers(NativeRegion payload, long offset) {
        Layout list = CoreLayouts.NUMBERS;
        Layout element = CoreLayouts.NUMBER;
        int count = payload.getInt(offset + offsetOf(list, "count"));
        if (count == 0) {
            return Collections.emptyList();
        }
        long itemsAddress = payload.getAddress(offset + offsetOf(list, "items"));
        NativeRegion first = payload.at(itemsAddress, element.byteSize());
        long stride = Integer.toUnsignedLong(first.getInt(offsetOf(element, "size")));
        if (stride < element.byteSize()) {
            throw new IllegalStateException("a number element smaller than the fields the layer reads");
        }
        NativeRegion items = payload.at(itemsAddress, stride * count);
        List<NamedNumber> numbers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long base = index * stride;
            numbers.add(new NamedNumber(items.textAt(items.getAddress(base + offsetOf(element, "name"))),
                    items.getDouble(base + offsetOf(element, "value"))));
        }
        return Collections.unmodifiableList(numbers);
    }
}
