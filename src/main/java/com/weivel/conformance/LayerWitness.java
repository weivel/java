package com.weivel.conformance;

import com.weivel.context.LogRecord;
import com.weivel.core.Failure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** What the driver saw of one vector's run. */
public final class LayerWitness {
    private final String id;
    private final Map<String, Object> observed;
    private final List<LogRecord> logs;
    private final List<Failure> failures;
    private final Optional<String> loadMistake;

    LayerWitness(String id, Map<String, Object> observed, List<LogRecord> logs, List<Failure> failures,
                 Optional<String> loadMistake) {
        this.id = Objects.requireNonNull(id, "id");
        this.observed = Collections.unmodifiableMap(observed);
        this.logs = Collections.unmodifiableList(new ArrayList<>(logs));
        this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        this.loadMistake = loadMistake;
    }

    /**
     * Answers {@code loadMistake}.
     *
     * @return why the host refused the vector's load as the caller's mistake; empty when it loaded, or made no load
     */
    public Optional<String> loadMistake() {
        return loadMistake;
    }

    /**
     * Answers {@code id}.
     *
     * @return the vector's id
     */
    public String id() {
        return id;
    }

    /**
     * Every field of the vector's {@code then}, in its shape: an {@code equals} field as {@code {equals: observed}},
     * a {@code before} field as each pair asked, marked when the run broke it, and any other field as the observed
     * value; so it equals {@link LayerVector#then()} exactly when the run witnessed the vector.
     *
     * @return the observed fields
     */
    public Map<String, Object> observed() {
        return observed;
    }

    /**
     * Answers {@code logs}.
     *
     * @return every record the host's log received during the run, in order
     */
    public List<LogRecord> logs() {
        return logs;
    }

    /**
     * Answers {@code failures}.
     *
     * @return every failure the host's error sink received during the run, in order
     */
    public List<Failure> failures() {
        return failures;
    }
}
