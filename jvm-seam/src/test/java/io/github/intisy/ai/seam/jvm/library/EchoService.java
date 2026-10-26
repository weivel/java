package io.github.intisy.ai.seam.jvm.library;

import com.google.gson.JsonElement;

/** The echo fixture's service as a plugin sees it, written by hand until the generator emits it. */
public interface EchoService {

    /** The argument {@code echo_repeat} takes. */
    final class Repetition {
        private final int count;

        Repetition(int count) {
            this.count = count;
        }
    }

    JsonElement echo(JsonElement argument);

    JsonElement fail(JsonElement argument);

    String repeat(Repetition argument);

    String garbage(JsonElement argument);
}
