package com.weivel.core;

/**
 * Selects the native backend this Java runs on: JNA here, and the foreign function and memory API on Java 22 and
 * later, where the multi-release jar's {@code META-INF/versions/22} carries the class of this name that selects it.
 */
final class NativeBackends {
    private NativeBackends() {
    }

    /** @throws IllegalStateException when the JVM is not 64-bit, which the layer's struct layouts assume */
    static NativeAccess current() {
        Layout.requireAddressSize(JnaAccess.INSTANCE.addressSize());
        return JnaAccess.INSTANCE;
    }
}
