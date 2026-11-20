package com.weivel.host;

import static com.weivel.core.CoreProbes.ok;

import com.weivel.context.LogRecord;
import com.weivel.core.Answer;
import com.weivel.core.Choice;
import com.weivel.core.CoreOptions;
import com.weivel.core.CoreProbes;
import com.weivel.core.DescriptionCheck;
import com.weivel.core.Failure;
import com.weivel.core.FailureKind;
import com.weivel.core.Manifest;
import com.weivel.core.NamedNumber;
import com.weivel.timer.TestScheduler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** A host on the real core, its scheduler the test's own, with every failure, log line and trace record kept. */
final class HostFixture implements AutoCloseable {
    private static final CoreOptions INFO_AND_ABOVE = new CoreOptions(0, 2, 0, Collections.<String>emptyList(),
            Collections.<String>emptyList(), Collections.<CoreOptions.ConstraintEntry>emptyList());

    final List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
    final List<LogRecord> logs = Collections.synchronizedList(new ArrayList<>());
    final List<String> trace = Collections.synchronizedList(new ArrayList<>());
    final TestScheduler scheduler = new TestScheduler();
    final Host host;

    HostFixture() {
        this(Opening.THROUGH_THE_CONTEXTS);
    }

    HostFixture(Opening opening) {
        this(opening, Pauses.NONE);
    }

    HostFixture(Pauses pauses) {
        this(Opening.THROUGH_THE_CONTEXTS, pauses);
    }

    HostFixture(Opening opening, Pauses pauses) {
        this(opening, pauses, Optional.<DescriptionCheck>empty());
    }

    /** A host given the standard's description check, as one that binds described libraries is made. */
    HostFixture(DescriptionCheck check) {
        this(Opening.THROUGH_THE_CONTEXTS, Pauses.NONE, Optional.of(check));
    }

    private HostFixture(Opening opening, Pauses pauses, Optional<DescriptionCheck> check) {
        HostOptions options = HostOptions.defaults().withCore(INFO_AND_ABOVE).withScheduler(scheduler)
                .withLog(logs::add).withErrorSink(failures::add);
        host = ok(Host.create(CoreProbes.binary(), check.isPresent() ? options.withDescriptionCheck(check.get())
                : options, opening, pauses));
    }

    TestEntry entry(String name) {
        return new TestEntry(name, trace);
    }

    long register(Manifest manifest, Entry entry) {
        return ok(host.register(manifest, entry));
    }

    /** Activates the plugins under services, answering the activation. */
    Activation activate(Long... plugins) {
        return ok(host.activate(Arrays.asList(plugins), com.weivel.core.EdgeSource.SERVICES));
    }

    List<Failure> failuresOf(FailureKind kind) {
        synchronized (failures) {
            return failures.stream().filter(failure -> failure.kind() == kind).collect(Collectors.toList());
        }
    }

    List<String> trace() {
        synchronized (trace) {
            return new ArrayList<>(trace);
        }
    }

    @Override
    public void close() throws Exception {
        host.destroy().toCompletableFuture().get(10, TimeUnit.SECONDS);
        host.close();
    }

    static <T> T done(CompletionStage<T> stage) {
        CompletableFuture<T> future = stage.toCompletableFuture();
        if (!future.isDone()) {
            throw new AssertionError("the stage has not completed");
        }
        return future.join();
    }

    static <T> T value(Answer<T> answer) {
        return ok(answer);
    }

    static Manifest services(String importName, List<String> provides, List<String> consumes) {
        return new Manifest(importName, 0, Optional.empty(), Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                provides.stream().map(id -> new Manifest.ServiceOffer(id, Collections.<NamedNumber>emptyList()))
                        .collect(Collectors.toList()),
                consumes.stream().map(id -> new Manifest.ServiceWant(id, Optional.<String>empty(), Choice.NO,
                        Choice.NO, Collections.<NamedNumber>emptyList())).collect(Collectors.toList()),
                Collections.<String>emptyList(), Optional.empty());
    }

    /** A manifest providing {@code provides} and consuming each of {@code consumes} optionally. */
    static Manifest optionally(String importName, List<String> provides, String... consumes) {
        return new Manifest(importName, 0, Optional.empty(), Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                provides.stream().map(id -> new Manifest.ServiceOffer(id, Collections.<NamedNumber>emptyList()))
                        .collect(Collectors.toList()),
                Arrays.stream(consumes).map(id -> new Manifest.ServiceWant(id, Optional.<String>empty(), Choice.YES,
                        Choice.NO, Collections.<NamedNumber>emptyList())).collect(Collectors.toList()),
                Collections.<String>emptyList(), Optional.empty());
    }

    static Manifest consuming(String importName, String... consumes) {
        return optionally(importName, Collections.<String>emptyList(), consumes);
    }

    static Manifest named(String importName) {
        return services(importName, Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    /** A manifest providing {@code capability} and consuming each of {@code consumes} optionally. */
    static Manifest providingCapability(String importName, String capability, String... consumes) {
        Manifest consuming = consuming(importName, consumes);
        return new Manifest(importName, 0, Optional.empty(), Collections.<String>emptyList(),
                Collections.singletonList(capability), Collections.<String>emptyList(),
                Collections.<Manifest.ServiceOffer>emptyList(), consuming.servicesConsumed(),
                Collections.<String>emptyList(), Optional.empty());
    }
}
