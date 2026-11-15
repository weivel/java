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
import com.weivel.host.LibraryAnswer;
import com.weivel.host.LibraryDeclaration;
import com.weivel.host.LibraryService;
import com.weivel.host.Outcome;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The library call: the core's echo test library's {@code echo_echo} through the host's library service, 16 bytes of
 * a heap array in and the same 16 back, over the same function called bare with the same bytes, copied in and its
 * answer copied out and released as the host does.
 */
public final class LibraryCallBenchmark {
    /**
     * Enough batches that the median of the per-round ratios settles.
     *
     * @implNote The warm-up is ten times the 100 000 calls that left a JVM's first run slower, as
     * {@link ControlCallBenchmark#DEFAULT_SAMPLING} records.
     */
    public static final Sampling DEFAULT_SAMPLING = new Sampling(1_000_000, 1_000, 201);

    private static final String ECHO = "echo";
    private static final String ECHO_SYMBOL = "echo_echo";
    private static final String RELEASE_SYMBOL = "echo_release";
    private static final int INPUT_LENGTH = 16;
    private static final long ACTIVATION_SECONDS = 10;

    private LibraryCallBenchmark() {
    }

    /**
     * Times the call through the host's service against the bare call.
     *
     * @param binary the core's loaded binary
     * @param echoLibrary a build of the core's {@code echo} test library
     * @param sampling how the paths are sampled
     * @return the measurement
     * @throws IllegalStateException when either path answers anything but its input
     */
    public static PairMeasurement measure(CoreBinary binary, Path echoLibrary, Sampling sampling) {
        Host host = CoreWorld.ok(Host.create(binary, HostOptions.defaults().withLog(record -> { })),
                "the host's creation");
        try {
            LibraryService echo = resolvedEcho(host, echoLibrary);
            try (ForeignLibrary bareLibrary = CoreWorld.ok(ForeignLibrary.open(echoLibrary,
                    Arrays.asList(ECHO_SYMBOL, RELEASE_SYMBOL)), "the bare open of " + echoLibrary);
                 ForeignMemory memory = bareLibrary.memory()) {
                byte[] input = new byte[INPUT_LENGTH];
                Arrays.fill(input, (byte) 7);
                LinkedHashMap<String, MeasuredPath> paths = new LinkedHashMap<>();
                paths.put(PairMeasurement.BARE, new BareEcho(bareLibrary, memory, input)::call);
                paths.put(PairMeasurement.WEIVEL, call -> throughTheHost(echo, input));
                return PairMeasurement.of(Interleaved.sample(paths, sampling));
            }
        } finally {
            destroyed(host);
        }
    }

    private static LibraryService resolvedEcho(Host host, Path echoLibrary) {
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
        return (LibraryService) CoreWorld.ok(waited(activated).get(ECHO), "the consumer's get of " + ECHO);
    }

    private static LibraryDeclaration declaration() {
        return new LibraryDeclaration(ECHO, "0.1.0", Optional.of(RELEASE_SYMBOL), Collections.singletonList(
                new LibraryDeclaration.Service(ECHO, Collections.singletonList(
                        LibraryDeclaration.Function.bytes(ECHO, ECHO_SYMBOL)))));
    }

    private static long throughTheHost(LibraryService echo, byte[] input) {
        LibraryAnswer answer = echo.call(ECHO, input);
        if (!(answer instanceof LibraryAnswer.Returned)
                || ((LibraryAnswer.Returned) answer).bytes().length != INPUT_LENGTH) {
            throw new IllegalStateException("echo through the host answered " + answer);
        }
        return INPUT_LENGTH;
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

    /** {@code echo_echo} called bare: the input copied into memory laid out once, its answer copied, then released. */
    private static final class BareEcho {
        private final ForeignFunction echo;
        private final ForeignFunction release;
        private final ForeignMemory memory;
        private final byte[] input;
        private final long inputCopy;
        private final long output;
        private final long outputLength;

        BareEcho(ForeignLibrary library, ForeignMemory memory, byte[] input) {
            this.echo = library.function(ECHO_SYMBOL, NativeType.INT, NativeType.ADDRESS, NativeType.LONG,
                    NativeType.ADDRESS, NativeType.ADDRESS);
            this.release = library.function(RELEASE_SYMBOL, NativeType.VOID, NativeType.ADDRESS);
            this.memory = memory;
            this.input = input;
            this.inputCopy = memory.allocate(input.length);
            this.output = memory.allocate(Long.BYTES);
            this.outputLength = memory.allocate(Long.BYTES);
        }

        long call(int index) {
            memory.write(inputCopy, 0, input);
            int status = (int) echo.call(inputCopy, input.length, output, outputLength);
            long body = memory.getLong(output, 0);
            int length = (int) memory.getLong(outputLength, 0);
            byte[] copied = body == 0 ? new byte[0] : memory.readBytes(body, 0, length);
            if (body != 0) {
                release.call(body);
            }
            if (status != 0 || copied.length != input.length) {
                throw new IllegalStateException("the bare echo_echo answered status " + status + " with "
                        + copied.length + " bytes");
            }
            return copied.length;
        }
    }
}
