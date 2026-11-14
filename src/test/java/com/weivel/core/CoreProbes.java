package com.weivel.core;

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
        private final TestCores.CallLog log = new TestCores.CallLog();
        private final List<Long> advanceTimes = Collections.synchronizedList(new ArrayList<>());

        CoreFunctions around(CoreFunctions table) {
            CoreFunctions counted = log.around(table);
            return counted.mapped((function, handle) -> function == CoreFunction.ADVANCE
                    ? MethodHandles.filterArguments(handle, 1, RECORD_TIME.bindTo(this))
                    : handle);
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
