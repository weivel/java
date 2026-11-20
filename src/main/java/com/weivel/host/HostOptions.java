package com.weivel.host;

import com.weivel.context.HostLog;
import com.weivel.core.CoreOptions;
import com.weivel.core.DescriptionCheck;
import com.weivel.core.ErrorSink;
import com.weivel.timer.Scheduler;
import com.weivel.timer.TimerObserver;
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
    private final TimerObserver timerObserver;
    private final Optional<DescriptionCheck> descriptionCheck;

    private HostOptions(CoreOptions core, Optional<Scheduler> scheduler, HostLog log, ErrorSink sink,
                        TimerObserver timerObserver, Optional<DescriptionCheck> descriptionCheck) {
        this.core = core;
        this.scheduler = scheduler;
        this.log = log;
        this.sink = sink;
        this.timerObserver = timerObserver;
        this.descriptionCheck = descriptionCheck;
    }

    /**
     * The core's default options, a scheduler of the host's own, and the log and the error sink writing to the
     * standard error output.
     *
     * @return the options
     */
    public static HostOptions defaults() {
        return new HostOptions(CoreOptions.defaults(), Optional.<Scheduler>empty(), STANDARD_ERROR_LOG,
                ErrorSink.STANDARD_ERROR, TimerObserver.NONE, Optional.<DescriptionCheck>empty());
    }

    /**
     * Replaces the core's options.
     *
     * @param chosen handed to the core's creation unchanged
     * @return the copy
     */
    public HostOptions withCore(CoreOptions chosen) {
        return new HostOptions(Objects.requireNonNull(chosen, "chosen"), scheduler, log, sink, timerObserver,
                descriptionCheck);
    }

    /**
     * Gives the host the caller's clock and scheduler, which the caller keeps owning.
     *
     * @param chosen the scheduler; {@link Scheduler#none()} for no timer
     * @return the copy
     */
    public HostOptions withScheduler(Scheduler chosen) {
        return new HostOptions(core, Optional.of(Objects.requireNonNull(chosen, "chosen")), log, sink,
                timerObserver, descriptionCheck);
    }

    /**
     * Replaces where the plugins' log and the core's own log events go.
     *
     * @param chosen the log
     * @return the copy
     */
    public HostOptions withLog(HostLog chosen) {
        return new HostOptions(core, scheduler, Objects.requireNonNull(chosen, "chosen"), sink, timerObserver,
                descriptionCheck);
    }

    /**
     * Replaces where failures in code the layer calls go.
     *
     * @param chosen the error sink
     * @return the copy
     */
    public HostOptions withErrorSink(ErrorSink chosen) {
        return new HostOptions(core, scheduler, log, Objects.requireNonNull(chosen, "chosen"), timerObserver,
                descriptionCheck);
    }

    /**
     * Gives the host what hears each move of its timer, armed for a deadline or disarmed, as the core announces it.
     *
     * @param chosen the observer, called outside every lock of the layer's; {@link TimerObserver#NONE} for none
     * @return the copy
     */
    public HostOptions withTimerObserver(TimerObserver chosen) {
        return new HostOptions(core, scheduler, log, sink, Objects.requireNonNull(chosen, "chosen"),
                descriptionCheck);
    }

    /**
     * Gives the host the standard's description check, which it runs on every description a library declares before
     * binding the definition it describes. The host never loads the standard itself, so a host given none refuses
     * every library that declares a description.
     *
     * @param chosen the check, as {@code Standard.descriptionCheck()} answers it
     * @return the copy
     */
    public HostOptions withDescriptionCheck(DescriptionCheck chosen) {
        return new HostOptions(core, scheduler, log, sink, timerObserver,
                Optional.of(Objects.requireNonNull(chosen, "chosen")));
    }

    /**
     * Answers {@code descriptionCheck}.
     *
     * @return the standard's description check; empty when the host was given none
     */
    public Optional<DescriptionCheck> descriptionCheck() {
        return descriptionCheck;
    }

    /**
     * Answers {@code timerObserver}.
     *
     * @return what hears each move of the host's timer
     */
    public TimerObserver timerObserver() {
        return timerObserver;
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
