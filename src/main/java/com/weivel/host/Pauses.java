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
}
