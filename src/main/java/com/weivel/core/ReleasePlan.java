package com.weivel.core;

/**
 * One release of a described definition, bound once at the load: it takes the definition's context and the one thing
 * it frees, in the order its prototype has them, and answers nothing. A release the provider does not offer, past the
 * size its definition answered or NULL there, frees nothing.
 */
final class ReleasePlan {
    final String name;
    private final NativeFunction function;
    private final long context;
    private final boolean contextFirst;

    ReleasePlan(String name, NativeFunction function, long context, boolean contextFirst) {
        this.name = name;
        this.function = function;
        this.context = context;
        this.contextFirst = contextFirst;
    }

    void free(long pointer) {
        if (function == null) {
            return;
        }
        if (contextFirst) {
            function.call(context, pointer);
        } else {
            function.call(pointer, context);
        }
    }
}
