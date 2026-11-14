package com.weivel.context;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * How long a plugin's want may wait, each limit handed to the core unchanged, with the timer's time as its
 * {@code now}. A want with none waits for as long as it takes. A want with any of them needs the time, so on a host
 * whose scheduler has no clock it is the caller's mistake, answered before anything is asked of the core.
 *
 * @param timeoutMillis how long after now the want gives up; empty for no deadline
 * @param budgetMillis how long a constrained want polls before it degrades or is refused; empty for no budget
 * @param pollMillis how often a constrained want polls, which the core requires of one; empty to give none
 */
public record WantLimits(OptionalLong timeoutMillis, OptionalLong budgetMillis, OptionalLong pollMillis) {
    /**
     * Checks that every limit is present, if only as empty.
     *
     * @param timeoutMillis the timeout, or empty
     * @param budgetMillis the budget, or empty
     * @param pollMillis the poll interval, or empty
     */
    public WantLimits {
        Objects.requireNonNull(timeoutMillis, "timeoutMillis");
        Objects.requireNonNull(budgetMillis, "budgetMillis");
        Objects.requireNonNull(pollMillis, "pollMillis");
    }

    /**
     * A want that waits for as long as it takes.
     *
     * @return limits with none set
     */
    public static WantLimits unbounded() {
        return new WantLimits(OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty());
    }

    /**
     * A want that gives up {@code timeoutMillis} after now.
     *
     * @param timeoutMillis the timeout
     * @return limits with that timeout
     */
    public static WantLimits timeout(long timeoutMillis) {
        return new WantLimits(OptionalLong.of(timeoutMillis), OptionalLong.empty(), OptionalLong.empty());
    }

    /**
     * A constrained want that polls every {@code pollMillis} for as long as it takes.
     *
     * @param pollMillis the poll interval
     * @return limits with that poll interval and no budget
     */
    public static WantLimits polled(long pollMillis) {
        return new WantLimits(OptionalLong.empty(), OptionalLong.empty(), OptionalLong.of(pollMillis));
    }

    /**
     * A constrained want that polls every {@code pollMillis} for {@code budgetMillis}.
     *
     * @param budgetMillis the budget
     * @param pollMillis the poll interval
     * @return limits with that budget and poll interval
     */
    public static WantLimits budgeted(long budgetMillis, long pollMillis) {
        return new WantLimits(OptionalLong.empty(), OptionalLong.of(budgetMillis), OptionalLong.of(pollMillis));
    }

    /** Whether the core reads the want's {@code now}: for a timeout, and for a constrained want. */
    boolean needsTheTime() {
        return timeoutMillis.isPresent() || budgetMillis.isPresent() || pollMillis.isPresent();
    }
}
