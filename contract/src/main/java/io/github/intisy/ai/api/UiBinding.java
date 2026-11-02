package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;
import java.util.Map;

/** A call into one of this plugin's own services. */
@TsInterface(data = true)
public interface UiBinding {
    /**
     * The id of a service this plugin provides.
     *
     * @return the service id
     */
    String service();

    /**
     * The service method to call.
     *
     * @return the method name
     */
    String method();

    /**
     * The argument sent with the call.
     *
     * @return the argument, or absent when the call takes none
     */
    @TsOptional
    Map<String, Object> argument();
}
