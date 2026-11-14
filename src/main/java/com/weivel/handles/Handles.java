package com.weivel.handles;

import com.weivel.core.Answer;
import com.weivel.core.CallSteps;
import com.weivel.core.CoreEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * The table from the core's handles to the layer's objects: an entry per handle the core answered, kept until what
 * the handle stands for ends, and the holdings, the objects each plugin was handed for another's registration or
 * implementation, kept by the pair of the two handles.
 *
 * <p>The table decides nothing and calls nothing: it never calls into the core and compares handles only for
 * equality. Its caller makes an entry once the core answers a handle, in room a {@link Reservation} took before the
 * call, from the step the wrapper runs before any event of that call is dispatched ({@link Reservation#making}); and
 * ends it when what the handle stands for ends: an unregistration, a withdrawn implementation, a stopped watch, a
 * settled or withdrawn want, a released or forgotten plugin. A handle the table does not hold answers nothing, and an
 * ended handle or token is never matched to a newer entry.
 *
 * <p>A lookup by handle, by token or by holding costs one probe of a hash table on average and allocates nothing.
 *
 * <p>Any thread may call, at any time, under the layer lock or outside it: the table has one lock of its own, held only
 * inside its own methods and never while anything outside the table runs. It may be taken under the layer lock, by
 * the step a call runs, and never takes that lock itself, so it is always the innermost lock and cannot deadlock
 * against the wrapper.
 */
public final class Handles {
    /** The owner of an entry nothing owns, which a plugin's own entry is. */
    public static final long NO_OWNER = 0;

    private static final int SMALLEST_TOKEN_CAPACITY = 16;
    private static final long TOKEN_INDEX_MASK = 0xffffffffL;

    private final ReentrantLock lock = new ReentrantLock();
    private final TableMemory memory;
    private final PairTable<HandleEntry> entries;
    private final PairTable<Object> holdings;
    private HandleEntry[] entriesByToken;
    private int[] tokenGenerations;
    private int[] freeTokenIndexes;
    private int freeTokenCount;
    private int tokenIndexesUsed;
    private int reserved;

    /** Makes an empty table. */
    public Handles() {
        this(TableMemory.HEAP);
    }

    Handles(TableMemory memory) {
        this.memory = memory;
        entries = new PairTable<>(memory);
        holdings = new PairTable<>(memory);
        entriesByToken = memory.entries(SMALLEST_TOKEN_CAPACITY);
        tokenGenerations = memory.ints(SMALLEST_TOKEN_CAPACITY);
        freeTokenIndexes = memory.ints(SMALLEST_TOKEN_CAPACITY);
    }

    /**
     * Takes the room one later addition needs, an entry or a holding, so that making it allocates nothing and cannot
     * fail. It is what a caller does before it asks the core for a handle; a reservation the call did not use is
     * given back with {@link Reservation#cancel()}.
     *
     * @return the reservation, used once
     * @throws OutOfMemoryError when the room cannot be had, before anything is asked of the core, the table left as it
     *     was and no room taken
     */
    public Reservation reserve() {
        Reservation reservation = new Reservation(new HandleEntry());
        lock.lock();
        try {
            int wanted = reserved + 1;
            entries.ensureRoom(entries.size() + wanted);
            holdings.ensureRoom(holdings.size() + wanted);
            ensureTokenRoom(wanted);
            reserved = wanted;
        } finally {
            lock.unlock();
        }
        return reservation;
    }

    /**
     * Answers the entry of a handle.
     *
     * @param handle a handle the core answered
     * @return the entry; null for a handle the table does not hold, whether it ended or was never made
     */
    public HandleEntry find(long handle) {
        lock.lock();
        try {
            return entries.get(handle, 0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Answers the entry a token was answered for, from the token alone: no handle is compared and nothing searched.
     * A plugin opened with its entry's token as its payload is reached from the payload the core answers for it.
     *
     * @param token a token an entry carried
     * @return the entry; null for a token of an entry that ended, or one this table never answered
     */
    public HandleEntry findByToken(long token) {
        int index = (int) (token & TOKEN_INDEX_MASK) - 1;
        lock.lock();
        try {
            if (index < 0 || index >= tokenIndexesUsed) {
                return null;
            }
            HandleEntry entry = entriesByToken[index];
            return entry != null && entry.token() == token ? entry : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ends the entry of a handle, because what it stands for ended.
     *
     * @param handle the handle
     * @return the entry as it was; null for a handle the table does not hold
     */
    public HandleEntry end(long handle) {
        lock.lock();
        try {
            HandleEntry ended = entries.remove(handle, 0);
            if (ended != null) {
                freeToken(ended);
            }
            return ended;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ends every entry {@code owner} owns: every registration, implementation, watch and want of a plugin whose
     * release or quarantine has been heard. The plugin's own entry is owned by nothing and stays until it is
     * forgotten, and holdings are not entries and stay until their revocations.
     *
     * @param owner a plugin's handle, or {@code WEIVEL_HOST}
     * @return the entries ended, in no particular order
     * @throws IllegalStateException for {@link #NO_OWNER}, which owns every plugin's own entry
     */
    public List<HandleEntry> endOwned(long owner) {
        refuseNoOwner(owner);
        lock.lock();
        try {
            List<HandleEntry> ended = entries.removeAll((handle, unused, entry) -> entry.owner() == owner);
            ended.forEach(this::freeToken);
            return ended;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ends every entry {@code owner} owns whose kind is one of {@code kinds}, and leaves its others.
     *
     * @param owner a plugin's handle, or {@code WEIVEL_HOST}
     * @param kinds the kinds to end
     * @return the entries ended, in no particular order
     * @throws IllegalStateException for {@link #NO_OWNER}, which owns every plugin's own entry
     */
    public List<HandleEntry> endOwned(long owner, Set<HandleKind> kinds) {
        Objects.requireNonNull(kinds, "kinds");
        refuseNoOwner(owner);
        lock.lock();
        try {
            List<HandleEntry> ended = entries.removeAll((handle, unused, entry) ->
                    entry.owner() == owner && kinds.contains(entry.kind()));
            ended.forEach(this::freeToken);
            return ended;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether the table holds an entry {@code matches} accepts, read by walking every entry, so for a rare question
     * such as whether a service id is registered at all.
     *
     * @param matches what an entry must be; it may not call into the table
     * @return whether one is held
     */
    public boolean holdsAny(Predicate<HandleEntry> matches) {
        Objects.requireNonNull(matches, "matches");
        lock.lock();
        try {
            return entries.any((handle, unused, entry) -> matches.test(entry));
        } finally {
            lock.unlock();
        }
    }

    /** Whether the calling thread holds the table's lock: what a test reads from code that must run outside it. */
    boolean isLockHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * Answers the object {@code plugin} was handed for {@code implementation}.
     *
     * @param plugin the plugin that holds it, or {@code WEIVEL_HOST}
     * @param implementation the registration or capability implementation it holds
     * @return the object; null when the plugin holds no such thing
     */
    public Object held(long plugin, long implementation) {
        lock.lock();
        try {
            return holdings.get(plugin, implementation);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops the holding a revocation names. The event's {@code plugin} is half of the key and nothing else, so it may
     * name a plugin the core has forgotten and the table no longer holds.
     *
     * @param event the revocation heard
     * @return the object the plugin had been handed; null for a pair the table does not hold
     */
    public Object revoke(CoreEvent.RevokedEvent event) {
        Objects.requireNonNull(event, "event");
        lock.lock();
        try {
            return holdings.remove(event.plugin(), event.implementation());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops every holding of {@code plugin}: what its owner does once the plugin's own record is gone, so no holding is
     * left pointing at what the plugin was handed.
     *
     * @param plugin the plugin
     */
    public void dropHoldings(long plugin) {
        lock.lock();
        try {
            holdings.removeAll((holder, implementation, object) -> holder == plugin);
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many entries the table holds.
     *
     * @return the count
     */
    public int entryCount() {
        lock.lock();
        try {
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many holdings the table holds.
     *
     * @return the count
     */
    public int holdingCount() {
        lock.lock();
        try {
            return holdings.size();
        } finally {
            lock.unlock();
        }
    }

    private static void refuseNoOwner(long owner) {
        if (owner == NO_OWNER) {
            throw new IllegalStateException("the entries of no owner are every plugin's own; end each with end()");
        }
    }

    private void ensureTokenRoom(int wanted) {
        int available = freeTokenCount + entriesByToken.length - tokenIndexesUsed;
        if (available >= wanted) {
            return;
        }
        int capacity = entriesByToken.length;
        while (freeTokenCount + capacity - tokenIndexesUsed < wanted) {
            capacity *= 2;
        }
        HandleEntry[] grownEntries = memory.entries(capacity);
        int[] grownGenerations = memory.ints(capacity);
        int[] grownFreeIndexes = memory.ints(capacity);
        System.arraycopy(entriesByToken, 0, grownEntries, 0, entriesByToken.length);
        System.arraycopy(tokenGenerations, 0, grownGenerations, 0, tokenGenerations.length);
        System.arraycopy(freeTokenIndexes, 0, grownFreeIndexes, 0, freeTokenIndexes.length);
        entriesByToken = grownEntries;
        tokenGenerations = grownGenerations;
        freeTokenIndexes = grownFreeIndexes;
    }

    /**
     * Takes a token: the index of a free slot and, above it, that slot's generation, drawn anew each time.
     *
     * @implNote A generation is 32 bits and wraps after 2^32 takings of one slot, so a token ended that long ago
     * could match a newer entry in its slot. Reaching it takes 2^32 entries made and ended in that one slot while
     * someone still holds the old token, which the layer does not guard against.
     */
    private long takeToken() {
        int index = freeTokenCount > 0 ? freeTokenIndexes[--freeTokenCount] : tokenIndexesUsed++;
        int generation = ++tokenGenerations[index];
        return ((long) generation << Integer.SIZE) | (index + 1);
    }

    private void freeToken(HandleEntry ended) {
        int index = (int) (ended.token() & TOKEN_INDEX_MASK) - 1;
        entriesByToken[index] = null;
        freeTokenIndexes[freeTokenCount++] = index;
    }

    /**
     * The room one addition takes, used once: by {@link #add}, by {@link #hold}, or given back by {@link #cancel()}.
     * Using it allocates nothing.
     */
    public final class Reservation {
        private final HandleEntry shell;
        private boolean open = true;

        private Reservation(HandleEntry shell) {
            this.shell = shell;
        }

        /**
         * Makes the entry of a handle the core just answered, in this room.
         *
         * @param kind what the handle stands for
         * @param handle the handle, never 0
         * @param owner the plugin the entry belongs to, {@code WEIVEL_HOST}, or {@link Handles#NO_OWNER}
         * @param object the layer's object for the handle
         * @return the entry
         * @throws IllegalStateException when this room was used or given back, or the table already holds the
         *     handle, which the core never answers again while its object lives: a defect of the layer
         */
        public HandleEntry add(HandleKind kind, long handle, long owner, Object object) {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(object, "object");
            lock.lock();
            try {
                useRoom();
                if (handle == 0 || entries.get(handle, 0) != null) {
                    throw new IllegalStateException("a defect of the layer: an entry for handle " + handle
                            + " was made twice or for no handle");
                }
                long token = takeToken();
                shell.fill(kind, handle, owner, object, token);
                entriesByToken[(int) (token & TOKEN_INDEX_MASK) - 1] = shell;
                entries.put(handle, 0, shell);
                return shell;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Records, in this room, that {@code plugin} holds {@code implementation} with the object it was handed for
         * it. Holding it again replaces the object.
         *
         * @param plugin the plugin, or {@code WEIVEL_HOST}
         * @param implementation the registration or capability implementation it was handed
         * @param object what it was handed
         * @throws IllegalStateException when this room was used or given back
         */
        public void hold(long plugin, long implementation, Object object) {
            Objects.requireNonNull(object, "object");
            lock.lock();
            try {
                useRoom();
                holdings.put(plugin, implementation, object);
            } finally {
                lock.unlock();
            }
        }

        /**
         * The steps that make the entry of the handle a call answers, from the step the wrapper runs under the layer
         * lock once the core has answered and before any event of the call is dispatched, so a listener hearing of
         * the handle finds its entry.
         *
         * @param kind what the handle will stand for
         * @param owner the plugin the entry belongs to, {@code WEIVEL_HOST}, or {@link Handles#NO_OWNER}
         * @param object the layer's object for the handle
         * @return the steps for {@code CoreInternals.call}; the room stays open when the core does not answer OK
         */
        public CallSteps<Long> making(HandleKind kind, long owner, Object object) {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(object, "object");
            return new CallSteps<Long>(() -> Optional.<Answer<Long>>empty(), handle -> {
                add(kind, handle, owner, object);
                return new Answer.Ok<>(handle);
            });
        }

        /** Gives the room back when it was not used; does nothing once it was. */
        public void cancel() {
            lock.lock();
            try {
                if (open) {
                    open = false;
                    reserved--;
                }
            } finally {
                lock.unlock();
            }
        }

        private void useRoom() {
            if (!open) {
                throw new IllegalStateException("this room was already used or given back");
            }
            open = false;
            reserved--;
        }
    }
}
