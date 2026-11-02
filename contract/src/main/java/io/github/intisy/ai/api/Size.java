package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsEnum;
import io.github.intisy.ai.tsemit.TsLiteral;

/** A node's size as a role, never a pixel value. */
@TsEnum
public enum Size {
    /** Smaller than the host's default. */
    @TsLiteral("small")
    SMALL,

    /** The host's default size. */
    @TsLiteral("medium")
    MEDIUM,

    /** Larger than the host's default. */
    @TsLiteral("large")
    LARGE
}
