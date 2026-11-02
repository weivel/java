package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsEnum;
import io.github.intisy.ai.tsemit.TsLiteral;

/** How much attention a node asks for. */
@TsEnum
public enum UiEmphasis {
    /** Less than its neighbours. */
    @TsLiteral("low")
    LOW,

    /** As much as its neighbours. */
    @TsLiteral("normal")
    NORMAL,

    /** More than its neighbours. */
    @TsLiteral("high")
    HIGH
}
