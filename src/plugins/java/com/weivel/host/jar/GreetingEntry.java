package com.weivel.host.jar;

import com.weivel.context.PluginContext;
import com.weivel.core.Answer;
import com.weivel.host.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** A plugin loaded from a JAR off the test class path: it registers a greeting under its own namespace. */
public final class GreetingEntry implements Entry {
    @Override
    public CompletionStage<?> activate(PluginContext context) {
        Answer<?> registered = context.register("greeting:hello", "hello from a jar");
        CompletableFuture<Object> done = new CompletableFuture<>();
        if (registered instanceof Answer.Ok) {
            done.complete(null);
        } else {
            done.completeExceptionally(new IllegalStateException("the greeting was not registered: " + registered));
        }
        return done;
    }
}
