package com.weivel.core;

/**
 * Selects the native backend this Java runs on: the foreign function and memory API, final since Java 22. This class
 * replaces the base one of the same name in the multi-release jar's {@code META-INF/versions/22}.
 */
final class NativeBackends {
    private NativeBackends() {
    }

    /**
     * The backend this Java runs on.
     *
     * @return the foreign function and memory API's
     */
    static NativeAccess current() {
        return FfmAccess.INSTANCE;
    }
}
