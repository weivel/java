package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsName;
import io.github.intisy.ai.tsemit.TsOptional;
import java.util.List;

/** One setting; `choices` is required, with at least one value, when the type is `choice` or `multi-choice`. */
@TsInterface(data = true)
public interface SettingDeclaration {
    /**
     * How a host edits the value.
     *
     * @return the setting's type
     */
    SettingType type();

    /**
     * The text a host shows beside the control.
     *
     * @return the label
     */
    String label();

    /**
     * A sentence a host shows under the control.
     *
     * @return the help text, or absent when there is none
     */
    @TsOptional
    String help();

    /**
     * The value in force until a user changes it.
     *
     * @return the default value, or absent when the setting has none
     */
    @TsOptional
    @TsName("default")
    Object defaultValue();

    /**
     * Whether the value travels with the user or stays with this installation. Absent means portable.
     *
     * @return the layer, or absent when the value is portable
     */
    @TsOptional
    SettingsLayer layer();

    /**
     * The values a choice or multi-choice setting offers.
     *
     * @return the choices, or absent when the type offers none
     */
    @TsOptional
    List<String> choices();

    /**
     * The lowest value a number setting accepts.
     *
     * @return the minimum, or absent when there is none
     */
    @TsOptional
    Integer minimum();

    /**
     * The highest value a number setting accepts.
     *
     * @return the maximum, or absent when there is none
     */
    @TsOptional
    Integer maximum();

    /**
     * A regular expression a text value must match.
     *
     * @return the pattern, or absent when any text is accepted
     */
    @TsOptional
    String pattern();
}
