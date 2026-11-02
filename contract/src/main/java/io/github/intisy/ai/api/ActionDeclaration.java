package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;

/** One action: what a host labels it and which of this plugin's service methods it calls. */
@TsInterface(data = true)
public interface ActionDeclaration {
    /**
     * The text a host shows on the control.
     *
     * @return the label
     */
    String label();

    /**
     * The service method the action calls.
     *
     * @return the binding
     */
    UiBinding call();

    /**
     * A question a host asks before running the action. Absent means it runs at once.
     *
     * @return the question, or absent when the action runs at once
     */
    @TsOptional
    String confirm();

    /**
     * Values a host asks for before running the action, declared as settings are.
     *
     * @return the input settings, or absent when the action asks for none
     */
    @TsOptional
    UiSettings input();

    /**
     * The control's colour role.
     *
     * @return the tone, or absent for the host's default
     */
    @TsOptional
    UiTone tone();
}
