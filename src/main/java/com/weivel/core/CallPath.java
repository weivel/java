package com.weivel.core;

/**
 * Where in a call's input a value stands, such as {@code reflect's sample.tags[2]}, spelled out only when a refusal
 * names it, so a call that writes its input whole builds no text for it.
 */
final class CallPath {
    private static final int NO_INDEX = -1;

    private final CallPath parent;
    private final String text;
    private final String joiner;
    private final int index;

    private CallPath(CallPath parent, String joiner, String text, int index) {
        this.parent = parent;
        this.joiner = joiner;
        this.text = text;
        this.index = index;
    }

    /** The input itself, named as a refusal names it. */
    static CallPath of(String root) {
        return new CallPath(null, "", root, NO_INDEX);
    }

    /** A member of the struct or record standing here. */
    CallPath member(String name) {
        return new CallPath(this, ".", name, NO_INDEX);
    }

    /** An element of the list standing here. */
    CallPath element(int position) {
        return new CallPath(this, "", null, position);
    }

    /** A key of the record standing here. */
    CallPath key(String key) {
        return new CallPath(this, "'s key ", key, NO_INDEX);
    }

    @Override
    public String toString() {
        StringBuilder spelled = new StringBuilder();
        spell(spelled);
        return spelled.toString();
    }

    private void spell(StringBuilder spelled) {
        if (parent != null) {
            parent.spell(spelled);
        }
        if (index == NO_INDEX) {
            spelled.append(joiner).append(text);
        } else {
            spelled.append('[').append(index).append(']');
        }
    }
}
