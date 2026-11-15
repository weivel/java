package com.weivel.benchmarks;

import com.weivel.context.PluginContext;
import com.weivel.core.CoreBinary;
import com.weivel.core.EdgeSource;
import com.weivel.core.ForeignFunction;
import com.weivel.core.ForeignLibrary;
import com.weivel.core.ForeignMemory;
import com.weivel.core.NativeType;
import com.weivel.host.ActivationState;
import com.weivel.host.Host;
import com.weivel.host.HostOptions;
import com.weivel.host.LibraryDeclaration;
import com.weivel.host.Outcome;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * The definition row of the library call: the echo library's definition's {@code echo} called through the definition
 * a consumer resolved, over the same operation called through the pointer the definition's symbol answered, and that
 * direct call twice for the run's noise. The two are the same pointer, so the timing can show only noise.
 *
 * @implNote Every path reads the operation's function pointer and context out of the struct once, before the timed
 * loop, and is an instance of one class, so the paths differ only in which address they started from. A function
 * pointer read is bound once and shared by every path that read it: a downcall bound per path put three targets
 * behind one call site, which on the foreign function API cost seven times the call itself.
 */
public final class DefinitionCallBenchmark {
    /**
     * Batches of some milliseconds on each backend, long enough that two identical direct paths settle below the
     * platform's worst noise.
     *
     * @implNote A batch of 20 000 calls lasts about 0.6 ms on the foreign function API on Linux, where the call takes
     * 29 ns, and left the two direct paths 0.0072 apart, twice Linux's worst noise; on JNA, where it takes about
     * 1.6 us, the same batch left them 0.00004 apart, and a foreign function batch as long would make JNA's run
     * last minutes.
     */
    private static final Sampling FFM_SAMPLING = new Sampling(1_000_000, 500_000, 101);
    private static final Sampling JNA_SAMPLING = new Sampling(200_000, 20_000, 101);

    private static final String ECHO = "echo";
    private static final String SYMBOL = "echo_service_definition";
    private static final int INPUT_LENGTH = 16;
    private static final long ACTIVATION_SECONDS = 10;
    private static final int CONTEXT = 1;
    private static final int OPERATION = 2;
    private static final int RELEASE = 3;

    private DefinitionCallBenchmark() {
    }

    /**
     * The sampling this row is timed with on a backend.
     *
     * @param backend the backend the core was loaded on, {@code ffm} or {@code jna}
     * @return its sampling
     */
    public static Sampling samplingFor(String backend) {
        return backend.equals("ffm") ? FFM_SAMPLING : JNA_SAMPLING;
    }

    /**
     * Times the call through the resolved definition against the call through the symbol's own pointer.
     *
     * @param binary the core's loaded binary
     * @param echoLibrary a build of the core's {@code echo} test library
     * @param sampling how the paths are sampled
     * @return the measurement
     * @throws IllegalStateException when the consumer is not answered the symbol's own address, or a path's echo
     *     answers anything but its input's length
     */
    public static SameLanguageMeasurement measure(CoreBinary binary, Path echoLibrary, Sampling sampling) {
        Host host = CoreWorld.ok(Host.create(binary, HostOptions.defaults().withLog(record -> { })),
                "the host's creation");
        try (ForeignLibrary bare = CoreWorld.ok(ForeignLibrary.open(echoLibrary, Collections.singletonList(SYMBOL)),
                "the bare open of " + echoLibrary); ForeignMemory memory = ForeignMemory.open()) {
            long answered = bare.function(SYMBOL, NativeType.ADDRESS).call();
            long resolved = ForeignMemory.addressOf(resolvedDefinition(host, echoLibrary));
            if (resolved != answered) {
                throw new IllegalStateException("the consumer was not answered the definition's own address");
            }
            Map<Long, ForeignFunction> bound = new HashMap<>();
            LinkedHashMap<String, Supplier<MeasuredPath>> paths = new LinkedHashMap<>();
            paths.put(SameLanguageMeasurement.DIRECT, () -> new DefinitionEcho(memory, bound, answered));
            paths.put(SameLanguageMeasurement.DIRECT_AGAIN, () -> new DefinitionEcho(memory, bound, answered));
            paths.put(SameLanguageMeasurement.THROUGH_WEIVEL, () -> new DefinitionEcho(memory, bound, resolved));
            return SameLanguageMeasurement.of(Interleaved.sampleFresh(paths, sampling));
        } finally {
            destroyed(host);
        }
    }

    private static Object resolvedDefinition(Host host, Path echoLibrary) {
        CoreWorld.ok(host.loadLibrary(echoLibrary, declaration()), "the echo library's load");
        CompletableFuture<PluginContext> activated = new CompletableFuture<>();
        long consumer = CoreWorld.ok(host.register(CoreWorld.manifest("consumer", Collections.<String>emptyList(),
                Collections.singletonList(ECHO)), context -> {
                    activated.complete(context);
                    return CompletableFuture.completedFuture(null);
                }), "the consumer's registration");
        List<Outcome> outcomes = waited(CoreWorld.ok(host.activate(Collections.singletonList(consumer),
                EdgeSource.SERVICES), "the consumer's activation").settled().toCompletableFuture());
        if (outcomes.get(0).state() != ActivationState.ACTIVE) {
            throw new IllegalStateException("the consumer was not activated: " + outcomes.get(0));
        }
        return CoreWorld.ok(waited(activated).get(ECHO), "the consumer's get of " + ECHO);
    }

    private static LibraryDeclaration declaration() {
        return new LibraryDeclaration(ECHO, "0.2.0", Optional.<String>empty(), Collections.singletonList(
                new LibraryDeclaration.Service(ECHO, Collections.singletonList(
                        LibraryDeclaration.Function.definition("definition", SYMBOL)))));
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

    /** The definition's {@code echo} called through the struct at one address, its answer released as it says. */
    private static final class DefinitionEcho implements MeasuredPath {
        private final ForeignMemory memory;
        private final ForeignFunction echo;
        private final ForeignFunction release;
        private final long context;
        private final long input;
        private final long output;
        private final long outputLength;

        DefinitionEcho(ForeignMemory memory, Map<Long, ForeignFunction> bound, long definition) {
            this.memory = memory;
            this.context = memory.getLong(definition, CONTEXT * (long) Long.BYTES);
            this.echo = bound.computeIfAbsent(memory.getLong(definition, OPERATION * (long) Long.BYTES),
                    pointer -> ForeignFunction.at(pointer, NativeType.INT, NativeType.ADDRESS, NativeType.ADDRESS,
                            NativeType.LONG, NativeType.ADDRESS, NativeType.ADDRESS));
            this.release = bound.computeIfAbsent(memory.getLong(definition, RELEASE * (long) Long.BYTES),
                    pointer -> ForeignFunction.at(pointer, NativeType.VOID, NativeType.ADDRESS, NativeType.ADDRESS));
            byte[] bytes = new byte[INPUT_LENGTH];
            Arrays.fill(bytes, (byte) 7);
            this.input = memory.bytes(bytes);
            this.output = memory.allocate(Long.BYTES);
            this.outputLength = memory.allocate(Long.BYTES);
        }

        @Override
        public long call(int index) {
            int status = (int) echo.call(context, input, INPUT_LENGTH, output, outputLength);
            long length = memory.getLong(outputLength, 0);
            release.call(context, memory.getLong(output, 0));
            if (status != 0 || length != INPUT_LENGTH) {
                throw new IllegalStateException("the definition's echo answered status " + status + " with "
                        + length + " bytes");
            }
            return length;
        }
    }
}
