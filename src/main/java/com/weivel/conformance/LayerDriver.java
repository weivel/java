package com.weivel.conformance;

import com.weivel.core.Answer;
import com.weivel.core.CoreBinary;
import com.weivel.core.CoreOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The driver for the layer suite: it reads the kit's layer vectors through the standard's JSON reader, writing no
 * file, builds each vector's plugins as manifest values and entries, registers and drives them through the host's
 * public surface on the real core, and answers what the run shows in the vector's own shape. For a {@code want}
 * vector the suite's own clock and scheduler stand in for the language's.
 *
 * <p>One driver runs one vector at a time.
 */
public final class LayerDriver implements AutoCloseable {
    private final CoreBinary core;
    private final StandardJson json;

    private LayerDriver(CoreBinary core, StandardJson json) {
        this.core = core;
        this.json = json;
    }

    /**
     * Binds the standard's JSON reader, beside a core its caller loaded.
     *
     * @param core the core's binary, which its caller keeps open until the driver is closed
     * @param standardBinary the standard's binary, a path the caller chose
     * @return the driver; {@link Answer.Mistake} for a path that names no library, or one lacking the reader
     */
    public static Answer<LayerDriver> load(CoreBinary core, Path standardBinary) {
        Objects.requireNonNull(core, "core");
        Answer<StandardJson> json = StandardJson.load(standardBinary);
        if (!(json instanceof Answer.Ok)) {
            return Answer.retyped(json);
        }
        return new Answer.Ok<>(new LayerDriver(core, ((Answer.Ok<StandardJson>) json).value()));
    }

    /**
     * Reads every layer vector under {@code directory}, at any depth.
     *
     * @param directory the kit's layer vector directory
     * @return the vectors, by id
     * @throws IllegalArgumentException for a vector the standard's reader refuses, or whose id is not its path
     *     without {@code .json}
     * @throws java.io.UncheckedIOException when a vector cannot be read
     */
    public List<LayerVector> read(Path directory) {
        return LayerVectors.read(json, Objects.requireNonNull(directory, "directory"));
    }

    /**
     * Builds the vector's {@code given} through a host on the real core, drives it, and answers every field of its
     * {@code then} from the run, before the host is destroyed. What the destroy that ends the run makes anything
     * hear or write is not read.
     *
     * @param vector the vector
     * @return what the run showed
     * @throws IllegalArgumentException for an op, a part of {@code given}, a step or a {@code then} field this driver
     *     does not drive, so nothing passes unread
     * @throws IllegalStateException when the layer answers the driver what no well-made step can have answered
     */
    public LayerWitness witness(LayerVector vector) {
        Objects.requireNonNull(vector, "vector");
        CoreOptions options = vector.op().equals("want") ? WantRun.coreOptions(vector) : CoreOptions.defaults();
        try (DriverWorld world = new DriverWorld(core, options)) {
            Map<String, Observations.Observer> observers = drive(vector, world);
            return new LayerWitness(vector.id(), Observations.observe(vector.id(), vector.then(), observers),
                    world.logs(), world.failures());
        } catch (RuntimeException failed) {
            throw failed;
        } catch (Exception unclosed) {
            throw new IllegalStateException("the driver's host did not finish its destroy", unclosed);
        }
    }

    private static Map<String, Observations.Observer> drive(LayerVector vector, DriverWorld world) {
        if (vector.op().equals("consume")) {
            return ConsumeRun.drive(vector, world);
        }
        if (vector.op().equals("want")) {
            return WantRun.drive(vector, world);
        }
        if (vector.op().equals("lifecycle")) {
            return LifecycleRun.drive(vector, world);
        }
        throw new IllegalArgumentException("the vector " + vector.id() + " has the op " + vector.op()
                + ", which this driver does not drive");
    }

    /**
     * Closes the standard's binary; the core's stays its caller's.
     *
     * @throws IllegalStateException when it was closed already
     */
    @Override
    public void close() {
        json.close();
    }
}
