package com.weivel.standard;

import static com.weivel.standard.StandardFixture.refusal;
import static com.weivel.standard.StandardFixture.standard;
import static com.weivel.standard.StandardFixture.value;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
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

    @Test
    void anIdentityIsTakenApartAsTheStandardTakesIt() {
        assertEquals(new Identity(Identity.Kind.FORGE, Optional.<String>empty(), Optional.of("gitea"),
                Optional.of("git.example.com"), BigInteger.valueOf(123), Arrays.asList("a", "b")),
                value(standard().parseIdentity("gitea@git.example.com:0123/a/b")));
    }

    @Test
    void aLocalNameKeepsItsLengthANulAndEveryUtf8Byte() {
        Identity local = value(standard().parseIdentity("local:Café\u0000x"));

        assertEquals(Identity.Kind.LOCAL, local.kind());
        assertEquals(Optional.of("Café\u0000x"), local.name());
        assertEquals(BigInteger.ZERO, local.number());
    }

    @Test
    void aTextThatIsNoIdentityOrANumberPast64BitsIsRefused() {
        assertEquals(StandardRefusal.INVALID_VALUE, refusal(standard().parseIdentity("calendar")).code());
        assertEquals(StandardRefusal.UNREPRESENTABLE,
                refusal(standard().parseIdentity("forge:18446744073709551616")).code());
        assertEquals(new BigInteger("18446744073709551615"),
                value(standard().parseIdentity("forge:18446744073709551615")).number());
    }

    @Test
    void identitiesAreOrderedByTheStandard() {
        assertTrue(value(standard().compareIdentities("forge:9", "forge:10")) < 0);
        assertEquals(0, (int) value(standard().compareIdentities("forge:007", "forge:7")));
        assertTrue(value(standard().compareIdentities("forge:1", "local:a")) > 0);
        assertEquals(StandardRefusal.INVALID_VALUE,
                refusal(standard().compareIdentities("forge:1", "Forge:1")).code());
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
