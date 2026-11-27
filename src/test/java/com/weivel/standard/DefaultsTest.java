package com.weivel.standard;

import static com.weivel.standard.StandardFixture.refusal;
import static com.weivel.standard.StandardFixture.standard;
import static com.weivel.standard.StandardFixture.value;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultsTest {
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String HASH = "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab"
            + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab" + "ab"
            + "ab" + "ab" + "ab" + "ab" + "ab" + "ab";

    @Test
    void theServiceIdsAreTheOnesTheStandardAnswers() {
        assertEquals(Arrays.asList("weivel.config", "weivel.secrets", "weivel.paths"), Arrays.asList(
                standard().configServiceId(), standard().secretsServiceId(), standard().pathsServiceId()));
    }

    @Test
    void noMainSourceHoldsAServiceIdOfItsOwn() throws IOException {
        List<String> holding = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(StandardFixture.file("weivel.test.mainSources"))) {
            for (Path source : sources.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
                if (text.contains("\"weivel.config\"") || text.contains("\"weivel.secrets\"")
                        || text.contains("\"weivel.paths\"")) {
                    holding.add(source.toString());
                }
            }
        }

        assertEquals(Collections.<String>emptyList(), holding);
    }

    private static final String ROOT = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
    private static final String LONG_ROOT = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void anIdentityIsTakenApartAsTheStandardTakesIt() {
        assertEquals(new Identity(Identity.Kind.GIT, Optional.<String>empty(), Optional.of(ROOT),
                Arrays.asList("a", "b")), value(standard().parseIdentity("git:" + ROOT + "/a/b")));
        assertEquals(Optional.of(LONG_ROOT), value(standard().parseIdentity("git:" + LONG_ROOT)).commit());
    }

    @Test
    void aLocalNameKeepsEveryUtf8Byte() {
        Identity local = value(standard().parseIdentity("local:Café"));

        assertEquals(Identity.Kind.LOCAL, local.kind());
        assertEquals(Optional.of("Café"), local.name());
        assertEquals(Optional.<String>empty(), local.commit());
    }

    @Test
    void aTextThatIsNoIdentityIsRefusedAtTheByteItStopsBeingOne() {
        assertEquals(StandardRefusal.INVALID_VALUE, refusal(standard().parseIdentity("calendar")).code());
        assertEquals(StandardRefusal.INVALID_VALUE, refusal(standard().parseIdentity("forge:123")).code());
        assertTrue(refusal(standard().parseIdentity("git:" + ROOT + "0")).detail().startsWith("byte 44:"));
        assertTrue(refusal(standard().parseIdentity("local:a\u0000b")).detail().startsWith("byte 7:"));
    }

    @Test
    void identitiesAreOrderedByTheStandard() {
        assertEquals(-1, (int) value(standard().compareIdentities("local:z", "git:" + ROOT)));
        assertEquals(1, (int) value(standard().compareIdentities("git:" + LONG_ROOT, "git:" + ROOT)));
        assertEquals(-1, (int) value(standard().compareIdentities("git:" + ROOT, "git:" + ROOT + "/a")));
        assertEquals(StandardRefusal.INVALID_VALUE,
                refusal(standard().compareIdentities("git:" + ROOT, "Git:1")).code());
    }

    @Test
    void aMirrorListIsReadInFileOrderAndRefusedAtTheTemplateNamed() {
        assertEquals(Arrays.asList("https://one/{sha256}", "https://two/{sha256}.bin"), value(standard()
                .readMirrors("{\"mirrors\": [\"https://one/{sha256}\", \"https://two/{sha256}.bin\"]}")));
        assertEquals("mirrors[1]", refusal(standard()
                .readMirrors("{\"mirrors\": [\"https://one/{sha256}\", \"https://two/\"]}")).path());
    }

    @Test
    void aMirrorListIsLoadedFromAFileAndAMissingOneIsNotFound(@TempDir Path directory) throws IOException {
        Path mirrors = directory.resolve("mirrors.json");
        Files.write(mirrors, "{\"mirrors\": [\"https://one/{sha256}\"]}".getBytes(StandardCharsets.UTF_8));

        assertEquals(Collections.singletonList("https://one/{sha256}"), value(standard().loadMirrors(mirrors)));
        assertEquals(StandardRefusal.NOT_FOUND,
                refusal(standard().loadMirrors(directory.resolve("absent.json"))).code());
    }

    @Test
    void aTemplateIsExpandedForOneHashAndRefusedForAnother() {
        assertEquals("https://one/" + HASH + ".bin",
                value(standard().expandMirror("https://one/{sha256}.bin", HASH)));
        assertEquals(StandardRefusal.INVALID_VALUE,
                refusal(standard().expandMirror("https://one/{sha256}", HASH.toUpperCase())).code());
    }

    @Test
    void sha256IsAnsweredAsTheStandardComputesIt() {
        byte[] abc = "abc".getBytes(StandardCharsets.UTF_8);

        assertEquals(ABC_SHA256, standard().sha256Hex(abc));
        assertArrayEquals(hexBytes(ABC_SHA256), standard().sha256(abc));
        assertTrue(standard().isSha256Hex(ABC_SHA256));
        assertFalse(standard().isSha256Hex(ABC_SHA256.toUpperCase()));
        assertFalse(standard().isSha256Hex(ABC_SHA256 + "\u0000"));
    }

    @Test
    void aDownloadIsVerifiedAgainstTheHashAskedFor() {
        byte[] abc = "abc".getBytes(StandardCharsets.UTF_8);

        assertEquals(StandardStatus.OK, standard().verifyDownload(abc, ABC_SHA256).status());
        StandardRefusal mismatch = refusal(standard().verifyDownload(abc, HASH));
        assertEquals(StandardRefusal.INVALID_VALUE, mismatch.code());
        assertTrue(mismatch.detail().contains(ABC_SHA256) && mismatch.detail().contains(HASH), mismatch.detail());
    }

    private static byte[] hexBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        }
        return bytes;
    }
}
