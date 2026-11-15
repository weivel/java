package com.weivel.host;

import static com.weivel.host.HostFixture.consuming;
import static com.weivel.host.HostFixture.value;

import com.weivel.context.ServiceWatcher;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Gates that hold a thread at a point of the host's steps, so another thread's call lands exactly there. */
final class Gates {
    static final long WAIT_SECONDS = 10;

    private Gates() {
    }

    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("a gate was never opened");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    /** A gate one thread waits at, told once it is there, until another opens it. */
    static final class Gate {
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch opened = new CountDownLatch(1);

        void hold() {
            reached.countDown();
            await(opened);
        }

        void awaitReached() {
            await(reached);
        }

        void open() {
            opened.countDown();
        }
    }

    /**
     * A thread dispatching the host's core, held inside a plugin's watcher until {@link #release()}: a plugin
     * watching {@code signal} hears the host register it on that thread, and runs {@code inside} there first.
     */
    static final class BlockedDispatcher {
        private final Gate gate = new Gate();
        private final Thread thread;

        BlockedDispatcher(HostFixture fixture, Runnable inside) {
            TestEntry watcher = fixture.entry("blocking");
            long plugin = fixture.register(consuming("blocking", "signal"), watcher);
            fixture.activate(plugin);
            value(watcher.context.watch("signal", new ServiceWatcher() {
                @Override
                public void registered(Object service) {
                    inside.run();
                    gate.hold();
                }

                @Override
                public void unregistered() {
                }
            }));
            thread = new Thread(() -> fixture.host.registerHostService("signal", new Object()));
            thread.start();
            gate.awaitReached();
        }

        BlockedDispatcher(HostFixture fixture) {
            this(fixture, () -> { });
        }

        Thread thread() {
            return thread;
        }

        boolean isDispatching() {
            return thread.isAlive();
        }

        void release() throws InterruptedException {
            gate.open();
            thread.join();
        }
    }
}
