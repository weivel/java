package io.github.intisy.ai.seam.jvm.library;

import com.sun.jna.NativeLibrary;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Counts the bridges holding each loaded binary, so closing one never unloads it under another.
 *
 * @implNote JNA 5.19.1 caches a {@link NativeLibrary} by path: a second {@code getInstance} for the
 * same absolute path answers the very instance the first did, and closing either unloads the
 * binary for both, so the next call through the other is {@code Invalid memory access} (measured).
 * Loading and the final close share one lock, so a load can never be handed an instance a
 * concurrent close is about to unload.
 */
final class SharedNativeLibraries {

    private static final Map<NativeLibrary, Integer> HOLDERS = new IdentityHashMap<NativeLibrary, Integer>();

    private SharedNativeLibraries() {
    }

    static NativeLibrary hold(String absolutePath) {
        synchronized (HOLDERS) {
            NativeLibrary loaded = NativeLibrary.getInstance(absolutePath);
            Integer holders = HOLDERS.get(loaded);
            HOLDERS.put(loaded, holders == null ? 1 : holders + 1);
            return loaded;
        }
    }

    static void letGo(NativeLibrary loaded) {
        synchronized (HOLDERS) {
            Integer holders = HOLDERS.get(loaded);
            if (holders == null || holders <= 1) {
                HOLDERS.remove(loaded);
                loaded.close();
            } else {
                HOLDERS.put(loaded, holders - 1);
            }
        }
    }
}
