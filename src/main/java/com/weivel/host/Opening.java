package com.weivel.host;

import com.weivel.context.PluginContext;
import com.weivel.context.PluginContexts;
import com.weivel.core.Answer;

/**
 * How the host opens a plugin: through its contexts, always, but for a test of the host's own package, which makes an
 * open fail for a reason other than the core's refusal, as no open through the real core can be made to.
 */
@FunctionalInterface
interface Opening {
    Opening THROUGH_THE_CONTEXTS = PluginContexts::open;

    Answer<Void> open(PluginContexts contexts, PluginContext context, Runnable opened);
}
