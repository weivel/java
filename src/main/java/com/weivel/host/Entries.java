package com.weivel.host;

import com.weivel.core.Answer;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/** Loads a plugin's {@link Entry} by its class name, never by a name the layer assumes. */
public final class Entries {
    private Entries() {
    }

    /**
     * Loads and makes the entry named {@code className} from {@code loader}, through its public constructor taking
     * nothing.
     *
     * @param className the entry's binary class name
     * @param loader the class loader to find it in, the caller's own
     * @return the entry; {@link Answer.Mistake} for a class the loader cannot find or link, one that is no
     *     {@link Entry}, or one that cannot be made
     */
    public static Answer<Entry> load(String className, ClassLoader loader) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(loader, "loader");
        Class<?> type;
        try {
            type = Class.forName(className, false, loader);
        } catch (ClassNotFoundException | LinkageError missing) {
            return new Answer.Mistake<>("no class " + className + " could be loaded: " + missing);
        }
        if (!Entry.class.isAssignableFrom(type)) {
            return new Answer.Mistake<>(className + " is no " + Entry.class.getName());
        }
        return made(type.asSubclass(Entry.class));
    }

    private static Answer<Entry> made(Class<? extends Entry> type) {
        try {
            return new Answer.Ok<>(type.getConstructor().newInstance());
        } catch (InvocationTargetException thrown) {
            return new Answer.Mistake<>(type.getName() + "'s constructor threw: " + thrown.getCause());
        } catch (ReflectiveOperationException | LinkageError unmade) {
            return new Answer.Mistake<>(type.getName() + " has no public constructor taking nothing that could be "
                    + "called: " + unmade);
        }
    }
}
