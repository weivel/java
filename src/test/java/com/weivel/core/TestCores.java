package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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

    static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }

    static Refusal refusalOf(Answer<?> answer) {
        return (Refusal) assertInstanceOf(Answer.Refused.class, answer).refusal().get();
    }

    /** Every core function the wrapper called, in the order they returned. */
    static final class CallLog {
        private final List<CoreFunction> calls = Collections.synchronizedList(new ArrayList<>());

        CoreFunctions around(CoreFunctions table) {
            return table.mapped((function, original) -> arguments -> {
                long status = original.call(arguments);
                record(function);
                return status;
            });
        }

        void record(CoreFunction function) {
            calls.add(function);
        }

        List<CoreFunction> calls() {
            synchronized (calls) {
                return Collections.unmodifiableList(new ArrayList<>(calls));
            }
        }

        long count(CoreFunction function) {
            return calls().stream().filter(function::equals).count();
        }

        void clear() {
            calls.clear();
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
                return Collections.unmodifiableList(new ArrayList<>(failures));
            }
        }

        List<Failure> of(FailureKind kind) {
            return failures().stream().filter(failure -> failure.kind() == kind).collect(Collectors.toList());
        }
    }

    /** A listener that keeps every event it hears and the thread it heard it on. */
    static final class RecordingListener implements CoreListener {
        static final class Heard {
            private final CoreEvent event;
            private final Thread thread;

            Heard(CoreEvent event, Thread thread) {
                this.event = event;
                this.thread = thread;
            }

            public CoreEvent event() {
                return event;
            }

            public Thread thread() {
                return thread;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                }
                if (!(other instanceof Heard)) {
                    return false;
                }
                Heard that = (Heard) other;
                return Objects.equals(event, that.event)
                        && Objects.equals(thread, that.thread);
            }

            @Override
            public int hashCode() {
                int hash = 0;
                hash = 31 * hash + Objects.hashCode(event);
                hash = 31 * hash + Objects.hashCode(thread);
                return hash;
            }

            @Override
            public String toString() {
                return "Heard[event=" + event + ", thread=" + thread + "]";
            }
        }

        private final List<Heard> heard = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void hear(CoreEvent event) {
            heard.add(new Heard(event, Thread.currentThread()));
        }

        List<CoreEvent> events() {
            synchronized (heard) {
                return heard.stream().map(Heard::event).collect(Collectors.toList());
            }
        }

        List<Heard> heard() {
            synchronized (heard) {
                return Collections.unmodifiableList(new ArrayList<>(heard));
            }
        }

        <E extends CoreEvent> List<E> of(Class<E> kind) {
            return events().stream().filter(kind::isInstance).map(kind::cast).collect(Collectors.toList());
        }

        void clear() {
            heard.clear();
        }
    }
}
