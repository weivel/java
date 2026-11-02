package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import java.util.List;

/** One view: a title and the nodes that make it up. */
@TsInterface(data = true)
public interface ViewDeclaration {
    /**
     * The name a host shows for the view.
     *
     * @return the title
     */
    String title();

    /**
     * The view's nodes as a flat list, at least one; a node's `parent` names a node listed before it.
     *
     * @return the nodes
     */
    List<UiNode> nodes();
}
