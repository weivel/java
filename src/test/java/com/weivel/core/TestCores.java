package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Cores for the tests, made through the wrapper's seams over the real core. */
final class TestCores {
    private static final CoreBinary BINARY = loadedOnce();

    private TestCores() {
    }

    private static CoreBinary loadedOnce() {
        return assertInstanceOf(CoreLoad.Loaded.class, CoreBinary.load(NativeFiles.coreBinary())).binary();
    }

    static CoreFunctions functions() {
        return BINARY.functions();
    }

    static Core core(CoreListener listener, ErrorSink sink) {
        return core(functions(), EventCopy.INSTANCE, listener, sink);
    }

    static Core core(CoreFunctions functions, EventCopying copying, CoreListener listener, ErrorSink sink) {
        return created(functions, copying, CoreOptions.defaults(), listener, sink);
    }

    static Core created(CoreFunctions functions, EventCopying copying, CoreOptions options, CoreListener listener,
                        ErrorSink sink) {
        Answer<Core> created = Core.wrapped(Wrapper.start(functions, copying, options, Optional.of(listener), sink));
        return ok(created);
    }

    static <T> T ok(Answer<T> answer) {
        @SuppressWarnings("unchecked")
        Answer.Ok<T> ok = assertInstanceOf(Answer.Ok.class, answer);
        return ok.value();
    }

    static Refusal refusalOf(Answer<?> answer) {
        return (Refusal) assertInstanceOf(Answer.Refused.class, answer).refusal().orElseThrow();
    }

    /** Every core function the wrapper called, in the order they returned. */
    static final class CallLog {
        private static final MethodHandle RECORD = recordHandle();
        private final List<CoreFunction> calls = Collections.synchronizedList(new ArrayList<>());

        CoreFunctions around(CoreFunctions table) {
            return table.mapped((function, handle) ->
                    MethodHandles.filterReturnValue(handle, MethodHandles.insertArguments(RECORD, 0, this, function)));
        }

        int record(CoreFunction function, int status) {
            calls.add(function);
            return status;
        }

        List<CoreFunction> calls() {
            synchronized (calls) {
                return List.copyOf(calls);
            }
        }

        long count(CoreFunction function) {
            return calls().stream().filter(function::equals).count();
        }

        void clear() {
            calls.clear();
        }

        private static MethodHandle recordHandle() {
            try {
                return MethodHandles.lookup().findVirtual(CallLog.class, "record",
                        MethodType.methodType(int.class, CoreFunction.class, int.class));
            } catch (ReflectiveOperationException unreachable) {
                throw new AssertionError(unreachable);
            }
        }
    }

    /** A sink that keeps every failure it is told. */
    static final class RecordingSink implements ErrorSink {
        private final List<Failure> failures = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void report(Failure failure) {
            failures.add(failure);
        }

        List<Failure> failures() {
            synchronized (failures) {
                return List.copyOf(failures);
            }
        }

        List<Failure> of(FailureKind kind) {
            return failures().stream().filter(failure -> failure.kind() == kind).toList();
        }
    }

    /** A listener that keeps every event it hears and the thread it heard it on. */
    static final class RecordingListener implements CoreListener {
        record Heard(CoreEvent event, Thread thread) {
        }

        private final List<Heard> heard = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void hear(CoreEvent event) {
            heard.add(new Heard(event, Thread.currentThread()));
        }

        List<CoreEvent> events() {
            synchronized (heard) {
                return heard.stream().map(Heard::event).toList();
            }
        }

        List<Heard> heard() {
            synchronized (heard) {
                return List.copyOf(heard);
            }
        }

        <E extends CoreEvent> List<E> of(Class<E> kind) {
            return events().stream().filter(kind::isInstance).map(kind::cast).toList();
        }

        void clear() {
            heard.clear();
        }
    }
}
