package com.weivel.core;

/**
 * The standard's {@code weivel_standard_description_check} and {@code weivel_standard_description_answers}, handed to
 * whoever binds descriptions by its caller, so the layer never loads the standard itself: a binding runs it on every
 * description a service declares before it reads anything else of it, and binds nothing of one it refuses.
 */
@FunctionalInterface
public interface DescriptionCheck {
    /**
     * Checks a description, and that it describes the definition its service's definition symbol answered.
     *
     * @param description the description a library answered: a {@code MemorySegment} on the foreign function API, a
     *     JNA {@code Pointer} on JNA, carrying its address
     * @param definition the definition the service's definition symbol answered, carried as {@code description} is
     * @return {@link Answer.Ok} when it may be bound; {@link Answer.Mistake} saying why not, as the standard's status,
     *     path and detail, when either check refuses it; {@link Answer.Refused} of {@link RefusalStatus#OUT_OF_MEMORY}
     *     when a check ran out of memory, which is never the caller's mistake
     */
    Answer<Void> check(Object description, Object definition);
}
