package com.weivel.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Binds a definition from its description, once, at the load: the layouts checked against the backend's own, the
 * context and every function pointer read once from the definition's struct, each native function made with the
 * signature its parameters give, and the plan of every parameter built, so that a call follows plans and looks
 * nothing up.
 */
final class DescribedBinder {
    private static final int MOST_PARAMETERS = 7;
    private static final List<Integer> INPUT_KINDS = Arrays.asList(DescribedKind.CONTEXT, DescribedKind.U32,
            DescribedKind.U64, DescribedKind.I64, DescribedKind.F64, DescribedKind.SIZE, DescribedKind.TEXT,
            DescribedKind.TEXTS, DescribedKind.BYTES, DescribedKind.POINTER, DescribedKind.ARRAY,
            DescribedKind.HANDLE);
    private static final List<Integer> ANSWER_KINDS = Arrays.asList(DescribedKind.U32, DescribedKind.U64,
            DescribedKind.I64, DescribedKind.F64, DescribedKind.SIZE, DescribedKind.TEXT, DescribedKind.POINTER,
            DescribedKind.VALUE, DescribedKind.HANDLE);
    private static final long CELL = Layout.LONG.byteSize();

    private final NativeAccess access;
    private final DescribedLayouts layouts;
    private final Description description;
    private final long definition;
    private final long answeredSize;
    private final BoundOperation.Bound bound;
    private final Map<String, ReleasePlan> releases = new LinkedHashMap<>();
    private long context;

    private DescribedBinder(NativeAccess access, DescribedLayouts layouts, long definition,
                            BoundOperation.Bound bound) {
        this.access = access;
        this.layouts = layouts;
        this.description = layouts.description();
        this.definition = definition;
        this.answeredSize = Integer.toUnsignedLong(access.getInt(definition, 0));
        this.bound = bound;
    }

    /**
     * Binds the definition at {@code definition} from {@code description}.
     *
     * @throws DescriptionRefused for a description this binding cannot bind on this platform, or a definition that
     *     ends before its first shape
     * @throws UnsupportedOperationException from a backend that does not bind descriptions yet
     */
    static BoundDefinition bind(NativeAccess access, Description description, long definition, Object addressed,
                                BoundOperation.Bound bound) {
        DescribedBinder binder = new DescribedBinder(access, new DescribedLayouts(access, description), definition,
                bound);
        return new BoundDefinition(description.definition(), addressed, definition, description, binder.operations());
    }

    private Map<String, BoundOperation> operations() {
        Description.Type type = layouts.type(description.definition());
        if (type == null) {
            throw layouts.refused("it describes no struct " + description.definition() + " for its definition");
        }
        if (answeredSize < type.firstShape()) {
            throw layouts.refused("its definition answered a struct of " + answeredSize + " bytes, which ends before "
                    + "its first shape at " + type.firstShape());
        }
        for (Description.Member member : type.members()) {
            if (member.kind() == DescribedKind.CONTEXT) {
                context = pointerAt(member.offset());
            }
        }
        for (Description.Operation operation : description.operations()) {
            if (operation.returns() == Description.NOTHING) {
                releases.put(operation.name(), release(operation));
            } else if (operation.returns() != Description.STATUS) {
                throw layouts.refused("its operation " + operation.name() + " returns the form "
                        + operation.returns() + ", which this binding does not know");
            }
        }
        for (MemberPlan member : layouts.everyMemberPlan()) {
            member.release = handleRelease(member, "its member " + member.name);
        }
        Map<String, BoundOperation> operations = new LinkedHashMap<>();
        for (Description.Operation operation : description.operations()) {
            if (operation.returns() == Description.STATUS) {
                operations.put(operation.name(), operation(operation));
            }
        }
        return operations;
    }

    /** The pointer at {@code offset} of the definition; 0 when the struct its library answered ends before it. */
    private long pointerAt(long offset) {
        return offset + access.addressSize() <= answeredSize ? access.getLong(definition, offset) : 0;
    }

    private NativeFunction functionAt(long offset, NativeSignature signature) {
        long pointer = pointerAt(offset);
        return pointer == 0 ? null : access.function(pointer, signature);
    }

    private ReleasePlan release(Description.Operation operation) {
        List<Description.Parameter> parameters = operation.parameters();
        int contexts = 0;
        for (Description.Parameter parameter : parameters) {
            if (parameter.kind() == DescribedKind.CONTEXT) {
                contexts++;
            }
        }
        if (parameters.size() != 2 || contexts != 1) {
            throw layouts.refused("its release " + operation.name() + " takes " + parameters.size()
                    + " parameters, where a release takes the context and the one thing it frees");
        }
        return new ReleasePlan(operation.name(), functionAt(operation.offset(), NativeSignature.of(NativeType.VOID,
                NativeType.ADDRESS, NativeType.ADDRESS)), context,
                parameters.get(0).kind() == DescribedKind.CONTEXT);
    }

    private ReleasePlan handleRelease(MemberPlan plan, String where) {
        if (plan.kind != DescribedKind.HANDLE || plan.releaseName == null) {
            return null;
        }
        ReleasePlan release = releases.get(plan.releaseName);
        if (release == null) {
            throw layouts.refused(where + " is freed by " + plan.releaseName + ", which is no release of the "
                    + "definition");
        }
        return release;
    }

    private BoundOperation operation(Description.Operation operation) {
        List<Description.Parameter> parameters = operation.parameters();
        String name = operation.name();
        if (parameters.size() > MOST_PARAMETERS) {
            throw refused(name, "takes " + parameters.size() + " parameters, more than the " + MOST_PARAMETERS
                    + " this binding calls");
        }
        Set<String> counted = new HashSet<>();
        for (Description.Parameter parameter : parameters) {
            if (parameter.count().isPresent()) {
                counted.add(parameter.count().get());
            }
        }
        BoundOperation.Step[] steps = new BoundOperation.Step[parameters.size()];
        NativeType[] types = new NativeType[parameters.size()];
        List<String> inputNames = new ArrayList<>();
        List<String> answerNames = new ArrayList<>();
        boolean refusal = false;
        for (int index = 0; index < parameters.size(); index++) {
            Description.Parameter parameter = parameters.get(index);
            types[index] = NativeType.ADDRESS;
            if (parameter.kind() == DescribedKind.CONTEXT) {
                steps[index] = new DescribedSteps.Context(context);
            } else if (parameter.role() == Description.INPUT) {
                steps[index] = input(operation, parameter, counted, types, index);
                if (!counted.contains(parameter.name())) {
                    inputNames.add(parameter.name());
                }
            } else if (parameter.role() == Description.ANSWER) {
                steps[index] = answer(operation, parameter);
                answerNames.add(parameter.name());
            } else if (parameter.role() == Description.REFUSAL && !refusal) {
                steps[index] = refusal(operation, parameter);
                refusal = true;
            } else {
                throw refused(name, "takes " + parameter.name() + " in role " + parameter.role() + ", which this "
                        + "binding cannot marshal");
            }
        }
        return new BoundOperation(description.definition(), name,
                functionAt(operation.offset(), NativeSignature.of(NativeType.INT, types)), steps, inputNames,
                new LinkedHashSet<>(inputNames), answerNames.size() == 1 ? answerNames.get(0) : null, bound);
    }

    private BoundOperation.Step input(Description.Operation operation, Description.Parameter parameter,
                                      Set<String> counted, NativeType[] types, int index) {
        int kind = parameter.kind();
        if (!INPUT_KINDS.contains(kind)) {
            throw unmarshalled(operation, parameter);
        }
        if (counted.contains(parameter.name())) {
            types[index] = NativeType.LONG;
            return new DescribedSteps.Count();
        }
        if (DescribedLayouts.isScalar(kind)) {
            types[index] = kind == DescribedKind.U32 ? NativeType.INT
                    : kind == DescribedKind.F64 ? NativeType.DOUBLE : NativeType.LONG;
            return new DescribedSteps.Scalar(operation.name(), parameter.name(), kind);
        }
        MemberPlan plan = reach(operation, parameter);
        int countIndex = -1;
        if (parameter.count().isPresent()) {
            countIndex = indexOf(operation, parameter.count().get());
        }
        return new DescribedSteps.Reached(operation.name(), plan, countIndex);
    }

    private BoundOperation.Step answer(Description.Operation operation, Description.Parameter parameter) {
        int kind = parameter.kind();
        if (!ANSWER_KINDS.contains(kind)) {
            throw unmarshalled(operation, parameter);
        }
        if (kind == DescribedKind.POINTER && parameter.element() == DescribedKind.VALUE) {
            throw refused(operation.name(), "answers " + parameter.name() + " as a pointer to a value, which is "
                    + "answered as a tree");
        }
        if (kind == DescribedKind.TEXT && parameter.release().isPresent()) {
            throw refused(operation.name(), "answers the text " + parameter.name() + " freed by "
                    + parameter.release().get() + ", and which pointer a text's release is handed is not yet ruled, "
                    + "so this binding frees none");
        }
        Optional<ReleasePlan> release = namedRelease(operation, parameter);
        MemberPlan plan = reach(operation, parameter);
        if (kind == DescribedKind.HANDLE) {
            plan.release = release.orElse(null);
            return new DescribedSteps.Answering(access, plan, CELL, false, null);
        }
        if (kind == DescribedKind.POINTER) {
            return new DescribedSteps.Answering(access, plan, plan.target.size, plan.target.leadingSize,
                    release.orElse(null));
        }
        if (kind == DescribedKind.VALUE) {
            return new DescribedSteps.Answering(access, plan, ValueTrees.TREE_SIZE, true, release.orElse(null));
        }
        if (release.isPresent()) {
            throw refused(operation.name(), "frees its answer " + parameter.name() + " of kind " + kind
                    + ", which owns nothing to free");
        }
        return new DescribedSteps.Answering(access, plan, CELL, false, null);
    }

    private BoundOperation.Step refusal(Description.Operation operation, Description.Parameter parameter) {
        if (parameter.kind() != DescribedKind.POINTER || parameter.element() != DescribedKind.STRUCT) {
            throw unmarshalled(operation, parameter);
        }
        StructPlan type = layouts.plan(parameter.type(), "its operation " + operation.name() + "'s "
                + parameter.name());
        long detail = textMember(operation, type, "detail");
        long fix = textMember(operation, type, "fix");
        return new DescribedSteps.Refusing(access, type, detail, fix, namedRelease(operation, parameter).orElse(null));
    }

    private long textMember(Description.Operation operation, StructPlan type, String member) {
        for (Description.Member described : layouts.type(type.name).members()) {
            if (described.name().equals(member) && described.kind() == DescribedKind.TEXT) {
                return described.offset();
            }
        }
        throw refused(operation.name(), "refuses through " + type.name + ", which carries no " + member + " text");
    }

    private Optional<ReleasePlan> namedRelease(Description.Operation operation, Description.Parameter parameter) {
        if (!parameter.release().isPresent()) {
            return Optional.empty();
        }
        ReleasePlan release = releases.get(parameter.release().get());
        if (release == null) {
            throw refused(operation.name(), "frees " + parameter.name() + " by " + parameter.release().get()
                    + ", which is no release of the definition");
        }
        return Optional.of(release);
    }

    private MemberPlan reach(Description.Operation operation, Description.Parameter parameter) {
        String where = "its operation " + operation.name() + "'s " + parameter.name();
        layouts.refuseUnreachableElement(parameter.kind(), parameter.element(), parameter.type(), where);
        MemberPlan plan = layouts.reachOf(parameter.name(), parameter.kind(), parameter.element(), parameter.type(),
                parameter.release(), 0, MemberPlan.NO_COUNT, where);
        if (parameter.role() == Description.INPUT) {
            plan.release = handleRelease(plan, where);
        }
        return plan;
    }

    private static int indexOf(Description.Operation operation, String name) {
        List<Description.Parameter> parameters = operation.parameters();
        for (int index = 0; index < parameters.size(); index++) {
            if (parameters.get(index).name().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    private DescriptionRefused unmarshalled(Description.Operation operation, Description.Parameter parameter) {
        return refused(operation.name(), "takes " + parameter.name() + " of kind " + parameter.kind() + " in role "
                + parameter.role() + ", which this binding cannot marshal");
    }

    private DescriptionRefused refused(String operation, String detail) {
        return layouts.refused("its operation " + operation + " " + detail);
    }
}
