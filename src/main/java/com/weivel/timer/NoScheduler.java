package com.weivel.timer;

/** {@link Scheduler#none()}: nothing in the layer calls it, so each call is its caller's mistake. */
final class NoScheduler implements Scheduler {
    static final NoScheduler INSTANCE = new NoScheduler();

    private NoScheduler() {
    }

    @Override
    public long now() {
        throw new IllegalStateException("Scheduler.none() has no clock; give the host a scheduler to time a want by");
    }

    @Override
    public Task schedule(long at, Runnable callback) {
        throw new IllegalStateException("Scheduler.none() arms nothing; give the host a scheduler to arm a deadline");
    }
}
