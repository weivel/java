package com.weivel.core;

/**
 * One release of a described definition, bound once at the load: it takes the definition's context first and the one
 * thing it frees second, as the standard's check holds every release to, and answers nothing. A release the provider
 * does not offer, past the size its definition answered or NULL there, frees nothing.
 */
final class ReleasePlan {
    final String name;
    private final NativeFunction function;
    private final long context;

    ReleasePlan(String name, NativeFunction function, long context) {
        this.name = name;
        this.function = function;
        this.context = context;
    }

    void free(long pointer) {
        if (function == null) {
            return;
        }
        function.call(context, pointer);
    }
}
