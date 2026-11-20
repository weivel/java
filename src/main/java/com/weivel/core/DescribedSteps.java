package com.weivel.core;

import java.util.Map;
import java.util.Optional;

/** The steps a {@link BoundOperation} takes for each of its parameters, one kind of step per role a parameter has. */
final class DescribedSteps {
    private DescribedSteps() {
    }

    /** The definition's own context, handed as it is. */
    static final class Context extends BoundOperation.Step {
        private final long context;

        Context(long context) {
            this.context = context;
        }

        @Override
        void prepare(DescribedCall call, Map<String, ?> given, long[] arguments, int index) {
            arguments[index] = context;
        }
    }

    /** A size counting another parameter, set by the step of the parameter it counts. */
    static final class Count extends BoundOperation.Step {
        @Override
        void prepare(DescribedCall call, Map<String, ?> given, long[] arguments, int index) {
        }
    }

    /** A scalar input passed by value. */
    static final class Scalar extends BoundOperation.Step {
        private final String name;
        private final int kind;
        private final String where;

        Scalar(String operation, String name, int kind) {
            this.name = name;
            this.kind = kind;
            this.where = operation + "'s " + name;
        }

        @Override
        void prepare(DescribedCall call, Map<String, ?> given, long[] arguments, int index) {
            arguments[index] = given.containsKey(name) ? Scalars.bits(kind, given.get(name), where) : 0;
        }
    }

    /** An input passed as a pointer to what it reaches: a text, texts, bytes, a struct or value, an array, a handle. */
    static final class Reached extends BoundOperation.Step {
        private final MemberPlan plan;
        private final int countIndex;
        private final String where;

        Reached(String operation, MemberPlan plan, int countIndex) {
            this.plan = plan;
            this.countIndex = countIndex;
            this.where = operation + "'s " + plan.name;
        }

        @Override
        void prepare(DescribedCall call, Map<String, ?> given, long[] arguments, int index) {
            if (!given.containsKey(plan.name)) {
                return;
            }
            arguments[index] = call.reached(plan, given.get(plan.name), where);
            if (countIndex >= 0) {
                arguments[countIndex] = call.counted;
            }
        }
    }

    /**
     * An answer the provider fills on OK: a scalar, a text or a handle in a cell of its own, a struct, or a value
     * tree, each copied out on OK and, when the description names its release, freed whatever the status once it holds
     * anything.
     */
    static final class Answering extends BoundOperation.Step {
        private final NativeAccess access;
        private final MemberPlan plan;
        private final long size;
        private final boolean leadingSize;
        private final ReleasePlan release;

        Answering(NativeAccess access, MemberPlan plan, long size, boolean leadingSize, ReleasePlan release) {
            this.access = access;
            this.plan = plan;
            this.size = size;
            this.leadingSize = leadingSize;
            this.release = release;
        }

        @Override
        void prepare(DescribedCall call, Map<String, ?> given, long[] arguments, int index) {
            long block = call.allocate(size);
            if (leadingSize) {
                access.putInt(block, 0, (int) size);
            }
            arguments[index] = block;
        }

        @Override
        void answer(DescribedCall call, long block, Map<String, Object> answers) {
            switch (plan.kind) {
                case DescribedKind.TEXT:
                    answers.put(plan.name, call.readReached(plan, access.getLong(block, 0), 0));
                    return;
                case DescribedKind.HANDLE:
                    long handle = access.getLong(block, 0);
                    if (handle != 0) {
                        answers.put(plan.name, call.hold(plan, handle));
                    }
                    return;
                case DescribedKind.POINTER:
                    answers.put(plan.name, call.readStruct(plan.target, block));
                    return;
                case DescribedKind.VALUE:
                    long root = block + ValueTrees.TREE_ROOT;
                    if (ValueTrees.kindAt(access, root) != ValueTrees.NONE) {
                        answers.put(plan.name, ValueTrees.read(access, root));
                    }
                    return;
                default:
                    answers.put(plan.name, Scalars.read(access, plan.kind, block));
            }
        }

        @Override
        void release(long block) {
            if (release != null && !holdsNothing(access, block, leadingSize, size)) {
                release.free(block);
            }
        }
    }

    /** The consumer's empty refusal, read for its {@code detail} and {@code fix} on REFUSED, freed once filled. */
    static final class Refusing extends BoundOperation.Step {
        private final NativeAccess access;
        private final StructPlan type;
        private final long detail;
        private final long fix;
        private final ReleasePlan release;

        Refusing(NativeAccess access, StructPlan type, long detail, long fix, ReleasePlan release) {
            this.access = access;
            this.type = type;
            this.detail = detail;
            this.fix = fix;
            this.release = release;
        }

        @Override
        void prepare(DescribedCall call, Map<String, ?> given, long[] arguments, int index) {
            long block = call.allocate(type.size);
            if (type.leadingSize) {
                access.putInt(block, 0, (int) type.size);
            }
            arguments[index] = block;
        }

        @Override
        Optional<DescribedAnswer> refusal(long block) {
            return Optional.<DescribedAnswer>of(new DescribedAnswer.Refused(text(block + detail),
                    text(block + fix)));
        }

        private Optional<String> text(long at) {
            long text = access.getLong(at, 0);
            return text == 0 ? Optional.<String>empty() : Optional.of(access.readText(text));
        }

        @Override
        void release(long block) {
            if (release != null && !holdsNothing(access, block, type.leadingSize, type.size)) {
                release.free(block);
            }
        }
    }

    /**
     * Whether a block the binding handed the provider is still as it was handed, all zero past its leading size, so a
     * release would free nothing and the crossing is saved.
     */
    static boolean holdsNothing(NativeAccess access, long block, boolean leadingSize, long size) {
        long offset = 0;
        if (leadingSize) {
            offset = Integer.BYTES;
            if (size >= Long.BYTES && access.getInt(block, offset) != 0) {
                return false;
            }
            offset = Long.BYTES;
        }
        for (; offset + Long.BYTES <= size; offset += Long.BYTES) {
            if (access.getLong(block, offset) != 0) {
                return false;
            }
        }
        for (; offset < size; offset++) {
            if (access.readBytes(block, offset, 1)[0] != 0) {
                return false;
            }
        }
        return true;
    }
}
