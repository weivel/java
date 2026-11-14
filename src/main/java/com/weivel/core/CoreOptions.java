package com.weivel.core;

import java.util.List;
import java.util.Objects;

/**
 * What a core is created with, mirroring the core's {@code weivel_core_options}: a zero means the core's default. The
 * layer hands it to the core unchanged, so a value the core calls malformed is its caller's mistake.
 *
 * @param initialEventCapacity how many events the queue holds before it first grows; 0 for the core's default
 * @param minimumLogLevel the least log level the core queues; 0 for the core's default
 * @param api the highest api level the host implements; 0 for the core's own
 * @param applications the applications the host hosts; empty means every application
 * @param wellKnownServices the service ids any plugin may register
 * @param constraintVocabulary which constraint compares against which attribute, and how
 */
public record CoreOptions(int initialEventCapacity, int minimumLogLevel, int api, List<String> applications,
                          List<String> wellKnownServices, List<ConstraintEntry> constraintVocabulary) {
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
    public CoreOptions {
        applications = List.copyOf(applications);
        wellKnownServices = List.copyOf(wellKnownServices);
        constraintVocabulary = List.copyOf(constraintVocabulary);
    }

    /**
     * Options asking the core for every default.
     *
     * @return options all zero and empty
     */
    public static CoreOptions defaults() {
        return new CoreOptions(0, 0, 0, List.of(), List.of(), List.of());
    }

    /**
     * One entry of the host's constraint vocabulary, mirroring {@code weivel_constraint_entry}.
     *
     * @param constraintName the constraint's name
     * @param attributeName the attribute it is compared against
     * @param bound a bound of the core's header: 1 for a lower bound, 2 for an upper one
     */
    public record ConstraintEntry(String constraintName, String attributeName, int bound) {
        /**
         * Checks that both names are present.
         *
         * @param constraintName the constraint's name
         * @param attributeName the attribute's name
         * @param bound the bound
         */
        public ConstraintEntry {
            Objects.requireNonNull(constraintName, "constraintName");
            Objects.requireNonNull(attributeName, "attributeName");
        }
    }
}
