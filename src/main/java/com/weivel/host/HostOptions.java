package com.weivel.host;

import com.weivel.context.HostLog;
import com.weivel.core.CoreOptions;
import com.weivel.core.ErrorSink;
import com.weivel.timer.Scheduler;
import java.util.Objects;
import java.util.Optional;

/** How a host is made. Each {@code with} method answers a copy with one part replaced. */
public final class HostOptions {
    private static final HostLog STANDARD_ERROR_LOG = record -> System.err.println("weivel " + record.level() + " "
            + record.plugin().map(plugin -> plugin + ": ").orElse("") + record.text());

    private final CoreOptions core;
    private final Optional<Scheduler> scheduler;
    private final HostLog log;
    private final ErrorSink sink;

    private HostOptions(CoreOptions core, Optional<Scheduler> scheduler, HostLog log, ErrorSink sink) {
        this.core = core;
        this.scheduler = scheduler;
        this.log = log;
        this.sink = sink;
    }

    /**
     * The core's default options, a scheduler of the host's own, and the log and the error sink writing to the
     * standard error output.
     *
     * @return the options
     */
    public static HostOptions defaults() {
        return new HostOptions(CoreOptions.defaults(), Optional.<Scheduler>empty(), STANDARD_ERROR_LOG,
                ErrorSink.STANDARD_ERROR);
    }

    /**
     * Replaces the core's options.
     *
     * @param chosen handed to the core's creation unchanged
     * @return the copy
     */
    public HostOptions withCore(CoreOptions chosen) {
        return new HostOptions(Objects.requireNonNull(chosen, "chosen"), scheduler, log, sink);
    }

    /**
     * Gives the host the caller's clock and scheduler, which the caller keeps owning.
     *
     * @param chosen the scheduler; {@link Scheduler#none()} for no timer
     * @return the copy
     */
    public HostOptions withScheduler(Scheduler chosen) {
        return new HostOptions(core, Optional.of(Objects.requireNonNull(chosen, "chosen")), log, sink);
    }

    /**
     * Replaces where the plugins' log and the core's own log events go.
     *
     * @param chosen the log
     * @return the copy
     */
    public HostOptions withLog(HostLog chosen) {
        return new HostOptions(core, scheduler, Objects.requireNonNull(chosen, "chosen"), sink);
    }

    /**
     * Replaces where failures in code the layer calls go.
     *
     * @param chosen the error sink
     * @return the copy
     */
    public HostOptions withErrorSink(ErrorSink chosen) {
        return new HostOptions(core, scheduler, log, Objects.requireNonNull(chosen, "chosen"));
    }

    /**
     * Answers {@code core}.
     *
     * @return the core's options
     */
    public CoreOptions core() {
        return core;
    }

    /**
     * Answers {@code scheduler}.
     *
     * @return the caller's scheduler; empty when the host makes one of its own with {@code Schedulers.system()}
     *     and closes it when it is closed
     */
    public Optional<Scheduler> scheduler() {
        return scheduler;
    }

    /**
     * Answers {@code log}.
     *
     * @return where the plugins' log and the core's own log events go
     */
    public HostLog log() {
        return log;
    }

    /**
     * Answers {@code sink}.
     *
     * @return where failures in code the layer calls go
     */
    public ErrorSink sink() {
        return sink;
    }
}
