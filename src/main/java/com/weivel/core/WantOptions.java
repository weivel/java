package com.weivel.core;

import java.util.Objects;

/**
 * How long a want may wait, mirroring the core's {@code weivel_want_options}, handed to the core unchanged.
 *
 * @param timed whether the want has a deadline; the default is none
 * @param now the caller's time now, in its own milliseconds
 * @param timeoutMillis how long after {@code now} a timed want gives up
 * @param budgeted whether a constrained want has a budget; the default is none
 * @param budgetMillis how long a budgeted constrained want polls
 * @param pollMillis how often a constrained want polls
 */
public record WantOptions(Choice timed, long now, long timeoutMillis, Choice budgeted, long budgetMillis,
                          long pollMillis) {
    /**
     * Checks that both choices are present.
     *
     * @param timed whether the want has a deadline
     * @param now the time now
     * @param timeoutMillis the timeout
     * @param budgeted whether a constrained want has a budget
     * @param budgetMillis the budget
     * @param pollMillis the poll interval
     */
    public WantOptions {
        Objects.requireNonNull(timed, "timed");
        Objects.requireNonNull(budgeted, "budgeted");
    }

    /**
     * Options for a want that waits for as long as it takes.
     *
     * @return options with no deadline
     */
    public static WantOptions unbounded() {
        return new WantOptions(Choice.DEFAULT, 0, 0, Choice.DEFAULT, 0, 0);
    }

    /**
     * Options for a want that gives up {@code timeoutMillis} after {@code now}.
     *
     * @param now the caller's time now
     * @param timeoutMillis how long it waits
     * @return options with that deadline
     */
    public static WantOptions timed(long now, long timeoutMillis) {
        return new WantOptions(Choice.YES, now, timeoutMillis, Choice.DEFAULT, 0, 0);
    }
}
