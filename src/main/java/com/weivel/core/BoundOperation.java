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
 * answers. An answer carrying a definition is the one exception: it is kept, in memory of its own, until
 * {@link DescribedAnswer.Answered#release()} ends it. Any thread may call, while the library's binary is open; an
 * operation its description marks blocking is called on the calling thread as any other is, since a call blocks only
 * its caller.
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
    private final DescribedCall.Carrier carrier;
    private final boolean keepsAnswers;

    /**
     * @param carrier binds a definition an answer of the operation carries
     * @param keepsAnswers whether an answer of it can carry a definition, so its answers are written apart from the
     *     arena every call reuses
     */
    BoundOperation(String definition, String name, NativeFunction function, Step[] steps, List<String> inputNames,
                   Set<String> inputs, String onlyAnswer, Bound bound, DescribedCall.Carrier carrier,
                   boolean keepsAnswers) {
        this.definition = definition;
        this.name = name;
        this.function = function;
        this.steps = steps;
        this.inputNames = Collections.unmodifiableList(new ArrayList<>(inputNames));
        this.inputs = inputs;
        this.onlyAnswer = onlyAnswer;
        this.bound = bound;
        this.carrier = carrier;
        this.keepsAnswers = keepsAnswers;
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
     * @return what it answered, every answer copied out and released before it returns but one carrying a
     *     definition, which lives until its {@link DescribedAnswer.Answered#release()}
     * @throws IllegalArgumentException for a name the operation does not take, a size or count written, or a value its
     *     kind does not hold, before anything is called; and for an operation of a definition an answer carried once
     *     that answer has ended, by its release or the library's unload
     * @throws UnsupportedOperationException when the provider does not offer it
     * @implNote The scope is left before the gate: the end of an answer released while this call ran is run by the
     *     last call to leave the scope, and only leaving the scope first keeps that end, and the releases it calls,
     *     inside this call's own hold on the binary, so an unload can never have closed it under them. The gate is
     *     left even when that end throws.
     */
    public DescribedAnswer call(Map<String, ?> inputs) {
        if (function == null) {
            throw new UnsupportedOperationException(definition + "'s provider does not offer " + name + ": the struct "
                    + "its library answered ends before it, or holds NULL there");
        }
        refuseUnknownNames(inputs);
        if (!bound.scope.enter()) {
            throw new IllegalArgumentException(definition + " was carried by an answer that has ended, and lives only "
                    + "as long as that answer, so " + name + " can no longer be called");
        }
        boolean entered = false;
        try {
            entered = bound.gate.enter();
            if (!entered) {
                return new DescribedAnswer.Unloaded();
            }
            NativeArena arena = bound.arenas.take();
            try {
                return called(new DescribedCall(bound.access, arena, bound.holdings, carrier, keepsAnswers), inputs);
            } finally {
                bound.arenas.give(arena);
            }
        } finally {
            try {
                bound.scope.leave();
            } finally {
                if (entered) {
                    bound.gate.leave();
                }
            }
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
        boolean kept = false;
        try {
            DescribedAnswer answer = outcome(call, status, arguments);
            kept = answer instanceof DescribedAnswer.Answered && ((DescribedAnswer.Answered) answer).isKept();
            return answer;
        } finally {
            try {
                for (int index = 0; index < steps.length; index++) {
                    if (!kept || !steps[index].answers()) {
                        steps[index].release(arguments[index]);
                    }
                }
            } finally {
                if (!kept) {
                    call.closeAnswers();
                }
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
                return new DescribedAnswer.Answered(answers, onlyAnswer, kept(call, arguments));
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
            call.endCarried();
            throw failed;
        }
    }

    /**
     * The answer kept for the definitions the call's answers carried, among the holdings of what it was got through;
     * null when they carried none, so they are released at once.
     */
    private KeptAnswer kept(DescribedCall call, long[] arguments) {
        if (call.carried().isEmpty()) {
            return null;
        }
        long[] answered = arguments.clone();
        NativeArena memory = call.takeAnswers();
        KeptAnswer kept = new KeptAnswer(bound.holdings, call.carried(), () -> {
            try {
                for (int index = 0; index < steps.length; index++) {
                    if (steps[index].answers()) {
                        steps[index].release(answered[index]);
                    }
                }
            } finally {
                memory.close();
            }
        });
        bound.holdings.keep(kept);
        return kept;
    }

    @Override
    public String toString() {
        return "BoundOperation[" + definition + "." + name + inputNames + "]";
    }

    /**
     * What every operation of one library's definitions shares: the seam, the spare arena, its handles, the gate; and
     * for a definition an answer carried, the scope it lives in, with holdings of its own for what is got through it.
     */
    static final class Bound {
        final NativeAccess access;
        final SpareArena arenas;
        final DescribedHandles holdings;
        final CallGate gate;
        /** What runs inside every call, its inputs handed and its native call about to be made: a test's seam. */
        final Runnable insideTheCall;
        final CarriedScope scope;

        Bound(NativeAccess access, SpareArena arenas, DescribedHandles holdings, CallGate gate) {
            this(access, arenas, holdings, gate, () -> { });
        }

        Bound(NativeAccess access, SpareArena arenas, DescribedHandles holdings, CallGate gate,
              Runnable insideTheCall) {
            this(access, arenas, holdings, gate, insideTheCall, CarriedScope.UNSCOPED);
        }

        private Bound(NativeAccess access, SpareArena arenas, DescribedHandles holdings, CallGate gate,
                      Runnable insideTheCall, CarriedScope scope) {
            this.access = access;
            this.arenas = arenas;
            this.holdings = holdings;
            this.gate = gate;
            this.insideTheCall = insideTheCall;
            this.scope = scope;
        }

        /** What a definition an answer got through this carried shares: all of this but its holdings and scope. */
        Bound carried(DescribedHandles carriedHoldings, CarriedScope carriedScope) {
            return new Bound(access, arenas, carriedHoldings, gate, insideTheCall, carriedScope);
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

        /** Whether the argument is an answer, which an answer carrying a definition keeps until its own release. */
        boolean answers() {
            return false;
        }
    }
}
