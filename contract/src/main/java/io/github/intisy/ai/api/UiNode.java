package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;

/** One element of a view. It names what it is and how it looks as roles, never as raw colours or sizes. */
@TsInterface(data = true)
public interface UiNode {
    /**
     * The node's identity within its view.
     *
     * @return the node id
     */
    String id();

    /**
     * The id of the node this one sits under, which must come earlier in the view. Absent means the view's root.
     *
     * @return the parent id, or absent for a root node
     */
    @TsOptional
    String parent();

    /**
     * What the node is.
     *
     * @return the component
     */
    UiComponent component();

    /**
     * The node's size as a role.
     *
     * @return the size, or absent for the host's default
     */
    @TsOptional
    UiSize size();

    /**
     * How much attention the node asks for.
     *
     * @return the emphasis, or absent for normal
     */
    @TsOptional
    UiEmphasis emphasis();

    /**
     * The node's colour role.
     *
     * @return the tone, or absent for the host's default
     */
    @TsOptional
    UiTone tone();

    /**
     * Text a host shows as the node's heading or caption.
     *
     * @return the label, or absent when there is none
     */
    @TsOptional
    String label();

    /**
     * The node's own text.
     *
     * @return the text, or absent when there is none
     */
    @TsOptional
    String text();

    /**
     * The name of a host icon.
     *
     * @return the icon name, or absent when there is none
     */
    @TsOptional
    String icon();

    /**
     * The service call whose result the node shows.
     *
     * @return the binding, or absent when the node shows no service data
     */
    @TsOptional
    UiBinding bind();

    /**
     * The id of one of this plugin's actions the node runs.
     *
     * @return the action id, or absent when the node runs none
     */
    @TsOptional
    String action();

    /**
     * The key of one of this plugin's settings the node edits.
     *
     * @return the setting key, or absent when the node edits none
     */
    @TsOptional
    String field();

    /**
     * The chart form of a chart node.
     *
     * @return the chart form, or absent when the node is not a chart
     */
    @TsOptional
    UiChart chart();

    /**
     * How many of a grid's twelve columns the node takes, a whole number from 1 to 12.
     *
     * @return the span, or absent for the host's default
     */
    @TsOptional
    Integer span();
}
