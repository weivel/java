package com.weivel.conformance;

import com.weivel.context.PluginContext;
import com.weivel.context.PluginEvent;
import com.weivel.core.Answer;
import com.weivel.core.EdgeSource;
import com.weivel.core.Manifest;
import com.weivel.core.ForeignFunction;
import com.weivel.core.ForeignLibrary;
import com.weivel.core.ForeignMemory;
import com.weivel.core.NativeType;
import com.weivel.host.Entry;
import com.weivel.host.LibraryAnswer;
import com.weivel.host.LibraryDeclaration;
import com.weivel.host.LibraryService;
import com.weivel.host.LoadedLibrary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Drives a {@code library} vector: the core's echo library loaded through the host, each holder a plugin opened with
 * its activate left in flight, and every step run on the suite's thread through the host's and the contexts' public
 * surface, so once a step returns the layer has nothing left to run from it.
 *
 * <p>The host forgets a plugin as the last part of its release, so the only time a released plugin is not yet
 * forgotten is while the events its release queued are dispatched. A {@code get} a vector makes between a holder's
 * {@code releaseHolder} and its {@code forgetHolder} is made then, as a late call, from the holder's listener as it
 * hears the first revocation its release queued, and the {@code forgetHolder} finds the holder forgotten.
 *
 * @implNote An {@code echo_definition} is read as the echo library's header lays it out: its {@code uint32_t size},
 * then four pointers each at the platform's pointer alignment, the context and the {@code echo}, {@code release}
 * and {@code count_unloads} operations, so on a 64-bit platform they sit at 8, 16, 24 and 32.
 */
final class LibraryRun {
    private static final long WAIT_SECONDS = 10;
    private static final int CONTEXT = 1;
    private static final int ECHO = 2;
    private static final int RELEASE = 3;
    private static final int COUNT_UNLOADS = 4;

    private final DriverWorld world;
    private final Path echoBinary;
    private final Optional<LibraryDeclaration> declaration;
    private final ForeignMemory memory;
    private final long counter;
    private final Map<String, CompletableFuture<Void>> activates = new HashMap<>();
    private final Map<String, Map<String, Object>> resolved = new HashMap<>();
    private final Map<String, Long> answeredAddresses;
    private final List<Object> got = new ArrayList<>();
    private final List<Object> echoed = new ArrayList<>();
    private final List<List<String>> revoked = new ArrayList<>();
    private final Map<String, List<PluginEvent.Revoked>> revocations = new HashMap<>();
    private final List<Object> holdingsReleased = new ArrayList<>();
    private final Map<String, List<String>> lateGets = new HashMap<>();
    private final Set<Integer> consumed = new HashSet<>();
    private final List<Object> unloads = new ArrayList<>();
    private Answer<LoadedLibrary> load;
    private int failuresBeforeTheSteps;
    private int logsBeforeTheSteps;

    private LibraryRun(DriverWorld world, Path echoBinary, Optional<LibraryDeclaration> declaration,
                       ForeignMemory memory) {
        this.world = world;
        this.echoBinary = echoBinary;
        this.declaration = declaration;
        this.memory = memory;
        this.counter = memory.allocate(Long.BYTES);
        this.answeredAddresses = answeredAddresses(echoBinary, declaration);
    }

    static Map<String, Observations.Observer> drive(LayerVector vector, DriverWorld world, Path echoBinary,
                                                    ForeignMemory memory) {
        String where = "the vector " + vector.id() + "'s given";
        Map<String, Object> given = vector.given();
        Shapes.refuseUnknownKeys(where, given, "declaration", "owners", "holders", "steps");
        Optional<LibraryDeclaration> declaration = given.containsKey("declaration")
                ? Optional.of(declarationOf(where, Shapes.object(where, given, "declaration")))
                : Optional.<LibraryDeclaration>empty();
        LibraryRun run = new LibraryRun(world, echoBinary, declaration, memory);
        List<Map<String, Object>> owners = given.containsKey("owners")
                ? Shapes.objects(where, given, "owners")
                : Collections.<Map<String, Object>>emptyList();
        run.open(where, owners, Shapes.texts(where, given, "holders"));
        List<Map<String, Object>> steps = Shapes.objects(where, given, "steps");
        for (int index = 0; index < steps.size(); index++) {
            if (!run.consumed.contains(index)) {
                run.step(where + "'s step " + index, steps, index);
            }
        }
        return run.observers();
    }

    private static LibraryDeclaration declarationOf(String where, Map<String, Object> declaration) {
        String declarationWhere = where + "'s declaration";
        Shapes.refuseUnknownKeys(declarationWhere, declaration, "importName", "version", "release", "services");
        List<LibraryDeclaration.Service> services = new ArrayList<>();
        for (Map<String, Object> service : Shapes.objects(declarationWhere, declaration, "services")) {
            String serviceWhere = declarationWhere + "'s service";
            Shapes.refuseUnknownKeys(serviceWhere, service, "id", "functions");
            List<LibraryDeclaration.Function> functions = new ArrayList<>();
            for (Map<String, Object> function : Shapes.objects(serviceWhere, service, "functions")) {
                Shapes.refuseUnknownKeys(serviceWhere + "'s function", function, "name", "symbol", "kind");
                functions.add(new LibraryDeclaration.Function(Shapes.text(serviceWhere, function, "name"),
                        Shapes.text(serviceWhere, function, "symbol"),
                        kindOf(serviceWhere, Shapes.text(serviceWhere, function, "kind")), 0));
            }
            services.add(new LibraryDeclaration.Service(Shapes.text(serviceWhere, service, "id"), functions));
        }
        return new LibraryDeclaration(Shapes.text(declarationWhere, declaration, "importName"),
                Shapes.text(declarationWhere, declaration, "version"),
                declaration.containsKey("release")
                        ? Optional.of(Shapes.text(declarationWhere, declaration, "release"))
                        : Optional.<String>empty(), services);
    }

    private static int kindOf(String where, String kind) {
        if (kind.equals("bytes")) {
            return LibraryDeclaration.BYTES;
        }
        if (kind.equals("definition")) {
            return LibraryDeclaration.DEFINITION;
        }
        throw new IllegalArgumentException(where + " names the kind " + kind + ", which no driver knows");
    }

    /**
     * The address each definition's symbol answers, read through a load of the binary the suite closes again before
     * the first step, so that only the layer holds the binary across the steps.
     */
    private static Map<String, Long> answeredAddresses(Path echoBinary, Optional<LibraryDeclaration> declaration) {
        Map<String, String> symbols = new LinkedHashMap<>();
        for (LibraryDeclaration.Service service : servicesOf(declaration)) {
            LibraryDeclaration.Function first = service.functions().get(0);
            if (first.kind() == LibraryDeclaration.DEFINITION) {
                symbols.put(service.id(), first.symbol());
            }
        }
        Map<String, Long> addresses = new HashMap<>();
        if (symbols.isEmpty()) {
            return addresses;
        }
        Answer<ForeignLibrary> opened = ForeignLibrary.open(echoBinary, new ArrayList<>(symbols.values()));
        if (!(opened instanceof Answer.Ok)) {
            throw new IllegalStateException("the suite could not open " + echoBinary + ": " + opened);
        }
        try (ForeignLibrary library = ((Answer.Ok<ForeignLibrary>) opened).value()) {
            for (Map.Entry<String, String> symbol : symbols.entrySet()) {
                addresses.put(symbol.getKey(), library.function(symbol.getValue(), NativeType.ADDRESS).call());
            }
        }
        return addresses;
    }

    private static List<LibraryDeclaration.Service> servicesOf(Optional<LibraryDeclaration> declaration) {
        return declaration.isPresent() ? declaration.get().services()
                : Collections.<LibraryDeclaration.Service>emptyList();
    }

    /** Opens the owners, then the holders, each consuming every id the declaration lists and every id an owner offers. */
    private void open(String where, List<Map<String, Object>> owners, List<String> holders) {
        List<String> ids = new ArrayList<>();
        for (LibraryDeclaration.Service service : servicesOf(declaration)) {
            ids.add(service.id());
        }
        List<String> opened = new ArrayList<>();
        for (Map<String, Object> owner : owners) {
            String ownerWhere = where + "'s owner";
            Shapes.refuseUnknownKeys(ownerWhere, owner, "id", "provides");
            String name = Shapes.text(ownerWhere, owner, "id");
            List<String> provides = Shapes.texts(ownerWhere, owner, "provides");
            ids.addAll(provides);
            registerInFlight(Manifests.services(name, provides, Collections.<String>emptyList()));
            opened.add(name);
        }
        for (String holder : holders) {
            registerInFlight(Manifests.services(holder, Collections.<String>emptyList(), ids));
            opened.add(holder);
        }
        world.openInFlight(opened, EdgeSource.CAPABILITIES);
        for (String plugin : opened) {
            world.context(plugin).listen(event -> heard(plugin, event));
        }
        failuresBeforeTheSteps = world.failures().size();
        logsBeforeTheSteps = world.logs().size();
    }

    private void registerInFlight(Manifest manifest) {
        CompletableFuture<Void> activate = new CompletableFuture<>();
        activates.put(manifest.importName(), activate);
        Entry entry = context -> activate;
        world.register(manifest, entry);
    }

    private void heard(String plugin, PluginEvent event) {
        if (event instanceof PluginEvent.HoldingsReleased) {
            PluginEvent.HoldingsReleased released = (PluginEvent.HoldingsReleased) event;
            holdingsReleased.add(Arrays.asList(plugin, released.id()));
            return;
        }
        if (!(event instanceof PluginEvent.Revoked)) {
            return;
        }
        PluginEvent.Revoked revocation = (PluginEvent.Revoked) event;
        revocationsOf(plugin).add(revocation);
        for (Map.Entry<String, Object> held : resolvedBy(plugin).entrySet()) {
            if (held.getValue() == revocation.object()) {
                revoked.add(Arrays.asList(plugin, held.getKey()));
            }
        }
        List<String> late = lateGets.remove(plugin);
        if (late != null) {
            for (String id : late) {
                get(plugin, id);
            }
        }
    }

    private List<PluginEvent.Revoked> revocationsOf(String plugin) {
        List<PluginEvent.Revoked> heard = revocations.get(plugin);
        if (heard == null) {
            heard = new ArrayList<>();
            revocations.put(plugin, heard);
        }
        return heard;
    }

    private void step(String where, List<Map<String, Object>> steps, int index) {
        Map<String, Object> step = steps.get(index);
        String named = Shapes.theOneNamed(where, step, "load", "get", "echo", "call", "drop", "countUnloads",
                "unload", "releaseHolding", "releaseHolder", "quarantine", "readUnloads", "registerAwaitingReleases",
                "forgetHolder");
        if (named.equals("load")) {
            Shapes.refuseUnknownKeys(where, step, "load");
            Shapes.requireTrue(where, step, "load");
            if (!declaration.isPresent()) {
                throw new IllegalArgumentException(where + " loads a library the vector declares none of");
            }
            load = world.host.loadLibrary(echoBinary, declaration.get());
        } else if (named.equals("unload")) {
            Shapes.refuseUnknownKeys(where, step, "unload");
            Shapes.requireTrue(where, step, "unload");
            unload();
        } else if (named.equals("readUnloads")) {
            Shapes.refuseUnknownKeys(where, step, "readUnloads");
            Shapes.requireTrue(where, step, "readUnloads");
            unloads.add(memory.getLong(counter, 0));
        } else if (named.equals("releaseHolder")) {
            Shapes.refuseUnknownKeys(where, step, "releaseHolder");
            String holder = Shapes.text(where, step, "releaseHolder");
            lateGetsBeforeTheForget(where, steps, index, holder);
            releaseHolder(holder);
            if (lateGets.containsKey(holder)) {
                throw new IllegalStateException(holder + " heard no revocation its release queued, so its late gets "
                        + "were never made");
            }
        } else if (named.equals("forgetHolder")) {
            Shapes.refuseUnknownKeys(where, step, "forgetHolder");
            String holder = Shapes.text(where, step, "forgetHolder");
            if (world.host.context(world.plugin(holder)).isPresent()) {
                throw new IllegalStateException("the host still holds " + holder + " after its release");
            }
        } else if (named.equals("registerAwaitingReleases")) {
            Shapes.refuseUnknownKeys(where, step, "owner", "registerAwaitingReleases");
            registerAwaitingReleases(Shapes.text(where, step, "owner"),
                    Shapes.text(where, step, "registerAwaitingReleases"));
        } else if (named.equals("quarantine")) {
            Shapes.refuseUnknownKeys(where, step, "quarantine");
            quarantine(Shapes.text(where, step, "quarantine"));
        } else {
            holderStep(where, step, named, Shapes.text(where, step, "holder"));
        }
    }

    private void holderStep(String where, Map<String, Object> step, String named, String holder) {
        if (named.equals("get")) {
            Shapes.refuseUnknownKeys(where, step, "holder", "get");
            get(holder, Shapes.text(where, step, "get"));
        } else if (named.equals("echo")) {
            Shapes.refuseUnknownKeys(where, step, "holder", "echo");
            echoed.add(echo(firstDefinition(holder), Shapes.text(where, step, "echo")));
        } else if (named.equals("call")) {
            Shapes.refuseUnknownKeys(where, step, "holder", "call", "function", "text", "keep");
            Shapes.requireTrue(where, step, "keep");
            call(holder, Shapes.text(where, step, "call"), Shapes.text(where, step, "function"),
                    Shapes.text(where, step, "text"));
        } else if (named.equals("drop")) {
            Shapes.refuseUnknownKeys(where, step, "holder", "drop");
            Shapes.text(where, step, "drop");
        } else if (named.equals("countUnloads")) {
            Shapes.refuseUnknownKeys(where, step, "holder", "countUnloads");
            Shapes.requireTrue(where, step, "countUnloads");
            countUnloads(firstDefinition(holder));
        } else {
            Shapes.refuseUnknownKeys(where, step, "holder", "releaseHolding");
            releaseHolding(holder, Shapes.text(where, step, "releaseHolding"));
        }
    }

    /**
     * Takes the {@code get} steps of {@code holder} that follow its release and come before its forget, to be made as
     * late calls while its release's events are dispatched.
     */
    private void lateGetsBeforeTheForget(String where, List<Map<String, Object>> steps, int release, String holder) {
        List<Integer> late = new ArrayList<>();
        for (int index = release + 1; index < steps.size(); index++) {
            Map<String, Object> step = steps.get(index);
            if (holder.equals(step.get("forgetHolder"))) {
                List<String> ids = new ArrayList<>();
                for (int taken : late) {
                    Shapes.refuseUnknownKeys(where, steps.get(taken), "holder", "get");
                    ids.add(Shapes.text(where, steps.get(taken), "get"));
                }
                consumed.addAll(late);
                if (!ids.isEmpty()) {
                    lateGets.put(holder, ids);
                }
                return;
            }
            if (!holder.equals(step.get("holder")) || !step.containsKey("get")) {
                return;
            }
            late.add(index);
        }
    }

    private void registerAwaitingReleases(String owner, String id) {
        Object service = new Object();
        Answer<?> registered = world.context(owner).registerAwaitingReleases(id, service);
        if (!(registered instanceof Answer.Ok)) {
            throw new IllegalStateException(owner + "'s registration of " + id + " answered " + registered);
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

    private Object firstDefinition(String holder) {
        for (Object object : resolvedBy(holder).values()) {
            if (!(object instanceof LibraryService)) {
                return object;
            }
        }
        throw new IllegalStateException(holder + " resolved no definition before a step that calls through one");
    }

    private long operation(long definition, int index) {
        return memory.getLong(definition, index * ForeignMemory.addressSize());
    }

    private String echo(Object definition, String text) {
        long address = ForeignMemory.addressOf(definition);
        long context = operation(address, CONTEXT);
        byte[] input = text.getBytes(StandardCharsets.UTF_8);
        long inputCopy = memory.bytes(input);
        long output = memory.allocate(Long.BYTES);
        long outputLength = memory.allocate(Long.BYTES);
        long status = ForeignFunction.at(operation(address, ECHO), NativeType.INT, NativeType.ADDRESS,
                NativeType.ADDRESS, NativeType.LONG, NativeType.ADDRESS, NativeType.ADDRESS)
                .call(context, inputCopy, input.length, output, outputLength);
        long body = memory.getLong(output, 0);
        byte[] answered = body == 0 ? new byte[0] : memory.readBytes(body, 0, (int) memory.getLong(outputLength, 0));
        ForeignFunction.at(operation(address, RELEASE), NativeType.VOID, NativeType.ADDRESS, NativeType.ADDRESS)
                .call(context, body);
        if (status != 0) {
            throw new IllegalStateException("the definition's echo answered status " + status);
        }
        return new String(answered, StandardCharsets.UTF_8);
    }

    private void countUnloads(Object definition) {
        long address = ForeignMemory.addressOf(definition);
        ForeignFunction.at(operation(address, COUNT_UNLOADS), NativeType.VOID, NativeType.ADDRESS, NativeType.ADDRESS)
                .call(operation(address, CONTEXT), counter);
    }

    private void call(String holder, String id, String function, String text) {
        Object service = resolvedBy(holder).get(id);
        if (!(service instanceof LibraryService)) {
            throw new IllegalStateException(holder + " resolved no bytes service " + id);
        }
        LibraryAnswer answer = ((LibraryService) service).call(function, text.getBytes(StandardCharsets.UTF_8));
        if (!(answer instanceof LibraryAnswer.Returned)) {
            throw new IllegalStateException(holder + "'s call of " + id + " " + function + " answered " + answer);
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

    private void releaseHolding(String holder, String id) {
        Object object = resolvedBy(holder).get(id);
        if (object == null) {
            throw new IllegalStateException(holder + " resolved no " + id + " to let go of");
        }
        PluginEvent.Revoked revocation = null;
        for (PluginEvent.Revoked heard : revocationsOf(holder)) {
            if (heard.object() == object && heard.awaitsRelease()) {
                revocation = heard;
            }
        }
        if (revocation == null) {
            throw new IllegalStateException(holder + " heard no revocation of " + id + " awaiting its release");
        }
        Answer<Boolean> released = world.context(holder).releaseHolding(revocation);
        if (!(released instanceof Answer.Ok) || !((Answer.Ok<Boolean>) released).value()) {
            throw new IllegalStateException(holder + "'s release of its holding of " + id + " answered " + released);
        }
    }

    private void releaseHolder(String holder) {
        Answer<Boolean> released = waited(world.host.release(world.plugin(holder)).toCompletableFuture());
        if (!(released instanceof Answer.Ok) || !((Answer.Ok<Boolean>) released).value()) {
            throw new IllegalStateException("the host's release of " + holder + " answered " + released);
        }
    }

    /** Quarantines a holder the way the host does it unasked: its activate fails, and the host quarantines it. */
    private void quarantine(String holder) {
        CompletableFuture<Void> activate = activates.get(holder);
        if (activate == null) {
            throw new IllegalArgumentException("the vector quarantines a plugin it does not hold: " + holder);
        }
        activate.completeExceptionally(new IllegalStateException("the suite quarantines " + holder));
    }

    private static <T> T waited(CompletableFuture<T> future) {
        try {
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while the host released a holder", interrupted);
        } catch (ExecutionException | TimeoutException unsettled) {
            throw new IllegalStateException("the host did not release a holder in " + WAIT_SECONDS + " seconds",
                    unsettled);
        }
    }

    private boolean isSameObject() {
        for (Map<String, Object> held : resolved.values()) {
            for (Map.Entry<String, Object> object : held.entrySet()) {
                Long answered = answeredAddresses.get(object.getKey());
                if (answered != null && ForeignMemory.addressOf(object.getValue()) != answered) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<Object> sortedRevocations() {
        List<List<String>> sorted = new ArrayList<>(revoked);
        sorted.sort((left, right) -> {
            int byHolder = left.get(0).compareTo(right.get(0));
            return byHolder != 0 ? byHolder : left.get(1).compareTo(right.get(1));
        });
        return new ArrayList<Object>(sorted);
    }

    private Map<String, Observations.Observer> observers() {
        Map<String, Observations.Observer> observers = new HashMap<>();
        observers.put("loaded", expectation -> load instanceof Answer.Ok);
        observers.put("callersMistake", expectation -> load instanceof Answer.Mistake);
        observers.put("got", expectation -> new ArrayList<>(got));
        observers.put("sameObject", expectation -> isSameObject());
        observers.put("echoed", expectation -> new ArrayList<>(echoed));
        observers.put("revoked", expectation -> sortedRevocations());
        observers.put("unloads", expectation -> new ArrayList<>(unloads));
        observers.put("holdingsReleased", expectation -> new ArrayList<>(holdingsReleased));
        observers.put("reported", expectation -> reported());
        return observers;
    }

    /** What the error sink and the log received from the first step on, the sink's first. */
    private List<Object> reported() {
        List<Object> written = new ArrayList<>();
        List<?> failures = world.failures();
        List<?> logs = world.logs();
        for (Object record : failures.subList(failuresBeforeTheSteps, failures.size())) {
            written.add(String.valueOf(record));
        }
        for (Object record : logs.subList(logsBeforeTheSteps, logs.size())) {
            written.add(String.valueOf(record));
        }
        return written;
    }
}
