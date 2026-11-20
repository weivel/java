package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One operation of a {@link BoundDefinition}, its native function and the plan of every parameter built once at the
 * load, so a call looks nothing up: it writes its inputs into an arena it reuses, makes the one native call, copies
 * every answer out, and releases each answer and the refusal through the release the description names before it
 * answers. Any thread may call, while the library's binary is open.
 */
public final class BoundOperation {
    private final String definition;
    private final String name;
    private final NativeFunction function;
    private final Step[] steps;
    private final List<String> inputNames;
    private final Set<String> inputs;
    private final String onlyAnswer;
    private final Bound bound;

    BoundOperation(String definition, String name, NativeFunction function, Step[] steps, List<String> inputNames,
                   Set<String> inputs, String onlyAnswer, Bound bound) {
        this.definition = definition;
        this.name = name;
        this.function = function;
        this.steps = steps;
        this.inputNames = Collections.unmodifiableList(new ArrayList<>(inputNames));
        this.inputs = inputs;
        this.onlyAnswer = onlyAnswer;
        this.bound = bound;
    }

    /**
     * Answers {@code name}.
     *
     * @return the operation's name, as its definition's struct names it
     */
    public String name() {
        return name;
    }

    /**
     * Answers {@code inputNames}.
     *
     * @return the inputs a caller writes, in the prototype's order: every input but the context and a size counting
     *     another, which the binding sets from what it counts
     */
    public List<String> inputNames() {
        return inputNames;
    }

    /**
     * Whether the provider offers it.
     *
     * @return false when the struct its library answered ends before the operation, or holds NULL there
     */
    public boolean isOffered() {
        return function != null;
    }

    /**
     * Calls the operation with no input written, each at its kind's zero.
     *
     * @return what it answered
     * @throws UnsupportedOperationException when the provider does not offer it
     */
    public DescribedAnswer call() {
        return call(Collections.<String, Object>emptyMap());
    }

    /**
     * Calls the operation.
     *
     * @param inputs one value per input, keyed by its name, each as {@link DescribedKind} says its kind is written; an
     *     input left out is its kind's zero: 0, a NULL text, pointer or handle, or an empty array, texts or bytes
     * @return what it answered, every answer copied out and released before it returns
     * @throws IllegalArgumentException for a name the operation does not take, a size or count written, or a value its
     *     kind does not hold, before anything is called
     * @throws UnsupportedOperationException when the provider does not offer it
     */
    public DescribedAnswer call(Map<String, ?> inputs) {
        if (function == null) {
            throw new UnsupportedOperationException(definition + "'s provider does not offer " + name + ": the struct "
                    + "its library answered ends before it, or holds NULL there");
        }
        refuseUnknownNames(inputs);
        if (!bound.gate.enter()) {
            return new DescribedAnswer.Unloaded();
        }
        try {
            NativeArena arena = bound.arenas.take();
            try {
                return called(new DescribedCall(bound.access, arena, bound.holdings), inputs);
            } finally {
                bound.arenas.give(arena);
            }
        } finally {
            bound.gate.leave();
        }
    }

    private void refuseUnknownNames(Map<String, ?> given) {
        if (inputs.containsAll(given.keySet())) {
            return;
        }
        for (String key : given.keySet()) {
            if (!inputs.contains(key)) {
                throw new IllegalArgumentException(name + "'s input has no " + key + " a caller writes; it takes "
                        + (inputNames.isEmpty() ? "nothing" : String.join(", ", inputNames)));
            }
        }
    }

    private DescribedAnswer called(DescribedCall call, Map<String, ?> given) {
        long[] arguments = new long[steps.length];
        int status;
        try {
            for (int index = 0; index < steps.length; index++) {
                steps[index].prepare(call, given, arguments, index);
            }
            bound.insideTheCall.run();
            status = (int) function.call(arguments);
        } finally {
            call.leaveUsedHandles();
        }
        try {
            return outcome(call, status, arguments);
        } finally {
            for (int index = 0; index < steps.length; index++) {
                steps[index].release(arguments[index]);
            }
        }
    }

    private DescribedAnswer outcome(DescribedCall call, int status, long[] arguments) {
        try {
            if (status == CoreConstants.STATUS_OK) {
                Map<String, Object> answers = new LinkedHashMap<>();
                for (int index = 0; index < steps.length; index++) {
                    steps[index].answer(call, arguments[index], answers);
                }
                return new DescribedAnswer.Answered(answers, onlyAnswer);
            }
            if (status == CoreConstants.STATUS_NONE) {
                return new DescribedAnswer.None();
            }
            if (status == CoreConstants.STATUS_REFUSED) {
                for (int index = 0; index < steps.length; index++) {
                    Optional<DescribedAnswer> refused = steps[index].refusal(arguments[index]);
                    if (refused.isPresent()) {
                        return refused.get();
                    }
                }
            }
            return new DescribedAnswer.Failed(status);
        } catch (RuntimeException | Error failed) {
            call.releaseMadeHandles();
            throw failed;
        }
    }

    @Override
    public String toString() {
        return "BoundOperation[" + definition + "." + name + inputNames + "]";
    }

    /** What every operation of one library's definitions shares: the seam, the spare arena, its handles, the gate. */
    static final class Bound {
        final NativeAccess access;
        final SpareArena arenas;
        final DescribedHandles holdings;
        final CallGate gate;
        /** What runs inside every call, its inputs handed and its native call about to be made: a test's seam. */
        final Runnable insideTheCall;

        Bound(NativeAccess access, SpareArena arenas, DescribedHandles holdings, CallGate gate) {
            this(access, arenas, holdings, gate, () -> { });
        }

        Bound(NativeAccess access, SpareArena arenas, DescribedHandles holdings, CallGate gate,
              Runnable insideTheCall) {
            this.access = access;
            this.arenas = arenas;
            this.holdings = holdings;
            this.gate = gate;
            this.insideTheCall = insideTheCall;
        }
    }

    /** What one parameter is handed, worked out at the bind. */
    abstract static class Step {
        /** Sets the argument at {@code index}, and the one at a count's index for a counted input. */
        abstract void prepare(DescribedCall call, Map<String, ?> given, long[] arguments, int index);

        /** On OK, puts the answer the argument holds among {@code answers}. */
        void answer(DescribedCall call, long argument, Map<String, Object> answers) {
        }

        /** On REFUSED, the refusal the argument holds. */
        Optional<DescribedAnswer> refusal(long argument) {
            return Optional.empty();
        }

        /** Whatever the status, frees what the argument holds through its release. */
        void release(long argument) {
        }
    }
}
