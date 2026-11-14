package com.weivel.core;

import java.nio.file.Path;

/** What loading the core's binary answered: the loaded binary, or why there is none. */
public sealed interface CoreLoad permits CoreLoad.Loaded, CoreLoad.NotALibrary, CoreLoad.MissingFunction {
    /**
     * The binary loaded, with every core function resolved.
     *
     * @param binary the loaded binary, which its caller closes
     */
    record Loaded(CoreBinary binary) implements CoreLoad {
    }

    /**
     * The path names no library the platform's loader could open.
     *
     * @param path the path the caller gave
     * @param reason what the loader said
     */
    record NotALibrary(Path path, String reason) implements CoreLoad {
    }

    /**
     * The library opened but lacks a function of the core, so it is no core, or a core older than the layer.
     *
     * @param path the path the caller gave
     * @param function the symbol of the first core function, in the header's order, the library lacks
     */
    record MissingFunction(Path path, String function) implements CoreLoad {
    }
}
