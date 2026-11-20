package com.weivel.core;

/**
 * Who decides whether a call may still enter a library's binary: a {@link BoundDefinition} enters before every native
 * call it makes and leaves once the call returns, so its owner never closes the binary under a call.
 */
public interface CallGate {
    /** A gate that always lets a call in, for a binary its owner keeps open for as long as anything calls it. */
    CallGate OPEN = new CallGate() {
        @Override
        public boolean enter() {
            return true;
        }

        @Override
        public void leave() {
        }
    };

    /**
     * Counts a call in.
     *
     * @return whether it may run; false once the binary is closed, or about to be
     */
    boolean enter();

    /** Counts a call {@link #enter} let in out again. */
    void leave();
}
