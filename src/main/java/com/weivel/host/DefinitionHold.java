package com.weivel.host;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The hold one definition service keeps on its library's binary, from before its registration until the core reports
 * that nothing holds the registration any more, or until the host is freed, whichever comes first. It ends once.
 */
final class DefinitionHold implements Runnable {
    private final AtomicBoolean ended = new AtomicBoolean();
    private final LibraryLifetime lifetime;
    private final Set<DefinitionHold> held;

    /** Counts the hold among {@code held}, which the host ends when it is freed. */
    DefinitionHold(LibraryLifetime lifetime, Set<DefinitionHold> held) {
        this.lifetime = lifetime;
        this.held = held;
        held.add(this);
    }

    @Override
    public void run() {
        if (ended.compareAndSet(false, true)) {
            held.remove(this);
            lifetime.leave();
        }
    }
}
