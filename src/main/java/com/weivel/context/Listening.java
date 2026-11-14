package com.weivel.context;

/** A plugin's listener, until it stops. */
public final class Listening {
    private final Runnable stopping;

    Listening(Runnable stopping) {
        this.stopping = stopping;
    }

    /** Stops the listener; a dispatch on another thread may still be telling it the one event it had begun. */
    public void stop() {
        stopping.run();
    }
}
