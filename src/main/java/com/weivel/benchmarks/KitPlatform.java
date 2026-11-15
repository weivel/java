package com.weivel.benchmarks;

import java.util.Locale;
import java.util.Optional;

/** The kit's name for the system a run is on: {@code linux}, {@code windows} or {@code macos}. */
public final class KitPlatform {
    private KitPlatform() {
    }

    /**
     * The kit's name for a system, as Java's {@code os.name} names it.
     *
     * @param osName the system's name
     * @return the kit's platform; empty for a system the kit names no bound for
     */
    public static Optional<String> of(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("linux")) {
            return Optional.of("linux");
        }
        if (name.startsWith("windows")) {
            return Optional.of("windows");
        }
        if (name.startsWith("mac os x") || name.startsWith("macos")) {
            return Optional.of("macos");
        }
        return Optional.empty();
    }

    /**
     * The kit's name for the system this JVM runs on.
     *
     * @return the kit's platform; empty for a system the kit names no bound for
     */
    public static Optional<String> current() {
        return of(System.getProperty("os.name", ""));
    }
}
