package com.weivel.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.context.LogRecord;
import com.weivel.core.CoreConstants;
import com.weivel.core.Failure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * One test per {@code description} vector of the pinned kit, each named by the vector's id and comparing everything
 * the run showed with the vector's whole {@code then}, through the host's public surface on the real core, the
 * standard's check and its described library.
 *
 * @implNote On JNA, which lays out no described struct yet, a vector whose description the standard's check passes
 *     is witnessed failing its load with JNA's own explicit "not yet bound on JNA", never silently, and its later
 *     steps are not run: the two that the check decides alone, or that declare no description, are witnessed in full
 *     on both backends.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class DescriptionVectorsTest {
    private static final Pattern MOVED = Pattern.compile("is described at offset (\\d+) and size (\\d+), where this "
            + "platform lays it out at offset (\\d+) and size (\\d+)");

    @Test
    @DisplayName("description/a-checked-description-registers-the-providers-own-pointer")
    void aCheckedDescriptionRegistersTheProvidersOwnPointer() {
        assertBound("description/a-checked-description-registers-the-providers-own-pointer");
    }

    @Test
    @DisplayName("description/a-broken-count-description-fails-the-load")
    void aBrokenCountDescriptionFailsTheLoad() {
        LayerWitness witness = assertWitnessed("description/a-broken-count-description-fails-the-load");

        String why = KitFixture.onJna() ? "not yet bound on JNA" : "check refuses";
        assertTrue(witness.loadMistake().get().contains(why), witness.loadMistake().toString());
    }

    @Test
    @DisplayName("description/a-definition-declared-without-its-description-loads-as-before")
    void aDefinitionDeclaredWithoutItsDescriptionLoadsAsBefore() {
        assertWitnessed("description/a-definition-declared-without-its-description-loads-as-before");
    }

    @Test
    @DisplayName("description/binding/a-broken-offset-description-fails-the-load")
    void aBrokenOffsetDescriptionFailsTheLoad() {
        String id = "description/binding/a-broken-offset-description-fails-the-load";
        if (KitFixture.onJna()) {
            assertNotYetBoundOnJna(id);
            return;
        }
        LayerWitness witness = assertWitnessed(id);

        Matcher moved = MOVED.matcher(witness.loadMistake().get());
        assertTrue(moved.find(), witness.loadMistake().toString());
        assertEquals(4, Long.parseLong(moved.group(1)) - Long.parseLong(moved.group(3)));
        assertEquals(moved.group(2), moved.group(4));
    }

    @Test
    @DisplayName("description/binding/a-handle-the-plugin-never-released-is-released-at-the-unload")
    void aHandleThePluginNeverReleasedIsReleasedAtTheUnload() {
        assertBound("description/binding/a-handle-the-plugin-never-released-is-released-at-the-unload");
    }

    @Test
    @DisplayName("description/binding/a-missing-required-input-answers-malformed")
    void aMissingRequiredInputAnswersMalformed() {
        assertBound("description/binding/a-missing-required-input-answers-malformed");
    }

    @Test
    @DisplayName("description/binding/a-none-answer-arrives-as-none")
    void aNoneAnswerArrivesAsNone() {
        assertBound("description/binding/a-none-answer-arrives-as-none");
    }

    @Test
    @DisplayName("description/binding/a-refusal-arrives-with-its-detail-and-fix")
    void aRefusalArrivesWithItsDetailAndFix() {
        assertBound("description/binding/a-refusal-arrives-with-its-detail-and-fix");
    }

    @Test
    @DisplayName("description/binding/every-answer-is-released-exactly-once")
    void everyAnswerIsReleasedExactlyOnce() {
        assertBound("description/binding/every-answer-is-released-exactly-once");
    }

    @Test
    @DisplayName("description/binding/find-answers-a-text-the-provider-keeps")
    void findAnswersATextTheProviderKeeps() {
        assertBound("description/binding/find-answers-a-text-the-provider-keeps");
    }

    @Test
    @DisplayName("description/binding/grow-takes-and-answers-a-size")
    void growTakesAndAnswersASize() {
        assertBound("description/binding/grow-takes-and-answers-a-size");
    }

    @Test
    @DisplayName("description/binding/halve-takes-and-answers-a-double")
    void halveTakesAndAnswersADouble() {
        assertBound("description/binding/halve-takes-and-answers-a-double");
    }

    @Test
    @DisplayName("description/binding/invert-takes-and-answers-a-32-bit-flag-word")
    void invertTakesAndAnswersA32BitFlagWord() {
        assertBound("description/binding/invert-takes-and-answers-a-32-bit-flag-word");
    }

    @Test
    @DisplayName("description/binding/measure-takes-bytes-and-their-length")
    void measureTakesBytesAndTheirLength() {
        assertBound("description/binding/measure-takes-bytes-and-their-length");
    }

    @Test
    @DisplayName("description/binding/negate-takes-and-answers-a-signed-64-bit-integer")
    void negateTakesAndAnswersASigned64BitInteger() {
        assertBound("description/binding/negate-takes-and-answers-a-signed-64-bit-integer");
    }

    @Test
    @DisplayName("description/binding/open-answers-a-handle-and-use-takes-it")
    void openAnswersAHandleAndUseTakesIt() {
        assertBound("description/binding/open-answers-a-handle-and-use-takes-it");
    }

    @Test
    @DisplayName("description/binding/reflect-answers-every-member-kind")
    void reflectAnswersEveryMemberKind() {
        assertBound("description/binding/reflect-answers-every-member-kind");
    }

    @Test
    @DisplayName("description/binding/swap-shade-takes-and-answers-an-enum-value")
    void swapShadeTakesAndAnswersAnEnumValue() {
        assertBound("description/binding/swap-shade-takes-and-answers-an-enum-value");
    }

    @Test
    @DisplayName("description/binding/tree-answers-a-value-tree")
    void treeAnswersAValueTree() {
        assertBound("description/binding/tree-answers-a-value-tree");
    }

    @Test
    @DisplayName("description/binding/visit-takes-a-handle-inside-a-struct")
    void visitTakesAHandleInsideAStruct() {
        assertBound("description/binding/visit-takes-a-handle-inside-a-struct");
    }

    @Test
    @DisplayName("description/binding/weigh-takes-a-value-by-pointer")
    void weighTakesAValueByPointer() {
        assertBound("description/binding/weigh-takes-a-value-by-pointer");
    }

    /** A vector whose description the check passes, so only a backend that binds descriptions witnesses it. */
    private static void assertBound(String id) {
        if (KitFixture.onJna()) {
            assertNotYetBoundOnJna(id);
            return;
        }
        assertWitnessed(id);
    }

    private static LayerWitness assertWitnessed(String id) {
        LayerVector vector = KitFixture.vector(id);

        LayerWitness witness = KitFixture.witness(vector);

        assertEquals(vector.then(), witness.observed());
        assertEquals(Collections.<Failure>emptyList(), witness.failures());
        assertEquals(Collections.emptyList(), errorLogs(witness));
        assertEquals(id, witness.id());
        return witness;
    }

    /** The vector's load alone, since every step after it reaches services a refused load never registered. */
    private static void assertNotYetBoundOnJna(String id) {
        LayerVector vector = KitFixture.vector(id);
        Map<String, Object> given = new LinkedHashMap<>(vector.given());
        given.put("steps", Collections.singletonList(Collections.singletonMap("load", true)));
        LayerVector loadOnly = new LayerVector(id, vector.op(), given, Collections.<String, Object>singletonMap(
                "loaded", false));

        LayerWitness witness = KitFixture.witness(loadOnly);

        assertEquals(loadOnly.then(), witness.observed());
        assertTrue(witness.loadMistake().isPresent() && witness.loadMistake().get().contains("not yet bound on JNA"),
                witness.loadMistake().toString());
    }

    private static List<String> errorLogs(LayerWitness witness) {
        List<String> errors = new ArrayList<>();
        for (LogRecord record : witness.logs()) {
            if (record.level() == CoreConstants.LOG_ERROR) {
                errors.add(record.text());
            }
        }
        return errors;
    }
}
