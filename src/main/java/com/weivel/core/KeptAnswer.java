package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An answer kept past its call because it carried a definition, which lives exactly as long as it: its release
 * refuses every new call through each definition it carried at once, waits for the calls still running in them,
 * releases what was got through them by their own releases, and only then frees the answer through the release its
 * description names. It is freed exactly once, by its release, its library's unload, or at the latest its binary's
 * close.
 */
final class KeptAnswer {
    private final DescribedHandles holdings;
    private final List<Carried> carried;
    private final Runnable releaseAnswer;
    private boolean released;
    private boolean freed;

    /**
     * @param releaseAnswer frees the answer through the release its description names and then the memory it was
     *     answered in, run once with the binary open
     */
    KeptAnswer(DescribedHandles holdings, List<Carried> carried, Runnable releaseAnswer) {
        this.holdings = holdings;
        this.carried = Collections.unmodifiableList(new ArrayList<>(carried));
        this.releaseAnswer = releaseAnswer;
    }

    /** Ends the answer, as {@link KeptAnswer} says; a later call does nothing. */
    void release() {
        synchronized (this) {
            if (released) {
                return;
            }
            released = true;
        }
        AtomicInteger ending = new AtomicInteger(carried.size());
        for (Carried definition : carried) {
            definition.scope.close(() -> {
                try {
                    definition.holdings.releaseAll();
                } finally {
                    if (ending.decrementAndGet() == 0) {
                        holdings.free(this);
                    }
                }
            });
        }
    }

    /**
     * Takes the one free there is, for a caller about to free it.
     *
     * @return false when it was freed already
     */
    synchronized boolean takeTheFree() {
        if (freed) {
            return false;
        }
        freed = true;
        return true;
    }

    /**
     * Marks it released as its binary closes, every definition it carried closed to calls, with nothing left to call.
     *
     * @return whether this close frees it, which it does for every answer not freed yet
     */
    boolean releaseAtTheClose() {
        synchronized (this) {
            released = true;
        }
        for (Carried definition : carried) {
            definition.scope.close(() -> { });
        }
        return takeTheFree();
    }

    DescribedHandles holdings() {
        return holdings;
    }

    void free() {
        releaseAnswer.run();
    }

    /** One definition the answer carried: the scope its calls run in, and what was got through it. */
    static final class Carried {
        final CarriedScope scope;
        final DescribedHandles holdings;

        Carried(CarriedScope scope, DescribedHandles holdings) {
            this.scope = scope;
            this.holdings = holdings;
        }
    }
}
