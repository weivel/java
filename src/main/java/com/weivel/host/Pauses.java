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

    /** A load has checked the ids its declaration lists, and is about to keep the library among the host's. */
    default void beforeALibraryIsKept(String importName) {
    }

    /** Inside a host registration's guard, under the layer lock: its id is claimed and the core not yet asked. */
    default void afterAHostServiceIsClaimed(String id) {
    }

    /** A load registered one of its library's services; a test throws here to stand for a defect of the layer. */
    default void afterALibraryServiceIsRegistered(String id) {
    }

    /** A load registered every service of its library and has not yet answered. */
    default void afterALibrarysServicesAreRegistered(String importName) {
    }

    /** An unload closed its library to calls and is about to unregister its services. */
    default void beforeALibrarysServicesAreUnregistered(String importName) {
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
