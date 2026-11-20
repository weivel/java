package com.weivel.core;

import java.util.Optional;

/**
 * The standard's {@code weivel_standard_description_check}, handed to whoever binds descriptions by its caller, so the
 * layer never loads the standard itself: a binding runs it on every description before it reads anything else of it,
 * and binds nothing of one it refuses.
 */
@FunctionalInterface
public interface DescriptionCheck {
    /**
     * Checks a description.
     *
     * @param description the description a library answered: a {@code MemorySegment} on the foreign function API, a
     *     JNA {@code Pointer} on JNA, carrying its address
     * @return empty when it may be bound; otherwise why not, as the standard's status, path and detail
     */
    Optional<String> refusalOf(Object description);
}
