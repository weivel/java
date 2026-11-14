package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What a core is created with, mirroring the core's {@code weivel_core_options}: a zero means the core's default. The
 * layer hands it to the core unchanged, so a value the core calls malformed is its caller's mistake.
 */
public final class CoreOptions {
    private final int initialEventCapacity;
    private final int minimumLogLevel;
    private final int api;
    private final List<String> applications;
    private final List<String> wellKnownServices;
    private final List<ConstraintEntry> constraintVocabulary;

    /**
     * Copies every list, so a caller's later change reaches nothing.
     *
     * @param initialEventCapacity the initial event capacity, or 0
     * @param minimumLogLevel the least log level queued, or 0
     * @param api the host's api level, or 0
     * @param applications the applications hosted
     * @param wellKnownServices the well-known service ids
     * @param constraintVocabulary the constraint vocabulary
     */
    public CoreOptions(int initialEventCapacity, int minimumLogLevel, int api, List<String> applications,
            List<String> wellKnownServices, List<ConstraintEntry> constraintVocabulary) {
        applications = Collections.unmodifiableList(new ArrayList<>(applications));
        wellKnownServices = Collections.unmodifiableList(new ArrayList<>(wellKnownServices));
        constraintVocabulary = Collections.unmodifiableList(new ArrayList<>(constraintVocabulary));
        this.initialEventCapacity = initialEventCapacity;
        this.minimumLogLevel = minimumLogLevel;
        this.api = api;
        this.applications = applications;
        this.wellKnownServices = wellKnownServices;
        this.constraintVocabulary = constraintVocabulary;
    }

    /**
     * Answers {@code initialEventCapacity}.
     *
     * @return how many events the queue holds before it first grows; 0 for the core's default
     */
    public int initialEventCapacity() {
        return initialEventCapacity;
    }

    /**
     * Answers {@code minimumLogLevel}.
     *
     * @return the least log level the core queues; 0 for the core's default
     */
    public int minimumLogLevel() {
        return minimumLogLevel;
    }

    /**
     * Answers {@code api}.
     *
     * @return the highest api level the host implements; 0 for the core's own
     */
    public int api() {
        return api;
    }

    /**
     * Answers {@code applications}.
     *
     * @return the applications the host hosts; empty means every application
     */
    public List<String> applications() {
        return applications;
    }

    /**
     * Answers {@code wellKnownServices}.
     *
     * @return the service ids any plugin may register
     */
    public List<String> wellKnownServices() {
        return wellKnownServices;
    }

    /**
     * Answers {@code constraintVocabulary}.
     *
     * @return which constraint compares against which attribute, and how
     */
    public List<ConstraintEntry> constraintVocabulary() {
        return constraintVocabulary;
    }

    /**
     * Options asking the core for every default.
     *
     * @return options all zero and empty
     */
    public static CoreOptions defaults() {
        return new CoreOptions(0, 0, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * One entry of the host's constraint vocabulary, mirroring {@code weivel_constraint_entry}.
     */
    public static final class ConstraintEntry {
        private final String constraintName;
        private final String attributeName;
        private final int bound;

        /**
         * Checks that both names are present.
         *
         * @param constraintName the constraint's name
         * @param attributeName the attribute's name
         * @param bound the bound
         */
        public ConstraintEntry(String constraintName, String attributeName, int bound) {
            Objects.requireNonNull(constraintName, "constraintName");
            Objects.requireNonNull(attributeName, "attributeName");
            this.constraintName = constraintName;
            this.attributeName = attributeName;
            this.bound = bound;
        }

        /**
         * Answers {@code constraintName}.
         *
         * @return the constraint's name
         */
        public String constraintName() {
            return constraintName;
        }

        /**
         * Answers {@code attributeName}.
         *
         * @return the attribute it is compared against
         */
        public String attributeName() {
            return attributeName;
        }

        /**
         * Answers {@code bound}.
         *
         * @return a bound of the core's header: 1 for a lower bound, 2 for an upper one
         */
        public int bound() {
            return bound;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstraintEntry)) {
                return false;
            }
            ConstraintEntry that = (ConstraintEntry) other;
            return Objects.equals(constraintName, that.constraintName)
                    && Objects.equals(attributeName, that.attributeName)
                    && bound == that.bound;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(constraintName);
            hash = 31 * hash + Objects.hashCode(attributeName);
            hash = 31 * hash + Integer.hashCode(bound);
            return hash;
        }

        @Override
        public String toString() {
            return "ConstraintEntry[constraintName=" + constraintName + ", attributeName=" + attributeName
                    + ", bound=" + bound + "]";
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CoreOptions)) {
            return false;
        }
        CoreOptions that = (CoreOptions) other;
        return initialEventCapacity == that.initialEventCapacity
                && minimumLogLevel == that.minimumLogLevel
                && api == that.api
                && Objects.equals(applications, that.applications)
                && Objects.equals(wellKnownServices, that.wellKnownServices)
                && Objects.equals(constraintVocabulary, that.constraintVocabulary);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(initialEventCapacity);
        hash = 31 * hash + Integer.hashCode(minimumLogLevel);
        hash = 31 * hash + Integer.hashCode(api);
        hash = 31 * hash + Objects.hashCode(applications);
        hash = 31 * hash + Objects.hashCode(wellKnownServices);
        hash = 31 * hash + Objects.hashCode(constraintVocabulary);
        return hash;
    }

    @Override
    public String toString() {
        return "CoreOptions[initialEventCapacity=" + initialEventCapacity + ", minimumLogLevel=" + minimumLogLevel
                + ", api=" + api + ", applications=" + applications + ", wellKnownServices=" + wellKnownServices
                + ", constraintVocabulary=" + constraintVocabulary + "]";
    }
}
