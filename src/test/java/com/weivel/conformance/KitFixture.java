package com.weivel.conformance;

import com.weivel.core.Answer;
import com.weivel.core.CoreBinary;
import com.weivel.core.CoreLoad;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** The pinned kit's layer vectors and a driver over the real core and standard, each named by its Gradle property. */
final class KitFixture {
    private static final CoreBinary CORE = loadedCore();
    private static final LayerDriver DRIVER = loadedDriver();

    private KitFixture() {
    }

    static Path layerVectorDirectory() {
        Path directory = Paths.get(property("weivel.test.layerVectors"));
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("weivel.test.layerVectors names " + directory + ", which is no directory");
        }
        return directory;
    }

    static List<LayerVector> vectors() {
        return DRIVER.read(layerVectorDirectory());
    }

    static LayerVector vector(String id) {
        for (LayerVector vector : vectors()) {
            if (vector.id().equals(id)) {
                return vector;
            }
        }
        throw new IllegalStateException("the pinned kit carries no layer vector " + id);
    }

    static LayerWitness witness(LayerVector vector) {
        return DRIVER.witness(vector);
    }

    private static CoreBinary loadedCore() {
        CoreLoad load = CoreBinary.load(Paths.get(property("weivel.test.coreBinary")));
        if (!(load instanceof CoreLoad.Loaded)) {
            throw new IllegalStateException("the core did not load: " + load);
        }
        return ((CoreLoad.Loaded) load).binary();
    }

    private static LayerDriver loadedDriver() {
        Answer<LayerDriver> loaded = LayerDriver.load(CORE, Paths.get(property("weivel.test.standardBinary")),
                Paths.get(property("weivel.test.echoBinary")));
        if (!(loaded instanceof Answer.Ok)) {
            throw new IllegalStateException("the driver did not load: " + loaded);
        }
        return ((Answer.Ok<LayerDriver>) loaded).value();
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        if (value == null) {
            throw new IllegalStateException(name + " is not set. Run the tests through Gradle, which sets it.");
        }
        return value;
    }
}
