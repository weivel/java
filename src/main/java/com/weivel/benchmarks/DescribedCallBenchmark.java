package com.weivel.benchmarks;

import com.weivel.context.PluginContext;
import com.weivel.core.BoundDefinition;
import com.weivel.core.BoundOperation;
import com.weivel.core.CoreBinary;
import com.weivel.core.CoreConstants;
import com.weivel.core.DescribedAnswer;
import com.weivel.core.EdgeSource;
import com.weivel.core.ForeignFunction;
import com.weivel.core.ForeignLibrary;
import com.weivel.core.ForeignMemory;
import com.weivel.core.NativeType;
import com.weivel.host.ActivationState;
import com.weivel.host.Host;
import com.weivel.host.HostOptions;
import com.weivel.host.LibraryAnswer;
import com.weivel.host.LibraryDeclaration;
import com.weivel.host.LibraryService;
import com.weivel.host.Outcome;
import com.weivel.standard.Standard;
import com.weivel.standard.StandardBinary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The described call: the standard's described mirror's {@code reflect}, a sample holding a text and a counted array
 * of three texts in and its reflection out, called on the bound object a consumer resolved, as a plugin calls it,
 * over the same operation called through a binding of the fixture's structs written by hand on the same backend,
 * with the same input, every member of its answer copied out, and its answer and refusal released the same way. The
 * echo library's bytes call through the host is sampled in the same rounds, for its time beside theirs.
 */
public final class DescribedCallBenchmark {
    /** The library call's sampling, so the two ratio gates are measured alike. */
    public static final Sampling DEFAULT_SAMPLING = LibraryCallBenchmark.DEFAULT_SAMPLING;

    private static final String ECHO = "echo";
    private static final String ECHO_SYMBOL = "echo_echo";
    private static final String ECHO_RELEASE = "echo_release";
    private static final String MIRROR = "described-mirror";
    private static final String MIRROR_SYMBOL = "described_mirror_definition";
    private static final String REFLECT = "reflect";
    private static final String LABEL = "request";
    private static final List<String> TAGS = Collections.unmodifiableList(Arrays.asList("accept", "host",
            "content-length"));
    private static final int ECHO_LENGTH = 16;
    private static final long ACTIVATION_SECONDS = 10;

    /** The last answer of either path, so no copy of a member can be dropped as unread. */
    private static Object kept;

    private DescribedCallBenchmark() {
    }

    /**
     * Times the call through the bound object against the call through the binding written by hand.
     *
     * @param binary the core's loaded binary
     * @param standardLibrary a build of the standard, whose description check the host runs
     * @param describedLibrary a build of the standard's {@code described} test library
     * @param echoLibrary a build of the core's {@code echo} test library
     * @param sampling how the paths are sampled
     * @return the measurement
     * @throws IllegalStateException when either path answers anything but the sample it was handed
     */
    public static DescribedCallMeasurement measure(CoreBinary binary, Path standardLibrary, Path describedLibrary,
                                                   Path echoLibrary, Sampling sampling) {
        return onThePaths(binary, standardLibrary, describedLibrary, echoLibrary,
                (paths, mirror) -> DescribedCallMeasurement.of(Interleaved.sample(paths, sampling)));
    }

    /**
     * What each path made the described library release over {@code calls} calls of it, counted by the library
     * itself: reflections, then refusals, keyed by path.
     */
    static Map<String, long[]> releasesOver(CoreBinary binary, Path standardLibrary, Path describedLibrary,
                                            Path echoLibrary, int calls) {
        return onThePaths(binary, standardLibrary, describedLibrary, echoLibrary, (paths, mirror) -> {
            Map<String, long[]> released = new LinkedHashMap<>();
            for (Map.Entry<String, MeasuredPath> path : paths.entrySet()) {
                long[] before = releases(mirror);
                for (int call = 0; call < calls; call++) {
                    path.getValue().call(call);
                }
                long[] after = releases(mirror);
                released.put(path.getKey(), new long[] {after[0] - before[0], after[1] - before[1]});
            }
            return released;
        });
    }

    private static long[] releases(BoundDefinition mirror) {
        DescribedAnswer answer = mirror.call("count_releases", Collections.<String, Object>emptyMap());
        Map<?, ?> counted = (Map<?, ?>) ((DescribedAnswer.Answered) answer).value();
        return new long[] {(Long) counted.get("reflections"), (Long) counted.get("refusals")};
    }

    private interface PathsUse<T> {
        T use(LinkedHashMap<String, MeasuredPath> paths, BoundDefinition mirror);
    }

    private static <T> T onThePaths(CoreBinary binary, Path standardLibrary, Path describedLibrary, Path echoLibrary,
                                    PathsUse<T> use) {
        try (StandardBinary standard = CoreWorld.ok(StandardBinary.load(standardLibrary), "the standard's load")) {
            Host host = CoreWorld.ok(Host.create(binary, HostOptions.defaults().withLog(record -> { })
                    .withDescriptionCheck(Standard.of(standard).descriptionCheck())), "the host's creation");
            try {
                PluginContext consumer = activatedConsumer(host, describedLibrary, echoLibrary);
                LibraryService echo = (LibraryService) CoreWorld.ok(consumer.get(ECHO), "the consumer's get of "
                        + ECHO);
                BoundDefinition mirror = (BoundDefinition) CoreWorld.ok(consumer.get(MIRROR),
                        "the consumer's get of " + MIRROR);
                BoundOperation reflect = mirror.operation(REFLECT);
                try (ForeignLibrary bareLibrary = CoreWorld.ok(ForeignLibrary.open(describedLibrary,
                        Collections.singletonList(MIRROR_SYMBOL)), "the bare open of " + describedLibrary);
                     ForeignMemory memory = ForeignMemory.open()) {
                    HandReflect hand = new HandReflect(memory, bareLibrary.function(MIRROR_SYMBOL,
                            NativeType.ADDRESS).call());
                    Map<String, Object> input = Collections.<String, Object>singletonMap("sample", sample());
                    refuseUnlessReflected(boundSample(reflect.call(input)), "bound");
                    HandSample handed = hand.reflected();
                    refuseUnlessReflected(Arrays.asList(handed.label, handed.tags), "bare");
                    byte[] echoInput = new byte[ECHO_LENGTH];
                    Arrays.fill(echoInput, (byte) 7);
                    LinkedHashMap<String, MeasuredPath> paths = new LinkedHashMap<>();
                    paths.put(PairMeasurement.BARE, hand::call);
                    paths.put(PairMeasurement.WEIVEL, call -> throughTheBinding(reflect, input));
                    paths.put(DescribedCallMeasurement.ECHO, call -> throughTheHost(echo, echoInput));
                    return use.use(paths, mirror);
                }
            } finally {
                destroyed(host);
            }
        }
    }

    private static Map<String, Object> sample() {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("label", LABEL);
        sample.put("tags", TAGS);
        return Collections.unmodifiableMap(sample);
    }

    private static PluginContext activatedConsumer(Host host, Path describedLibrary, Path echoLibrary) {
        CoreWorld.ok(host.loadLibrary(echoLibrary, echoDeclaration()), "the echo library's load");
        CoreWorld.ok(host.loadLibrary(describedLibrary, describedDeclaration()), "the described library's load");
        CompletableFuture<PluginContext> activated = new CompletableFuture<>();
        long consumer = CoreWorld.ok(host.register(CoreWorld.manifest("consumer", Collections.<String>emptyList(),
                Arrays.asList(ECHO, MIRROR)), context -> {
                    activated.complete(context);
                    return CompletableFuture.completedFuture(null);
                }), "the consumer's registration");
        List<Outcome> outcomes = waited(CoreWorld.ok(host.activate(Collections.singletonList(consumer),
                EdgeSource.SERVICES), "the consumer's activation").settled().toCompletableFuture());
        if (outcomes.get(0).state() != ActivationState.ACTIVE) {
            throw new IllegalStateException("the consumer was not activated: " + outcomes.get(0));
        }
        return waited(activated);
    }

    private static LibraryDeclaration echoDeclaration() {
        return new LibraryDeclaration(ECHO, "0.1.0", Optional.of(ECHO_RELEASE), Collections.singletonList(
                new LibraryDeclaration.Service(ECHO, Collections.singletonList(
                        LibraryDeclaration.Function.bytes(ECHO, ECHO_SYMBOL)))));
    }

    private static LibraryDeclaration describedDeclaration() {
        return new LibraryDeclaration("described", "1.0.0", Optional.<String>empty(), Collections.singletonList(
                new LibraryDeclaration.Service(MIRROR, Collections.singletonList(
                        LibraryDeclaration.Function.definition("definition", MIRROR_SYMBOL)),
                        Optional.of("described_mirror_description"))));
    }

    private static long throughTheBinding(BoundOperation reflect, Map<String, Object> input) {
        DescribedAnswer answer = reflect.call(input);
        if (!(answer instanceof DescribedAnswer.Answered)) {
            throw new IllegalStateException("reflect through the bound object answered " + answer);
        }
        kept = answer;
        return 1;
    }

    private static long throughTheHost(LibraryService echo, byte[] input) {
        LibraryAnswer answer = echo.call(ECHO, input);
        if (!(answer instanceof LibraryAnswer.Returned)
                || ((LibraryAnswer.Returned) answer).bytes().length != ECHO_LENGTH) {
            throw new IllegalStateException("echo through the host answered " + answer);
        }
        return ECHO_LENGTH;
    }

    private static List<Object> boundSample(DescribedAnswer answer) {
        if (!(answer instanceof DescribedAnswer.Answered)) {
            return null;
        }
        Object reflection = ((DescribedAnswer.Answered) answer).value();
        Object sample = reflection instanceof Map ? ((Map<?, ?>) reflection).get("sample") : null;
        if (!(sample instanceof Map)) {
            return null;
        }
        return Arrays.asList(((Map<?, ?>) sample).get("label"), ((Map<?, ?>) sample).get("tags"));
    }

    private static void refuseUnlessReflected(List<Object> labelAndTags, String path) {
        if (!Arrays.<Object>asList(LABEL, TAGS).equals(labelAndTags)) {
            throw new IllegalStateException("the " + path + " reflect did not answer the sample it was handed");
        }
    }

    private static void destroyed(Host host) {
        waited(host.destroy().toCompletableFuture());
        host.close();
    }

    private static <T> T waited(CompletableFuture<T> future) {
        try {
            return future.get(ACTIVATION_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while the host settled", interrupted);
        } catch (ExecutionException | TimeoutException unsettled) {
            throw new IllegalStateException("the host did not settle in " + ACTIVATION_SECONDS + " seconds",
                    unsettled);
        }
    }

    /** The fixture's {@code described_point}, declared by hand. */
    private static final class HandPoint {
        private final String label;
        private final long x;
        private final double y;

        HandPoint(String label, long x, double y) {
            this.label = label;
            this.x = x;
            this.y = y;
        }
    }

    /** The fixture's {@code described_sample} as {@code reflect} answers it, every member the binding reads. */
    private static final class HandSample {
        private long shade;
        private long tier;
        private long flags;
        private long big;
        private long delta;
        private double ratio;
        private String label;
        private List<String> tags;
        private byte[] bytes;
        private HandPoint origin;
        private HandPoint best;
        private List<HandPoint> points;
        private List<Long> numbers;
        private int heldKind;
        private long extra;
        private long values;
        private long valueCount;
    }

    /**
     * {@code reflect} called through the mirror's own function pointers, its sample written by hand into blocks laid
     * out once, every member of its answer copied out, its reflection released, and its refusal released only once
     * filled, as the bound object releases both.
     *
     * @implNote The fixture's types are declared by hand here alone, since the control is a binding written by hand,
     *     and only for a 64-bit platform, the only kind the kit's runners are. The sample, reflection and refusal are
     *     one block each, kept across calls as a binding written for one operation keeps them; the releases empty them.
     *     The benchmark hands no value, so a value member answered holding one is refused rather than read.
     */
    private static final class HandReflect {
        private static final int WORD = 8;
        private static final int MIRROR_CONTEXT = 1;
        private static final int MIRROR_REFLECT = 2;
        private static final int MIRROR_RELEASE_REFLECTION = 13;
        private static final int MIRROR_RELEASE_REFUSAL = 15;
        private static final int SAMPLE_SIZE = 224;
        private static final int SAMPLE_SHADE = 4;
        private static final int SAMPLE_TIER = 8;
        private static final int SAMPLE_FLAGS = 12;
        private static final int SAMPLE_BIG = 16;
        private static final int SAMPLE_DELTA = 24;
        private static final int SAMPLE_RATIO = 32;
        private static final int SAMPLE_LABEL = 40;
        private static final int SAMPLE_TAGS = 48;
        private static final int SAMPLE_TAG_COUNT = 56;
        private static final int SAMPLE_BYTES = 64;
        private static final int SAMPLE_LENGTH = 72;
        private static final int SAMPLE_ORIGIN = 80;
        private static final int SAMPLE_BEST = 104;
        private static final int SAMPLE_POINTS = 112;
        private static final int SAMPLE_POINT_COUNT = 120;
        private static final int SAMPLE_NUMBERS = 128;
        private static final int SAMPLE_NUMBER_COUNT = 136;
        private static final int SAMPLE_HELD = 144;
        private static final int SAMPLE_EXTRA = 200;
        private static final int SAMPLE_VALUES = 208;
        private static final int SAMPLE_VALUE_COUNT = 216;
        private static final int POINT_SIZE = 24;
        private static final int POINT_X = 8;
        private static final int POINT_Y = 16;
        private static final int REFLECTION_SIZE = 240;
        private static final int REFLECTION_SAMPLE = 8;
        private static final int REFUSAL_SIZE = 32;
        private static final int VALUE_NONE = 0;

        private final ForeignMemory memory;
        private final long context;
        private final ForeignFunction reflect;
        private final ForeignFunction releaseReflection;
        private final ForeignFunction releaseRefusal;
        private final long sample;
        private final long label;
        private final long tags;
        private final long[] tagTexts;
        private final long reflection;
        private final long refusal;

        HandReflect(ForeignMemory memory, long mirror) {
            if (ForeignMemory.addressSize() != WORD) {
                throw new IllegalStateException("the hand binding of the described fixture is declared for 64-bit "
                        + "platforms only");
            }
            this.memory = memory;
            this.context = memory.getLong(mirror, MIRROR_CONTEXT * WORD);
            this.reflect = ForeignFunction.at(memory.getLong(mirror, MIRROR_REFLECT * WORD), NativeType.INT,
                    NativeType.ADDRESS, NativeType.ADDRESS, NativeType.ADDRESS, NativeType.ADDRESS);
            this.releaseReflection = ForeignFunction.at(memory.getLong(mirror, MIRROR_RELEASE_REFLECTION * WORD),
                    NativeType.VOID, NativeType.ADDRESS, NativeType.ADDRESS);
            this.releaseRefusal = ForeignFunction.at(memory.getLong(mirror, MIRROR_RELEASE_REFUSAL * WORD),
                    NativeType.VOID, NativeType.ADDRESS, NativeType.ADDRESS);
            this.sample = memory.allocate(SAMPLE_SIZE);
            memory.putInt(sample, 0, SAMPLE_SIZE);
            this.label = memory.allocate(utf8(LABEL).length + 1L);
            this.tags = memory.allocate((long) TAGS.size() * WORD);
            this.tagTexts = new long[TAGS.size()];
            for (int index = 0; index < tagTexts.length; index++) {
                tagTexts[index] = memory.allocate(utf8(TAGS.get(index)).length + 1L);
            }
            this.reflection = memory.allocate(REFLECTION_SIZE);
            memory.putInt(reflection, 0, REFLECTION_SIZE);
            this.refusal = memory.allocate(REFUSAL_SIZE);
            memory.putInt(refusal, 0, REFUSAL_SIZE);
        }

        long call(int index) {
            HandSample answered = reflected();
            kept = answered;
            return answered.tags.size();
        }

        HandSample reflected() {
            written();
            int status = (int) reflect.call(context, sample, reflection, refusal);
            try {
                if (status != CoreConstants.STATUS_OK) {
                    throw new IllegalStateException("the bare reflect answered status " + status);
                }
                if (memory.getInt(reflection, REFLECTION_SAMPLE) != SAMPLE_SIZE) {
                    throw new IllegalStateException("the bare reflect answered a sample of another size than the "
                            + SAMPLE_SIZE + " bytes declared by hand");
                }
                return read(reflection + REFLECTION_SAMPLE);
            } finally {
                releaseReflection.call(context, reflection);
                if (status == CoreConstants.STATUS_REFUSED) {
                    releaseRefusal.call(context, refusal);
                }
            }
        }

        private HandSample read(long answered) {
            HandSample read = new HandSample();
            read.shade = unsigned(memory.getInt(answered, SAMPLE_SHADE));
            read.tier = unsigned(memory.getInt(answered, SAMPLE_TIER));
            read.flags = unsigned(memory.getInt(answered, SAMPLE_FLAGS));
            read.big = memory.getLong(answered, SAMPLE_BIG);
            read.delta = memory.getLong(answered, SAMPLE_DELTA);
            read.ratio = Double.longBitsToDouble(memory.getLong(answered, SAMPLE_RATIO));
            read.label = text(memory.getLong(answered, SAMPLE_LABEL));
            read.tags = texts(memory.getLong(answered, SAMPLE_TAGS), memory.getLong(answered, SAMPLE_TAG_COUNT));
            long bytes = memory.getLong(answered, SAMPLE_BYTES);
            long length = memory.getLong(answered, SAMPLE_LENGTH);
            read.bytes = bytes == 0 || length == 0 ? new byte[0] : memory.readBytes(bytes, 0, (int) length);
            read.origin = point(answered + SAMPLE_ORIGIN);
            long best = memory.getLong(answered, SAMPLE_BEST);
            read.best = best == 0 ? null : point(best);
            read.points = points(memory.getLong(answered, SAMPLE_POINTS),
                    memory.getLong(answered, SAMPLE_POINT_COUNT));
            read.numbers = numbers(memory.getLong(answered, SAMPLE_NUMBERS),
                    memory.getLong(answered, SAMPLE_NUMBER_COUNT));
            read.heldKind = memory.getInt(answered, SAMPLE_HELD);
            read.extra = memory.getLong(answered, SAMPLE_EXTRA);
            read.values = memory.getLong(answered, SAMPLE_VALUES);
            read.valueCount = memory.getLong(answered, SAMPLE_VALUE_COUNT);
            if (read.heldKind != VALUE_NONE || read.extra != 0 || read.values != 0 || read.valueCount != 0) {
                throw new IllegalStateException("the bare reflect answered a value, which this benchmark never hands");
            }
            return read;
        }

        private HandPoint point(long at) {
            return new HandPoint(text(memory.getLong(at, 0)), memory.getLong(at, POINT_X),
                    Double.longBitsToDouble(memory.getLong(at, POINT_Y)));
        }

        private List<String> texts(long items, long count) {
            if (items == 0 || count == 0) {
                return Collections.emptyList();
            }
            List<String> read = new ArrayList<>((int) count);
            for (int index = 0; index < count; index++) {
                read.add(text(memory.getLong(items, (long) index * WORD)));
            }
            return read;
        }

        private List<HandPoint> points(long items, long count) {
            if (items == 0 || count == 0) {
                return Collections.emptyList();
            }
            List<HandPoint> read = new ArrayList<>((int) count);
            for (int index = 0; index < count; index++) {
                read.add(point(items + (long) index * POINT_SIZE));
            }
            return read;
        }

        private List<Long> numbers(long items, long count) {
            if (items == 0 || count == 0) {
                return Collections.emptyList();
            }
            List<Long> read = new ArrayList<>((int) count);
            for (int index = 0; index < count; index++) {
                read.add(unsigned(memory.getInt(items, (long) index * Integer.BYTES)));
            }
            return read;
        }

        private String text(long address) {
            return address == 0 ? null : memory.readText(address);
        }

        private static long unsigned(int word) {
            return word & 0xFFFF_FFFFL;
        }

        private void written() {
            memory.write(label, 0, utf8(LABEL));
            for (int index = 0; index < tagTexts.length; index++) {
                memory.write(tagTexts[index], 0, utf8(TAGS.get(index)));
                memory.putLong(tags, (long) index * WORD, tagTexts[index]);
            }
            memory.putLong(sample, SAMPLE_LABEL, label);
            memory.putLong(sample, SAMPLE_TAGS, tags);
            memory.putLong(sample, SAMPLE_TAG_COUNT, TAGS.size());
        }

        private static byte[] utf8(String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }
}
