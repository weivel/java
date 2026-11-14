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
     * @throws IllegalStateException when the JVM is not 64-bit, which the layer's struct layouts assume
     */
    static NativeAccess current() {
        Layout.requireAddressSize(FfmAccess.INSTANCE.addressSize());
        return FfmAccess.INSTANCE;
    }
}
