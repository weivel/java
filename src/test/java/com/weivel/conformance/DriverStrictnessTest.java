package com.weivel.conformance;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.weivel.core.Answer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A vector part the driver does not drive as written fails the run, never passes unread. */
class DriverStrictnessTest {
    private static final String ONE_PLUGIN = "\"plugins\": [{\"id\": \"alone\", \"capabilities\": "
            + "{\"provides\": [], \"consumes\": []}}]";
    private static StandardJson json;

    @BeforeAll
    static void bindTheReader() {
        Answer<StandardJson> loaded = StandardJson.load(Paths.get(System.getProperty("weivel.test.standardBinary")));
        json = ((Answer.Ok<StandardJson>) loaded).value();
    }

    @AfterAll
    static void closeTheReader() {
        json.close();
    }

    @Test
    void aConsumeVerifyThatIsNotTrueFailsTheRun() {
        LayerVector vector = vector("consume/false-verify", "consume",
                "{" + ONE_PLUGIN + ", \"steps\": [{\"plugin\": \"alone\", \"verify\": false}]}");

        assertThrows(IllegalArgumentException.class, () -> KitFixture.witness(vector));
    }

    @Test
    void aConsumeReleaseThatIsNotTrueFailsTheRun() {
        LayerVector vector = vector("consume/text-release", "consume",
                "{" + ONE_PLUGIN + ", \"steps\": [{\"plugin\": \"alone\", \"release\": \"yes\"}]}");

        assertThrows(IllegalArgumentException.class, () -> KitFixture.witness(vector));
    }

    @Test
    void aLifecycleDestroyThatIsNotTrueFailsTheRun() {
        LayerVector vector = vector("lifecycle/false-destroy", "lifecycle",
                "{\"edgeSource\": \"capabilities\", \"plugins\": [], \"steps\": [{\"destroy\": false}]}");

        assertThrows(IllegalArgumentException.class, () -> KitFixture.witness(vector));
    }

    @Test
    void aCoversThatIsNoTextIsRefused() {
        String document = "{\"id\": \"want/numbered\", \"op\": \"want\", \"covers\": 8, \"given\": {}, \"then\": {}}";

        assertThrows(IllegalArgumentException.class, () -> LayerVectors.vector(json,
                document.getBytes(StandardCharsets.UTF_8), "want/numbered"));
    }

    private static LayerVector vector(String id, String op, String given) {
        String document = "{\"id\": \"" + id + "\", \"op\": \"" + op + "\", \"covers\": \"invariant-0\", \"given\": "
                + given + ", \"then\": {}}";
        return LayerVectors.vector(json, document.getBytes(StandardCharsets.UTF_8), id);
    }
}
