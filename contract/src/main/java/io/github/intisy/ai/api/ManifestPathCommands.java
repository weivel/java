package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;
import java.util.List;

/**
 * The executables a host puts on the user's PATH.
 *
 * @implNote Not the in-application slash commands, which are {@code commands} in a manifest: the
 * two are unrelated, and sharing the word is what made the first draft collide.
 */
@TsInterface(data = true)
public interface ManifestPathCommands {
    /**
     * The command names this host asks for, which a name already held may turn into a fallback.
     *
     * @return the requested command names
     */
    List<String> requested();

    /**
     * How many of its commands this host must keep: 1 means it cannot be left with none, 0 means it
     * can.
     *
     * @return 0 or 1, or absent, which means 0
     */
    @TsOptional
    Integer minimum();
}
