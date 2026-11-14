package com.weivel.core;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Real cores for the tests of the layer's other packages, which cannot reach the wrapper's seams: a plain one, and
 * one that counts every crossing by the core's symbol and keeps every time handed to {@code weivel_advance}.
 */
public final class CoreProbes {
    private CoreProbes() {
    }

    /**
     * A real core with a first listener and a sink.
     *
     * @param listener the first listener
     * @param sink the error sink
     * @return the core
     */
    public static Core core(CoreListener listener, ErrorSink sink) {
        return TestCores.core(listener, sink);
    }

    /**
     * A real core whose crossings {@code crossings} counts.
     *
     * @param crossings what counts the crossings
     * @param listener the first listener
     * @param sink the error sink
     * @return the core
     */
    public static Core counted(Crossings crossings, CoreListener listener, ErrorSink sink) {
        return TestCores.core(crossings.around(TestCores.functions()), EventCopy.INSTANCE, listener, sink);
    }

    /**
     * A real core created with {@code options}, whose crossings {@code crossings} counts.
     *
     * @param crossings what counts the crossings
     * @param options the core's options
     * @param listener the first listener
     * @param sink the error sink
     * @return the core
     */
    public static Core counted(Crossings crossings, CoreOptions options, CoreListener listener, ErrorSink sink) {
        return TestCores.created(crossings.around(TestCores.functions()), EventCopy.INSTANCE, options, listener, sink);
    }

    /**
     * A real core whose capacity questions are each delivered incomplete, as though their constraints could not be
     * copied, and whose crossings {@code crossings} counts.
     *
     * @param crossings what counts the crossings
     * @param options the core's options
     * @param listener the first listener
     * @param sink the error sink
     * @return the core
     */
    public static Core withIncompleteCapacityQuestions(Crossings crossings, CoreOptions options,
                                                       CoreListener listener, ErrorSink sink) {
        EventCopying incompleteQuestions = new EventCopying() {
            @Override
            public CoreEvent whole(MemorySegment event, long sequence) {
                if (EventCopy.kindOf(event) == CoreConstants.EVENT_CAPACITY_WANTED) {
                    throw new IllegalStateException("a capacity question left incomplete on purpose");
                }
                return EventCopy.INSTANCE.whole(event, sequence);
            }

            @Override
            public CoreEvent fixedPart(MemorySegment event, long sequence) {
                return EventCopy.INSTANCE.fixedPart(event, sequence);
            }
        };
        return TestCores.created(crossings.around(TestCores.functions()), incompleteQuestions, options, listener,
                sink);
    }

    /**
     * Whether the calling thread holds the layer lock of {@code core}.
     *
     * @param core the core
     * @return whether it does
     */
    public static boolean isLockHeldByCurrentThread(Core core) {
        return core.isLockHeldByCurrentThread();
    }

    /**
     * The value of an {@link Answer.Ok}, failing the test on any other answer.
     *
     * @param answer the answer
     * @param <T> the value's type
     * @return the value
     */
    public static <T> T ok(Answer<T> answer) {
        return TestCores.ok(answer);
    }

    /** Every crossing a counted core made, by symbol, and every time it handed {@code weivel_advance}. */
    public static final class Crossings {
        private static final MethodHandle RECORD_TIME = recordTimeHandle();
        private static final MethodHandle RECORD_OPTIONS = recordOptionsHandle();
        private final List<WantOptions> wantOptions = Collections.synchronizedList(new ArrayList<>());
        private final TestCores.CallLog log = new TestCores.CallLog();
        private final List<Long> advanceTimes = Collections.synchronizedList(new ArrayList<>());

        CoreFunctions around(CoreFunctions table) {
            CoreFunctions counted = log.around(table);
            return counted.mapped((function, handle) -> switch (function) {
                case ADVANCE -> MethodHandles.filterArguments(handle, 1, RECORD_TIME.bindTo(this));
                case WANT_SERVICE -> MethodHandles.filterArguments(handle, 3, RECORD_OPTIONS.bindTo(this));
                default -> handle;
            });
        }

        /**
         * Every want's options as the core was handed them, in order; a want handed none is not listed.
         *
         * @return the options
         */
        public List<WantOptions> wantOptions() {
            synchronized (wantOptions) {
                return List.copyOf(wantOptions);
            }
        }

        MemorySegment recordOptions(MemorySegment options) {
            if (!options.equals(MemorySegment.NULL)) {
                MemorySegment struct = options.reinterpret(CoreLayouts.WANT_OPTIONS.byteSize());
                wantOptions.add(new WantOptions(choiceOf(struct.get(JAVA_INT, field("timed"))),
                        struct.get(JAVA_LONG, field("now")), struct.get(JAVA_LONG, field("timeout_millis")),
                        choiceOf(struct.get(JAVA_INT, field("budgeted"))), struct.get(JAVA_LONG,
                        field("budget_millis")), struct.get(JAVA_LONG, field("poll_millis"))));
            }
            return options;
        }

        private static long field(String name) {
            return CoreLayouts.offsetOf(CoreLayouts.WANT_OPTIONS, name);
        }

        private static Choice choiceOf(int value) {
            return Arrays.stream(Choice.values()).filter(choice -> choice.value() == value).findFirst()
                    .orElseThrow();
        }

        /**
         * How many times the core's function {@code symbol} was crossed into.
         *
         * @param symbol the function's symbol, as the core's header names it
         * @return the count
         */
        public long count(String symbol) {
            Optional<CoreFunction> function = Arrays.stream(CoreFunction.values())
                    .filter(candidate -> candidate.symbol().equals(symbol)).findFirst();
            return log.count(function.orElseThrow(() -> new IllegalArgumentException("no core function " + symbol)));
        }

        /**
         * How many crossings into the core were made, of every function.
         *
         * @return the count
         */
        public long total() {
            return log.calls().size();
        }

        /**
         * The symbol of every crossing into the core, in the order they returned.
         *
         * @return the symbols
         */
        public List<String> symbols() {
            return log.calls().stream().map(CoreFunction::symbol).toList();
        }

        /**
         * Every time handed to {@code weivel_advance}, in order.
         *
         * @return the times
         */
        public List<Long> advanceTimes() {
            synchronized (advanceTimes) {
                return List.copyOf(advanceTimes);
            }
        }

        long recordTime(long now) {
            advanceTimes.add(now);
            return now;
        }

        private static MethodHandle recordOptionsHandle() {
            try {
                return MethodHandles.lookup().findVirtual(Crossings.class, "recordOptions",
                        MethodType.methodType(MemorySegment.class, MemorySegment.class));
            } catch (ReflectiveOperationException unreachable) {
                throw new AssertionError(unreachable);
            }
        }

        private static MethodHandle recordTimeHandle() {
            try {
                return MethodHandles.lookup().findVirtual(Crossings.class, "recordTime",
                        MethodType.methodType(long.class, long.class));
            } catch (ReflectiveOperationException unreachable) {
                throw new AssertionError(unreachable);
            }
        }
    }
}
