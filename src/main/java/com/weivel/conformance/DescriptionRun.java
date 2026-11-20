package com.weivel.conformance;

import com.weivel.core.Answer;
import com.weivel.core.BoundDefinition;
import com.weivel.core.CoreConstants;
import com.weivel.core.DescribedAnswer;
import com.weivel.core.DescribedHandle;
import com.weivel.core.DescribedKind;
import com.weivel.core.Description;
import com.weivel.core.EdgeSource;
import com.weivel.core.ForeignMemory;
import com.weivel.core.Manifest;
import com.weivel.host.Entry;
import com.weivel.host.LibraryDeclaration;
import com.weivel.host.LoadedLibrary;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Drives a {@code description} vector: the standard's described library loaded through the host, each holder a plugin
 * opened with its activate left in flight, and every call made through the {@link BoundDefinition} the holder
 * resolved. A vector's JSON is translated into Java values by the kinds its description gives, and every answer back,
 * as {@code vectors/layer/LAYER.md} writes them.
 *
 * @implNote The mirror whose releases a {@code readReleases} step counts is found as the holder's resolved object that
 *     has a {@code count_releases} operation, since a vector does not name the service.
 */
final class DescriptionRun {
    private static final String COUNT_RELEASES = "count_releases";
    private static final String HANDLE = "handle";

    private final DriverWorld world;
    private final Path describedBinary;
    private final LibraryDeclaration declaration;
    private final Map<String, Map<String, Object>> resolved = new HashMap<>();
    private final Map<String, Long> answeredAddresses = new HashMap<>();
    private final Map<String, DescribedHandle> kept = new HashMap<>();
    private final List<Object> got = new ArrayList<>();
    private final List<Object> calls = new ArrayList<>();
    private final List<Map<String, Object>> releaseReads = new ArrayList<>();
    private Answer<LoadedLibrary> load;

    private DescriptionRun(DriverWorld world, Path describedBinary, LibraryDeclaration declaration) {
        this.world = world;
        this.describedBinary = describedBinary;
        this.declaration = declaration;
    }

    static Map<String, Observations.Observer> drive(LayerVector vector, DriverWorld world, Path describedBinary) {
        String where = "the vector " + vector.id() + "'s given";
        Map<String, Object> given = vector.given();
        Shapes.refuseUnknownKeys(where, given, "declaration", "holders", "steps");
        DescriptionRun run = new DescriptionRun(world, describedBinary,
                LibraryRun.declarationOf(where, Shapes.object(where, given, "declaration")));
        run.open(Shapes.texts(where, given, "holders"));
        List<Map<String, Object>> steps = Shapes.objects(where, given, "steps");
        for (int index = 0; index < steps.size(); index++) {
            run.step(where + "'s step " + index, steps.get(index));
        }
        return run.observers();
    }

    private void open(List<String> holders) {
        List<String> ids = new ArrayList<>();
        for (LibraryDeclaration.Service service : declaration.services()) {
            ids.add(service.id());
        }
        for (String holder : holders) {
            Manifest manifest = Manifests.services(holder, Collections.<String>emptyList(), ids);
            CompletableFuture<Void> activate = new CompletableFuture<>();
            Entry entry = context -> activate;
            world.register(manifest, entry);
        }
        world.openInFlight(holders, EdgeSource.CAPABILITIES);
        world.startTheSteps();
    }

    private void step(String where, Map<String, Object> step) {
        String named = Shapes.theOneNamed(where, step, "load", "get", "call", "release", "readReleases", "unload");
        if (named.equals("load")) {
            Shapes.refuseUnknownKeys(where, step, "load");
            Shapes.requireTrue(where, step, "load");
            load();
        } else if (named.equals("unload")) {
            Shapes.refuseUnknownKeys(where, step, "unload");
            Shapes.requireTrue(where, step, "unload");
            unload();
        } else if (named.equals("get")) {
            Shapes.refuseUnknownKeys(where, step, "holder", "get");
            get(Shapes.text(where, step, "holder"), Shapes.text(where, step, "get"));
        } else if (named.equals("call")) {
            Shapes.refuseUnknownKeys(where, step, "holder", "call", "operation", "with", "keep");
            Optional<String> keep = step.containsKey("keep") ? Optional.of(Shapes.text(where, step, "keep"))
                    : Optional.<String>empty();
            calls.add(call(where, bound(Shapes.text(where, step, "holder"), Shapes.text(where, step, "call")),
                    Shapes.text(where, step, "operation"), Shapes.object(where, step, "with"), keep));
        } else if (named.equals("release")) {
            Shapes.refuseUnknownKeys(where, step, "holder", "release");
            Shapes.text(where, step, "holder");
            handle(where, Shapes.text(where, step, "release")).release();
        } else {
            Shapes.refuseUnknownKeys(where, step, "holder", "readReleases");
            Shapes.requireTrue(where, step, "readReleases");
            releaseReads.add(readReleases(Shapes.text(where, step, "holder")));
        }
    }

    private void load() {
        load = world.host.loadLibrary(describedBinary, declaration);
        if (load instanceof Answer.Ok) {
            answeredAddresses.putAll(LibraryRun.answeredAddresses(describedBinary, Optional.of(declaration)));
        } else if (load instanceof Answer.Mistake) {
            world.loadRefused(((Answer.Mistake<LoadedLibrary>) load).detail());
        }
    }

    private void unload() {
        if (!(load instanceof Answer.Ok)) {
            throw new IllegalStateException("an unload step after a load that answered " + load);
        }
        Answer<Boolean> unloaded = world.host.unloadLibrary(((Answer.Ok<LoadedLibrary>) load).value());
        if (!(unloaded instanceof Answer.Ok) || !((Answer.Ok<Boolean>) unloaded).value()) {
            throw new IllegalStateException("the unload answered " + unloaded);
        }
    }

    private void get(String holder, String id) {
        Answer<Object> answer = world.context(holder).get(id);
        got.add(answer instanceof Answer.Ok);
        if (answer instanceof Answer.Ok) {
            resolvedBy(holder).put(id, ((Answer.Ok<Object>) answer).value());
        }
    }

    private Map<String, Object> resolvedBy(String holder) {
        Map<String, Object> held = resolved.get(holder);
        if (held == null) {
            held = new LinkedHashMap<>();
            resolved.put(holder, held);
        }
        return held;
    }

    private BoundDefinition bound(String holder, String id) {
        Object object = resolvedBy(holder).get(id);
        if (!(object instanceof BoundDefinition)) {
            throw new IllegalStateException(holder + " resolved no bound definition " + id + ", but " + object);
        }
        return (BoundDefinition) object;
    }

    private DescribedHandle handle(String where, String name) {
        DescribedHandle handle = kept.get(name);
        if (handle == null) {
            throw new IllegalArgumentException(where + " names the handle " + name + ", which no step kept");
        }
        return handle;
    }

    private Map<String, Object> readReleases(String holder) {
        for (Object object : resolvedBy(holder).values()) {
            if (object instanceof BoundDefinition
                    && ((BoundDefinition) object).operationNames().contains(COUNT_RELEASES)) {
                DescribedAnswer answer = ((BoundDefinition) object).operation(COUNT_RELEASES).call();
                if (!(answer instanceof DescribedAnswer.Answered)) {
                    throw new IllegalStateException(COUNT_RELEASES + " answered " + answer);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> counts = (Map<String, Object>) ((DescribedAnswer.Answered) answer).value();
                return counts;
            }
        }
        throw new IllegalStateException(holder + " resolved nothing with a " + COUNT_RELEASES + " operation");
    }

    private Map<String, Object> call(String where, BoundDefinition definition, String operation,
                                     Map<String, Object> with, Optional<String> keep) {
        Description description = definition.description();
        Description.Operation described = description.operation(operation).orElseThrow(() ->
                new IllegalArgumentException(where + " calls " + operation + ", which the description lacks"));
        Values values = new Values(description, keep);
        Map<String, Object> inputs = new LinkedHashMap<>();
        Map<String, Description.Parameter> answers = new LinkedHashMap<>();
        for (Description.Parameter parameter : described.parameters()) {
            if (parameter.role() == Description.ANSWER) {
                answers.put(parameter.name(), parameter);
            } else if (with.containsKey(parameter.name())) {
                inputs.put(parameter.name(), values.toJava(parameter.kind(), parameter.element(), parameter.type(),
                        with.get(parameter.name()), where));
            }
        }
        for (String name : with.keySet()) {
            if (!inputs.containsKey(name)) {
                inputs.put(name, with.get(name));
            }
        }
        return outcome(definition.operation(operation).call(inputs), answers, values);
    }

    private Map<String, Object> outcome(DescribedAnswer answer, Map<String, Description.Parameter> answers,
                                        Values values) {
        Map<String, Object> call = new LinkedHashMap<>();
        if (answer instanceof DescribedAnswer.Answered) {
            call.put("status", "ok");
            DescribedAnswer.Answered answered = (DescribedAnswer.Answered) answer;
            if (answers.size() != 1) {
                throw new IllegalStateException("a call vector reads one answer, and the operation has " + answers);
            }
            if (answered.hasValue()) {
                Description.Parameter parameter = answers.values().iterator().next();
                call.put("answer", values.toJson(parameter.kind(), parameter.element(), parameter.type(),
                        answered.value()));
            }
        } else if (answer instanceof DescribedAnswer.None) {
            call.put("status", "none");
        } else if (answer instanceof DescribedAnswer.Refused) {
            DescribedAnswer.Refused refused = (DescribedAnswer.Refused) answer;
            call.put("status", "refused");
            call.put("detail", refused.detail().isPresent() ? refused.detail().get() : StandardJson.NULL);
            call.put("fix", refused.fix().isPresent() ? refused.fix().get() : StandardJson.NULL);
        } else if (answer instanceof DescribedAnswer.Failed) {
            call.put("status", statusName(((DescribedAnswer.Failed) answer).status()));
        } else {
            throw new IllegalStateException("a call through a held definition answered " + answer);
        }
        return call;
    }

    /** The status as LAYER.md writes it: the core's name, lowercase without its prefix. */
    private static String statusName(int status) {
        switch (status) {
            case CoreConstants.STATUS_MALFORMED:
                return "malformed";
            case CoreConstants.STATUS_REFUSED:
                return "refused";
            case CoreConstants.STATUS_OUT_OF_MEMORY:
                return "out_of_memory";
            default:
                throw new IllegalStateException("an operation answered the status " + status
                        + ", which the core does not publish");
        }
    }

    private boolean isSameObject() {
        for (Map<String, Object> held : resolved.values()) {
            for (Map.Entry<String, Object> object : held.entrySet()) {
                Long answered = answeredAddresses.get(object.getKey());
                long address = object.getValue() instanceof BoundDefinition
                        ? ((BoundDefinition) object.getValue()).address()
                        : ForeignMemory.addressOf(object.getValue());
                if (answered != null && address != answered) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Each read of the counts after the first, minus the first, per kind, as decimal strings. */
    private List<Object> released() {
        List<Object> differences = new ArrayList<>();
        for (int index = 1; index < releaseReads.size(); index++) {
            Map<String, Object> difference = new LinkedHashMap<>();
            for (Map.Entry<String, Object> count : releaseReads.get(0).entrySet()) {
                long later = (Long) releaseReads.get(index).get(count.getKey());
                difference.put(count.getKey(), Long.toUnsignedString(later - (Long) count.getValue()));
            }
            differences.add(difference);
        }
        return differences;
    }

    private Map<String, Observations.Observer> observers() {
        Map<String, Observations.Observer> observers = new HashMap<>();
        observers.put("loaded", expectation -> load instanceof Answer.Ok);
        observers.put("callersMistake", expectation -> load instanceof Answer.Mistake);
        observers.put("got", expectation -> new ArrayList<>(got));
        observers.put("sameObject", expectation -> isSameObject());
        observers.put("calls", expectation -> new ArrayList<>(calls));
        observers.put("released", expectation -> released());
        observers.put("reported", expectation -> world.reported());
        return observers;
    }

    /**
     * Translates one call's values between a vector's JSON and the Java values a {@link BoundDefinition} takes and
     * answers, by the kinds the description gives each: a 64-bit integer as a decimal string, bytes as an array of
     * numbers, a handle as {@code {handle: name}}, and a number the standard's JSON reader holds as a {@link Long}
     * whenever it is integral.
     */
    private final class Values {
        private final Description description;
        private final Optional<String> keep;

        Values(Description description, Optional<String> keep) {
            this.description = description;
            this.keep = keep;
        }

        Object toJava(int kind, int element, Optional<String> type, Object json, String where) {
            switch (kind) {
                case DescribedKind.U64:
                case DescribedKind.SIZE:
                    return Long.parseUnsignedLong((String) json);
                case DescribedKind.I64:
                    return Long.parseLong((String) json);
                case DescribedKind.F64:
                    return ((Number) json).doubleValue();
                case DescribedKind.TEXT:
                    return json == StandardJson.NULL ? null : json;
                case DescribedKind.TEXTS:
                    return list(json, item -> item == StandardJson.NULL ? null : item);
                case DescribedKind.BYTES:
                    List<?> numbers = (List<?>) json;
                    byte[] bytes = new byte[numbers.size()];
                    for (int index = 0; index < bytes.length; index++) {
                        bytes[index] = (byte) ((Long) numbers.get(index)).longValue();
                    }
                    return bytes;
                case DescribedKind.STRUCT:
                    return struct(type, json, where);
                case DescribedKind.POINTER:
                    return toJava(element, 0, type, json, where);
                case DescribedKind.ARRAY:
                    return list(json, item -> toJava(element, 0, type, item, where));
                case DescribedKind.VALUE:
                    return value(json);
                case DescribedKind.HANDLE:
                    Map<String, Object> named = Shapes.object(where, json);
                    return handle(where, Shapes.text(where, named, HANDLE));
                default:
                    return json;
            }
        }

        private Map<String, Object> struct(Optional<String> type, Object json, String where) {
            Description.Type described = description.type(type.orElse("")).orElseThrow(() ->
                    new IllegalArgumentException(where + " reaches a struct its description lacks"));
            Map<String, Object> members = new LinkedHashMap<>();
            for (Map.Entry<String, Object> member : Shapes.object(where, json).entrySet()) {
                Optional<Description.Member> found = member(described, member.getKey());
                members.put(member.getKey(), found.isPresent()
                        ? toJava(found.get().kind(), found.get().element(), found.get().type(), member.getValue(),
                        where + "." + member.getKey())
                        : member.getValue());
            }
            return members;
        }

        private Object value(Object json) {
            if (json == StandardJson.NULL) {
                return null;
            }
            if (json instanceof List) {
                return list(json, this::value);
            }
            if (json instanceof Map) {
                Map<String, Object> record = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) json).entrySet()) {
                    record.put((String) entry.getKey(), value(entry.getValue()));
                }
                return record;
            }
            return json;
        }

        Object toJson(int kind, int element, Optional<String> type, Object java) {
            switch (kind) {
                case DescribedKind.U64:
                case DescribedKind.SIZE:
                    return Long.toUnsignedString((Long) java);
                case DescribedKind.I64:
                    return Long.toString((Long) java);
                case DescribedKind.F64:
                    return number((Double) java);
                case DescribedKind.TEXT:
                    return java == null ? StandardJson.NULL : java;
                case DescribedKind.TEXTS:
                    return list(java, item -> item == null ? StandardJson.NULL : item);
                case DescribedKind.BYTES:
                    List<Object> numbers = new ArrayList<>();
                    for (byte value : (byte[]) java) {
                        numbers.add((long) (value & 0xFF));
                    }
                    return numbers;
                case DescribedKind.STRUCT:
                    return structJson(type, java);
                case DescribedKind.POINTER:
                    return toJson(element, 0, type, java);
                case DescribedKind.ARRAY:
                    return list(java, item -> toJson(element, 0, type, item));
                case DescribedKind.VALUE:
                    return valueJson(java);
                case DescribedKind.HANDLE:
                    return kept((DescribedHandle) java);
                default:
                    return java;
            }
        }

        private Map<String, Object> structJson(Optional<String> type, Object java) {
            Description.Type described = description.type(type.orElse("")).get();
            Map<String, Object> members = new LinkedHashMap<>();
            for (Map.Entry<?, ?> member : ((Map<?, ?>) java).entrySet()) {
                Description.Member found = member(described, (String) member.getKey()).get();
                members.put(found.name(), toJson(found.kind(), found.element(), found.type(), member.getValue()));
            }
            return members;
        }

        private Object valueJson(Object java) {
            if (java == null) {
                return StandardJson.NULL;
            }
            if (java instanceof Double) {
                return number((Double) java);
            }
            if (java instanceof List) {
                return list(java, this::valueJson);
            }
            if (java instanceof Map) {
                Map<String, Object> record = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) java).entrySet()) {
                    record.put((String) entry.getKey(), valueJson(entry.getValue()));
                }
                return record;
            }
            return java;
        }

        private Map<String, Object> kept(DescribedHandle handle) {
            if (!keep.isPresent()) {
                throw new IllegalArgumentException("a call answered a handle and its step names no keep");
            }
            if (kept.containsKey(keep.get()) && kept.get(keep.get()) != handle) {
                throw new IllegalStateException("a call answered a second handle to keep as " + keep.get());
            }
            kept.put(keep.get(), handle);
            return Collections.<String, Object>singletonMap(HANDLE, keep.get());
        }
    }

    /** A number as the standard's JSON reader holds it: a {@link Long} when it is integral, else a {@link Double}. */
    private static Object number(double number) {
        if (number >= -9223372036854775808.0 && number < 9223372036854775808.0 && number == Math.rint(number)) {
            return (long) number;
        }
        return number;
    }

    private static Optional<Description.Member> member(Description.Type type, String name) {
        for (Description.Member member : type.members()) {
            if (member.name().equals(name)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    private static List<Object> list(Object items, Function<Object, Object> each) {
        List<Object> translated = new ArrayList<>();
        for (Object item : (List<?>) items) {
            translated.add(each.apply(item));
        }
        return translated;
    }
}
