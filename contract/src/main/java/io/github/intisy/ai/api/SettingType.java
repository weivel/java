package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsEnum;
import io.github.intisy.ai.tsemit.TsLiteral;

/** How a host edits a setting's value. */
@TsEnum
public enum SettingType {
    /** A line of text. */
    @TsLiteral("text")
    TEXT,

    /** Text a host masks and keeps out of logs. */
    @TsLiteral("secret")
    SECRET,

    /** A number, within the setting's minimum and maximum. */
    @TsLiteral("number")
    NUMBER,

    /** On or off. */
    @TsLiteral("toggle")
    TOGGLE,

    /** One of the setting's choices. */
    @TsLiteral("choice")
    CHOICE,

    /** Any of the setting's choices. */
    @TsLiteral("multi-choice")
    MULTI_CHOICE,

    /** A file system path. */
    @TsLiteral("path")
    PATH,

    /** A list of text values. */
    @TsLiteral("list")
    LIST,

    /** Text values keyed by text. */
    @TsLiteral("key-value")
    KEY_VALUE
}
