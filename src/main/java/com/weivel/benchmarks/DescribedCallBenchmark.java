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
 * with the same input and its answer and refusal released the same way. The echo library's bytes call through the
 * host is sampled in the same rounds, for its time beside theirs.
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
        try (StandardBinary standard = CoreWorld.ok(StandardBinary.load(standardLibrary), "the standard's load")) {
            Host host = CoreWorld.ok(Host.create(binary, HostOptions.defaults().withLog(record -> { })
                    .withDescriptionCheck(Standard.of(standard).descriptionCheck())), "the host's creation");
            try {
                PluginContext consumer = activatedConsumer(host, describedLibrary, echoLibrary);
                LibraryService echo = (LibraryService) CoreWorld.ok(consumer.get(ECHO), "the consumer's get of "
                        + ECHO);
                BoundOperation reflect = ((BoundDefinition) CoreWorld.ok(consumer.get(MIRROR),
                        "the consumer's get of " + MIRROR)).operation(REFLECT);
                try (ForeignLibrary bareLibrary = CoreWorld.ok(ForeignLibrary.open(describedLibrary,
                        Collections.singletonList(MIRROR_SYMBOL)), "the bare open of " + describedLibrary);
                     ForeignMemory memory = ForeignMemory.open()) {
                    HandReflect hand = new HandReflect(memory, bareLibrary.function(MIRROR_SYMBOL,
                            NativeType.ADDRESS).call());
                    Map<String, Object> input = Collections.<String, Object>singletonMap("sample", sample());
                    refuseUnlessReflected(boundReflected(reflect.call(input)), "bound");
                    refuseUnlessReflected(hand.reflected(), "bare");
                    byte[] echoInput = new byte[ECHO_LENGTH];
                    Arrays.fill(echoInput, (byte) 7);
                    LinkedHashMap<String, MeasuredPath> paths = new LinkedHashMap<>();
                    paths.put(PairMeasurement.BARE, hand::call);
                    paths.put(PairMeasurement.WEIVEL, call -> throughTheBinding(reflect, input));
                    paths.put(DescribedCallMeasurement.ECHO, call -> throughTheHost(echo, echoInput));
                    return DescribedCallMeasurement.of(Interleaved.sample(paths, sampling));
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

    private static Reflected boundReflected(DescribedAnswer answer) {
        if (!(answer instanceof DescribedAnswer.Answered)) {
            return null;
        }
        Object reflection = ((DescribedAnswer.Answered) answer).value();
        Object sample = reflection instanceof Map ? ((Map<?, ?>) reflection).get("sample") : null;
        if (!(sample instanceof Map)) {
            return null;
        }
        Object label = ((Map<?, ?>) sample).get("label");
        Object tags = ((Map<?, ?>) sample).get("tags");
        return new Reflected(label instanceof String ? (String) label : null,
                tags instanceof List ? (List<?>) tags : null);
    }

    private static void refuseUnlessReflected(Reflected reflected, String path) {
        if (reflected == null || !LABEL.equals(reflected.label) || !TAGS.equals(reflected.tags)) {
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

    /** What {@code reflect} answered that both paths read back: the sample's text and counted array. */
    private static final class Reflected {
        private final String label;
        private final List<?> tags;

        Reflected(String label, List<?> tags) {
            this.label = label;
            this.tags = tags;
        }
    }

    /**
     * {@code reflect} called through the mirror's own function pointers, its sample written by hand into blocks laid
     * out once, its label and tags read back, its reflection released, and its refusal released only once filled, as
     * the bound object releases both.
     *
     * @implNote The fixture's types are declared by hand here alone, since the control is a binding written by hand,
     *     and only for a 64-bit platform, the only kind the kit's runners are. The sample, reflection and refusal are
     *     one block each, kept across calls as a binding written for one operation keeps them; the releases empty them.
     */
    private static final class HandReflect {
        private static final int WORD = 8;
        private static final int MIRROR_CONTEXT = 1;
        private static final int MIRROR_REFLECT = 2;
        private static final int MIRROR_RELEASE_REFLECTION = 13;
        private static final int MIRROR_RELEASE_REFUSAL = 15;
        private static final int SAMPLE_SIZE = 224;
        private static final int SAMPLE_LABEL = 40;
        private static final int SAMPLE_TAGS = 48;
        private static final int SAMPLE_TAG_COUNT = 56;
        private static final int REFLECTION_SIZE = 240;
        private static final int REFLECTION_SAMPLE = 8;
        private static final int REFUSAL_SIZE = 32;

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
            Reflected reflected = reflected();
            return reflected.tags.size();
        }

        Reflected reflected() {
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
                long answered = reflection + REFLECTION_SAMPLE;
                long text = memory.getLong(answered, SAMPLE_LABEL);
                long texts = memory.getLong(answered, SAMPLE_TAGS);
                int count = (int) memory.getLong(answered, SAMPLE_TAG_COUNT);
                List<String> read = new ArrayList<>(count);
                for (int tag = 0; tag < count; tag++) {
                    long element = memory.getLong(texts, (long) tag * WORD);
                    read.add(element == 0 ? null : memory.readText(element));
                }
                return new Reflected(text == 0 ? null : memory.readText(text), read);
            } finally {
                releaseReflection.call(context, reflection);
                if (status == CoreConstants.STATUS_REFUSED) {
                    releaseRefusal.call(context, refusal);
                }
            }
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
