package com.weivel.standard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What one scan of a directory found, its {@code weivel_standard_discovery} copied: the plugins in the standard's
 * order, and every file it could not take a plugin from.
 */
public final class Discovery {
    private final List<DiscoveredPlugin> plugins;
    private final List<Failure> failures;

    /**
     * Makes the value.
     *
     * @param plugins the plugins, ordered by identity and then by importName
     * @param failures the files no plugin was taken from, in the standard's order
     */
    public Discovery(List<DiscoveredPlugin> plugins, List<Failure> failures) {
        this.plugins = Collections.unmodifiableList(new ArrayList<>(plugins));
        this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
    }

    /**
     * Answers {@code plugins}.
     *
     * @return the plugins, unmodifiable
     */
    public List<DiscoveredPlugin> plugins() {
        return plugins;
    }

    /**
     * Answers {@code failures}.
     *
     * @return the failures, unmodifiable
     */
    public List<Failure> failures() {
        return failures;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Discovery && plugins.equals(((Discovery) other).plugins)
                && failures.equals(((Discovery) other).failures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugins, failures);
    }

    @Override
    public String toString() {
        return "Discovery[plugins=" + plugins + ", failures=" + failures + "]";
    }

    /** A file discovery could not take a plugin from, and why, as {@code weivel_standard_discovery_failure}. */
    public static final class Failure {
        private final String file;
        private final StandardRefusal refusal;

        /**
         * Makes the value.
         *
         * @param file the file's path
         * @param refusal why
         */
        public Failure(String file, StandardRefusal refusal) {
            this.file = Objects.requireNonNull(file, "file");
            this.refusal = Objects.requireNonNull(refusal, "refusal");
        }

        /**
         * Answers {@code file}.
         *
         * @return the file's path
         */
        public String file() {
            return file;
        }

        /**
         * Answers {@code refusal}.
         *
         * @return why
         */
        public StandardRefusal refusal() {
            return refusal;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Failure && file.equals(((Failure) other).file)
                    && refusal.equals(((Failure) other).refusal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, refusal);
        }

        @Override
        public String toString() {
            return "Failure[file=" + file + ", refusal=" + refusal + "]";
        }
    }
}
