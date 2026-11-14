package com.weivel.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.CoreProbes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every struct the standard fills is handed to the standard's own release before the call answers, the answer's and
 * the refusal's alike, and a release that fails leaves every other release of the call to run.
 */
class ReleasesTest {
    private static final String HASH = new String(new char[32]).replace("\0", "ab");
    private static final String LIBRARY = "{\"importName\": \"echo\", \"version\": \"0.1.0\", \"release\": \"r\","
            + " \"services\": [{\"id\": \"echo\", \"functions\": [{\"name\": \"e\", \"symbol\": \"s\","
            + " \"kind\": \"bytes\"}]}]}";

    private final List<StandardFunction> released = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> structs = Collections.synchronizedList(new ArrayList<>());
    private StandardBinary binary;
    private Standard standard;

    @BeforeEach
    void loadAStandardOfTheTestsOwn() {
        binary = CoreProbes.ok(StandardBinary.load(StandardFixture.file("weivel.test.standardBinary")));
        binary.observeReleases((release, struct) -> {
            released.add(release);
            structs.add(struct);
        });
        standard = Standard.of(binary);
    }

    @AfterEach
    void closeIt() {
        binary.close();
    }

    @Test
    void aLoadedManifestAndItsValidationAreReleased() {
        StandardFixture.value(standard.loadManifest(ManifestsTest.FULL_MANIFEST, ValidationOptions.defaults()));

        assertReleased(StandardFunction.MANIFEST_RELEASE, StandardFunction.VALIDATION_RELEASE);
        assertTrue(structs.get(0) != 0, "the manifest handed to its release was NULL");
    }

    @Test
    void aManifestReadWithoutValidationAndItsRefusalAreReleased() {
        StandardFixture.value(standard.readManifest(ManifestsTest.FULL_MANIFEST));

        assertReleased(StandardFunction.MANIFEST_RELEASE, StandardFunction.REFUSAL_RELEASE);
        assertTrue(structs.get(0) != 0, "the manifest handed to its release was NULL");
    }

    @Test
    void aValidationIsReleased() {
        standard.validate("{\"importName\": \"Bad Name\"}", ValidationOptions.defaults());

        assertReleased(StandardFunction.VALIDATION_RELEASE);
    }

    @Test
    void aDiscoveryIsReleased(@TempDir Path directory) {
        StandardFixture.value(standard.discover(directory, ValidationOptions.defaults()));

        assertReleased(StandardFunction.DISCOVERY_RELEASE);
    }

    @Test
    void aLibraryDeclarationAndItsRefusalAreReleased() {
        StandardFixture.value(standard.readLibrary(LIBRARY));

        assertReleased(StandardFunction.LIBRARY_RELEASE, StandardFunction.REFUSAL_RELEASE);
    }

    @Test
    void aMirrorListAndItsRefusalAreReleased() {
        standard.readMirrors("{\"mirrors\": [\"https://one/\"]}");

        assertReleased(StandardFunction.MIRROR_LIST_RELEASE, StandardFunction.REFUSAL_RELEASE);
    }

    @Test
    void anExpandedUrlAndItsRefusalAreReleased() {
        StandardFixture.value(standard.expandMirror("https://one/{sha256}", HASH));

        assertReleased(StandardFunction.TEXT_RELEASE, StandardFunction.REFUSAL_RELEASE);
        assertTrue(structs.get(0) != 0, "the URL handed to its release was NULL");
    }

    @Test
    void anIdentityAndItsRefusalAreReleasedForEachSideCompared() {
        StandardFixture.value(standard.compareIdentities("forge:1", "forge:2"));

        assertReleased(StandardFunction.IDENTITY_RELEASE, StandardFunction.REFUSAL_RELEASE,
                StandardFunction.IDENTITY_RELEASE, StandardFunction.REFUSAL_RELEASE);
    }

    @Test
    void aVerificationsRefusalIsReleased() {
        standard.verifyDownload("abc".getBytes(StandardCharsets.UTF_8), HASH);

        assertReleased(StandardFunction.REFUSAL_RELEASE);
    }

    @Test
    void aReleaseThatFailsLeavesTheOthersOfItsCallToRun() {
        assertSecondRunsAfterFirstFails(StandardFunction.MANIFEST_RELEASE, StandardFunction.VALIDATION_RELEASE,
                current -> current.loadManifest(ManifestsTest.FULL_MANIFEST, ValidationOptions.defaults()));
        assertSecondRunsAfterFirstFails(StandardFunction.MANIFEST_RELEASE, StandardFunction.REFUSAL_RELEASE,
                current -> current.readManifest(ManifestsTest.FULL_MANIFEST));
        assertSecondRunsAfterFirstFails(StandardFunction.LIBRARY_RELEASE, StandardFunction.REFUSAL_RELEASE,
                current -> current.readLibrary(LIBRARY));
        assertSecondRunsAfterFirstFails(StandardFunction.MIRROR_LIST_RELEASE, StandardFunction.REFUSAL_RELEASE,
                current -> current.readMirrors("{\"mirrors\": [\"https://one/{sha256}\"]}"));
        assertSecondRunsAfterFirstFails(StandardFunction.TEXT_RELEASE, StandardFunction.REFUSAL_RELEASE,
                current -> current.expandMirror("https://one/{sha256}", HASH));
        assertSecondRunsAfterFirstFails(StandardFunction.IDENTITY_RELEASE, StandardFunction.REFUSAL_RELEASE,
                current -> current.parseIdentity("forge:1"));
    }

    private void assertSecondRunsAfterFirstFails(StandardFunction first, StandardFunction second,
                                                 Consumer<Standard> call) {
        List<StandardFunction> seen = new ArrayList<>();
        binary.observeReleases((release, struct) -> {
            seen.add(release);
            if (release == first) {
                throw new IllegalStateException("the first release failed");
            }
        });

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> call.accept(standard));

        assertEquals("the first release failed", thrown.getMessage());
        assertEquals(Arrays.asList(first, second), seen, first + " then " + second);
    }

    private void assertReleased(StandardFunction... expected) {
        assertEquals(Arrays.asList(expected), released);
    }
}
