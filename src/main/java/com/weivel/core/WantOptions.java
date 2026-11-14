package com.weivel.core;

import java.util.Objects;

/**
 * How long a want may wait, mirroring the core's {@code weivel_want_options}, handed to the core unchanged.
 */
public final class WantOptions {
    private final Choice timed;
    private final long now;
    private final long timeoutMillis;
    private final Choice budgeted;
    private final long budgetMillis;
    private final long pollMillis;

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
    public WantOptions(Choice timed, long now, long timeoutMillis, Choice budgeted, long budgetMillis,
            long pollMillis) {
        Objects.requireNonNull(timed, "timed");
        Objects.requireNonNull(budgeted, "budgeted");
        this.timed = timed;
        this.now = now;
        this.timeoutMillis = timeoutMillis;
        this.budgeted = budgeted;
        this.budgetMillis = budgetMillis;
        this.pollMillis = pollMillis;
    }

    /**
     * Answers {@code timed}.
     *
     * @return whether the want has a deadline; the default is none
     */
    public Choice timed() {
        return timed;
    }

    /**
     * Answers {@code now}.
     *
     * @return the caller's time now, in its own milliseconds
     */
    public long now() {
        return now;
    }

    /**
     * Answers {@code timeoutMillis}.
     *
     * @return how long after {@code now} a timed want gives up
     */
    public long timeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Answers {@code budgeted}.
     *
     * @return whether a constrained want has a budget; the default is none
     */
    public Choice budgeted() {
        return budgeted;
    }

    /**
     * Answers {@code budgetMillis}.
     *
     * @return how long a budgeted constrained want polls
     */
    public long budgetMillis() {
        return budgetMillis;
    }

    /**
     * Answers {@code pollMillis}.
     *
     * @return how often a constrained want polls
     */
    public long pollMillis() {
        return pollMillis;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WantOptions)) {
            return false;
        }
        WantOptions that = (WantOptions) other;
        return Objects.equals(timed, that.timed)
                && now == that.now
                && timeoutMillis == that.timeoutMillis
                && Objects.equals(budgeted, that.budgeted)
                && budgetMillis == that.budgetMillis
                && pollMillis == that.pollMillis;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(timed);
        hash = 31 * hash + Long.hashCode(now);
        hash = 31 * hash + Long.hashCode(timeoutMillis);
        hash = 31 * hash + Objects.hashCode(budgeted);
        hash = 31 * hash + Long.hashCode(budgetMillis);
        hash = 31 * hash + Long.hashCode(pollMillis);
        return hash;
    }

    @Override
    public String toString() {
        return "WantOptions[timed=" + timed + ", now=" + now + ", timeoutMillis=" + timeoutMillis + ", budgeted="
                + budgeted + ", budgetMillis=" + budgetMillis + ", pollMillis=" + pollMillis + "]";
    }
}
