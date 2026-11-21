package com.weivel.core;

import java.util.ArrayList;
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
 * nothing up. The description has passed the standard's check, which alone rules the shapes the convention takes:
 * the kinds each role takes, one context and first, at most one refusal and last, and a release of two parameters.
 * Every description it nests is bound once too, against a definition struct holding no function, so one this
 * platform or binding cannot bind is refused here rather than when an answer first carries it.
 *
 * @implNote A definition an answer carries is bound afresh from its nested description on every answer: its function
 *     pointers, context and releases are read from the struct that answer pointed at and never kept by address, since
 *     a provider may free a definition it lent and lend another at the same address.
 */
final class DescribedBinder {
    private static final int MOST_PARAMETERS = 7;
    private static final long CELL = Layout.LONG.byteSize();

    private final NativeAccess access;
    private final DescribedLayouts layouts;
    private final Description description;
    private final long definition;
    private final long answeredSize;
    private final BoundOperation.Bound bound;
    private final DescribedCall.Carrier carrier;
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
        this.carrier = description.definitions().isEmpty() ? DescribedCall.Carrier.NONE
                : (nested, pointer, carried) -> carry(access, description, nested, pointer, bound, carried);
    }

    /**
     * Binds the definition at {@code definition} from {@code description}.
     *
     * @throws DescriptionRefused for a description this binding cannot bind on this platform, or a definition that
     *     ends before its first shape
     */
    static BoundDefinition bind(NativeAccess access, Description description, long definition, Object addressed,
                                BoundOperation.Bound bound) {
        DescribedBinder binder = new DescribedBinder(access, new DescribedLayouts(access, description), definition,
                bound);
        Map<String, BoundOperation> operations = binder.operations();
        for (Description nested : description.definitions()) {
            refuseUnbindable(access, nested);
        }
        return new BoundDefinition(description.definition(), addressed, definition, description, operations);
    }

    /**
     * Binds a definition with no library behind it, as {@link BoundDefinition#bindDefinition} does, on the backend this
     * Java runs on.
     *
     * @throws DescriptionRefused for a description this binding cannot bind on this platform, or a definition that
     *     ends before its first shape
     */
    static BoundDefinition bindDefinition(Object definition, Object description) {
        NativeAccess access = NativeBackends.current();
        return bind(access, new DescriptionReader(access).read(access.addressOf(description)),
                access.addressOf(definition), definition, new BoundOperation.Bound(access, new SpareArena(access),
                        new DescribedHandles(), CallGate.OPEN));
    }

    /** Binds a nested description against a zeroed struct of its definition, which offers no operation. */
    private static void refuseUnbindable(NativeAccess access, Description nested) {
        Description.Type type = nested.type(nested.definition()).orElseThrow(() -> new DescriptionRefused(
                nested.definition(), "it describes no struct " + nested.definition() + " for its definition"));
        long empty = access.allocate(Math.max(type.size(), Integer.BYTES));
        try {
            access.clear(empty, 0, Math.max(type.size(), Integer.BYTES));
            access.putInt(empty, 0, (int) type.size());
            bind(access, nested, empty, access.addressed(empty, type.size()), new BoundOperation.Bound(access,
                    new SpareArena(access), new DescribedHandles(), CallGate.OPEN));
        } finally {
            access.free(empty);
        }
    }

    /**
     * Binds the definition at {@code pointer}, which an answer got through {@code bound} carried, in a scope of its
     * own whose holdings are a child of {@code bound}'s, and adds that scope to {@code carried}.
     */
    private static BoundDefinition carry(NativeAccess access, Description description, String definition,
                                         long pointer, BoundOperation.Bound bound,
                                         List<KeptAnswer.Carried> carried) {
        Description nested = description.nested(definition).orElseThrow(() -> new IllegalStateException(
                "an answer of " + description.definition() + " carried the definition " + definition + ", which its "
                        + "description does not nest"));
        CarriedScope scope = CarriedScope.carried();
        DescribedHandles holdings = bound.holdings.child();
        BoundDefinition answered;
        try {
            answered = bind(access, nested, pointer, access.addressed(pointer, Integer.toUnsignedLong(
                    access.getInt(pointer, 0))), bound.carried(holdings, scope));
        } catch (DescriptionRefused refused) {
            throw new IllegalStateException("the provider answered a definition this binding cannot bind: "
                    + refused.getMessage(), refused);
        }
        carried.add(new KeptAnswer.Carried(scope, holdings));
        return answered;
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
                break;
            }
        }
        for (Description.Operation operation : description.operations()) {
            if (operation.returns() == Description.NOTHING) {
                releases.put(operation.name(), new ReleasePlan(operation.name(), functionAt(operation.offset(),
                        NativeSignature.of(NativeType.VOID, NativeType.ADDRESS, NativeType.ADDRESS)), context));
            }
        }
        for (MemberPlan member : layouts.everyMemberPlan()) {
            member.release = handleRelease(member);
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

    private ReleasePlan handleRelease(MemberPlan plan) {
        return plan.kind == DescribedKind.HANDLE && plan.releaseName != null ? releases.get(plan.releaseName) : null;
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
        boolean keepsAnswers = false;
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
                MemberPlan plan = reach(operation, parameter);
                steps[index] = answer(parameter, plan);
                keepsAnswers |= DescribedLayouts.carriesDefinition(plan);
                answerNames.add(parameter.name());
            } else {
                steps[index] = refusal(operation, parameter);
            }
        }
        return new BoundOperation(description.definition(), name,
                functionAt(operation.offset(), NativeSignature.of(NativeType.INT, types)), steps, inputNames,
                new LinkedHashSet<>(inputNames), answerNames.size() == 1 ? answerNames.get(0) : null, bound, carrier,
                keepsAnswers);
    }

    private BoundOperation.Step input(Description.Operation operation, Description.Parameter parameter,
                                      Set<String> counted, NativeType[] types, int index) {
        int kind = parameter.kind();
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

    private BoundOperation.Step answer(Description.Parameter parameter, MemberPlan plan) {
        int kind = parameter.kind();
        Optional<ReleasePlan> release = namedRelease(parameter);
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
        return new DescribedSteps.Answering(access, plan, CELL, false, null);
    }

    private BoundOperation.Step refusal(Description.Operation operation, Description.Parameter parameter) {
        StructPlan type = layouts.plan(parameter.type(), "its operation " + operation.name() + "'s "
                + parameter.name());
        return new DescribedSteps.Refusing(access, type, textOffset(type, "detail"), textOffset(type, "fix"),
                namedRelease(parameter).orElse(null));
    }

    private static long textOffset(StructPlan type, String member) {
        return type.visible[type.positionOf(member)].offset;
    }

    private Optional<ReleasePlan> namedRelease(Description.Parameter parameter) {
        return parameter.release().map(releases::get);
    }

    private MemberPlan reach(Description.Operation operation, Description.Parameter parameter) {
        String where = "its operation " + operation.name() + "'s " + parameter.name();
        layouts.refuseUnreachableElement(parameter.kind(), parameter.element(), parameter.type(), where);
        MemberPlan plan = layouts.reachOf(parameter.name(), parameter.kind(), parameter.element(), parameter.type(),
                parameter.release(), 0, MemberPlan.NO_COUNT, where);
        if (parameter.role() == Description.INPUT) {
            plan.release = handleRelease(plan);
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

    private DescriptionRefused refused(String operation, String detail) {
        return layouts.refused("its operation " + operation + " " + detail);
    }
}
