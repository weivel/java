package com.weivel.host;

/**
 * Points in the host's steps where a test of the host's own package holds a thread, so that another thread's call
 * lands exactly there: each interleaving the contract names is forced by a gate rather than hoped for.
 */
interface Pauses {
    Pauses NONE = new Pauses() {
    };

    default void beforeTheActivateIsClaimed(long plugin) {
    }

    default void afterTheActivateIsClaimed(long plugin) {
    }

    default void beforeTheVerification(long plugin) {
    }

    default void afterAHostServiceIsRegistered(String id) {
    }

    /** Inside a call through a library's service, counted in and before the library is called. */
    default void whileALibraryCallRuns(String id) {
    }

    /**
     * Just before a library's binary is closed; a test throws here to stand for a platform refusing the close, which
     * no real close of the echo library can be made to do.
     */
    default void beforeALibraryCloses(String importName) {
    }
}
