package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsEnum;
import io.github.intisy.ai.tsemit.TsLiteral;

/** A semantic colour role; a host maps it to its own palette. */
@TsEnum
public enum UiTone {
    /** No role: the host's ordinary colour. */
    @TsLiteral("neutral")
    NEUTRAL,

    /** The host's accent colour, for what the view is about. */
    @TsLiteral("accent")
    ACCENT,

    /** Something went right. */
    @TsLiteral("success")
    SUCCESS,

    /** Something needs attention. */
    @TsLiteral("warning")
    WARNING,

    /** Something went wrong, or an action cannot be undone. */
    @TsLiteral("danger")
    DANGER
}
