package com.weivel.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.CoreProbes;
import com.weivel.core.Failure;
import com.weivel.host.Host;
import com.weivel.host.HostOptions;
import com.weivel.timer.Scheduler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The standard's real binary, loaded once and never closed, and the files the build hands the tests. */
final class StandardFixture {
    private static final Standard STANDARD = Standard.of(CoreProbes.ok(StandardBinary.load(file(
            "weivel.test.standardBinary"))));

    private StandardFixture() {
    }

    static Standard standard() {
        return STANDARD;
    }

    static Path file(String property) {
        String location = System.getProperty(property);
        if (location == null) {
            throw new IllegalStateException(property + " is not set. Run the tests through Gradle, which sets it.");
        }
        Path path = java.nio.file.Paths.get(location);
        if (!Files.exists(path)) {
            throw new IllegalStateException(property + " names " + path + ", which does not exist.");
        }
        return path;
    }

    static <T> T value(StandardAnswer<T> answer) {
        assertEquals(StandardStatus.OK, answer.status(), answer.toString());
        assertTrue(answer.value().isPresent(), answer.toString());
        return answer.value().get();
    }

    static StandardRefusal refusal(StandardAnswer<?> answer) {
        assertEquals(StandardStatus.REFUSED, answer.status(), answer.toString());
        assertTrue(answer.refusal().isPresent(), answer.toString());
        return answer.refusal().get();
    }

    /** A host on the real core that arms no timer, every failure kept. */
    static final class TestHost implements AutoCloseable {
        final List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
        final Host host;

        TestHost() {
            Answer<Host> created = Host.create(CoreProbes.binary(), HostOptions.defaults()
                    .withScheduler(Scheduler.none()).withErrorSink(failures::add));
            host = CoreProbes.ok(created);
        }

        @Override
        public void close() throws Exception {
            host.destroy().toCompletableFuture().get(10, TimeUnit.SECONDS);
            host.close();
        }
    }
}
