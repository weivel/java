package com.weivel.handles;

/**
 * One entry of a {@link Handles} table: a handle the core answered and the layer's object for it. It is made in room a
 * {@link Handles.Reservation} took, so making it allocates nothing, and it reads the same once it has ended.
 */
public final class HandleEntry {
    private HandleKind kind;
    private long handle;
    private long owner;
    private Object object;
    private long token;

    HandleEntry() {
    }

    void fill(HandleKind kind, long handle, long owner, Object object, long token) {
        this.kind = kind;
        this.handle = handle;
        this.owner = owner;
        this.object = object;
        this.token = token;
    }

    /**
     * What the handle stands for.
     *
     * @return the kind
     */
    public HandleKind kind() {
        return kind;
    }

    /**
     * The handle the core answered, never 0.
     *
     * @return the handle
     */
    public long handle() {
        return handle;
    }

    /**
     * The plugin the entry belongs to, {@code WEIVEL_HOST} for the host's own, or {@link Handles#NO_OWNER} for an
     * entry nothing owns, which a plugin's own entry is.
     *
     * @return the owner
     */
    public long owner() {
        return owner;
    }

    /**
     * The layer's object for the handle.
     *
     * @return the object, never null
     */
    public Object object() {
        return object;
    }

    /**
     * The table's own token for the entry, never 0: {@link Handles#findByToken} answers the entry again from it alone,
     * and a plugin is opened with it as its payload.
     *
     * @return the token
     */
    public long token() {
        return token;
    }
}
