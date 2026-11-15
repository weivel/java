package com.weivel.core;

/**
 * {@code weivel_get_service} on one core, plugin and id, made through the very function of the core's table the
 * wrapper calls and on the core's own pointer, but past the wrapper: no lock, no drain, no deadline check, and its
 * arguments laid out once. It exists for the benchmarks' control ratio, the wrapper's call over this one, and for
 * nothing else.
 *
 * <p>Only one thread at a time may call it, while no other thread calls the core. Under that precondition, a call
 * made once the core is destroyed is refused before it reaches the core's pointer.
 *
 * @implNote The wrapper hands it the one function, so the table stays the wrapper's alone. It reaches only
 * {@code weivel_get_service}, which changes nothing in the core; a call that leaves an event waiting is answered as a
 * failure, since nothing past the wrapper drains.
 */
public final class BareServiceGet implements AutoCloseable {
    private static final int STATUS_OK = 0;

    private final Wrapper wrapper;
    private final ForeignFunction getService;
    private final ForeignMemory memory;
    private final long core;
    private final long plugin;
    private final long id;
    private final long implementation;
    private final long pending;
    private boolean closed;

    BareServiceGet(Wrapper wrapper, ForeignFunction getService, ForeignMemory memory, long core, long plugin,
                   String id) {
        this.wrapper = wrapper;
        this.getService = getService;
        this.memory = memory;
        this.core = core;
        this.plugin = plugin;
        this.id = memory.text(id);
        this.implementation = memory.allocate(Layout.LONG.byteSize());
        this.pending = memory.allocate(Layout.INT.byteSize());
    }

    /**
     * Makes the call.
     *
     * @return the implementation handle the core answered
     * @throws IllegalStateException once this is closed or the core is destroyed, or when it answers anything but OK
     *     or leaves an event waiting
     */
    public long call() {
        if (closed) {
            throw new IllegalStateException("the bare call is closed, so its arguments are freed");
        }
        if (wrapper.isDestroyed()) {
            throw new IllegalStateException("the core is destroyed, so the bare call reaches it no more");
        }
        int status = (int) getService.call(core, plugin, id, implementation, pending);
        int waiting = memory.getInt(pending, 0);
        if (status != STATUS_OK || waiting != 0) {
            throw new IllegalStateException("the bare weivel_get_service answered status " + status + " with "
                    + waiting + " events waiting");
        }
        return memory.getLong(implementation, 0);
    }

    /** Frees the arguments laid out for the call. */
    @Override
    public void close() {
        closed = true;
        memory.close();
    }
}
