package com.weivel.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What a host's destroy did, once it has finished. */
public final class DestroyResult {
    private final List<Long> released;

    DestroyResult(List<Long> released) {
        this.released = Collections.unmodifiableList(new ArrayList<>(released));
    }

    /**
     * Answers {@code released}.
     *
     * @return the plugins the destroy itself released, in the order it released them: the reverse of the order they
     *     were opened
     */
    public List<Long> released() {
        return released;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DestroyResult && released.equals(((DestroyResult) other).released);
    }

    @Override
    public int hashCode() {
        return released.hashCode();
    }

    @Override
    public String toString() {
        return "DestroyResult[released=" + released + "]";
    }
}
