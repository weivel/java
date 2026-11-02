package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsEnum;
import io.github.intisy.ai.tsemit.TsLiteral;

/** The form a chart node draws. */
@TsEnum
public enum UiChart {
    /** A line over a series. */
    @TsLiteral("line")
    LINE,

    /** Bars, one per value. */
    @TsLiteral("bar")
    BAR,

    /** A small line with no axes, inline with text. */
    @TsLiteral("sparkline")
    SPARKLINE,

    /** One value against its range. */
    @TsLiteral("meter")
    METER
}
