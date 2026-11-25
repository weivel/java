package com.weivel.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.context.LogRecord;
import com.weivel.core.CoreConstants;
import com.weivel.core.Failure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * One test per {@code description} vector of the pinned kit, each named by the vector's id and comparing everything
 * the run showed with the vector's whole {@code then}, through the host's public surface on the real core, the
 * standard's check and its described library, on whichever backend the suite runs.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class DescriptionVectorsTest {
    private static final Pattern MOVED = Pattern.compile("is described at offset (\\d+) and size (\\d+), where this "
            + "platform lays it out at offset (\\d+) and size (\\d+)");

    /**
     * The vector loading the same library's every description unbroken, witnessed beside the broken offset so the
     * refusal is known to be the moved member's and not a platform layout that disagrees with every description.
     */
    private static final String UNBROKEN = "description/a-checked-description-registers-the-providers-own-pointer";

    @Test
    @DisplayName("description/a-checked-description-registers-the-providers-own-pointer")
    void aCheckedDescriptionRegistersTheProvidersOwnPointer() {
        assertWitnessed("description/a-checked-description-registers-the-providers-own-pointer");
    }

    @Test
    @DisplayName("description/a-broken-count-description-fails-the-load")
    void aBrokenCountDescriptionFailsTheLoad() {
        LayerWitness witness = assertWitnessed("description/a-broken-count-description-fails-the-load");

        assertTrue(witness.loadMistake().get().contains("check refuses"), witness.loadMistake().toString());
    }

    @Test
    @DisplayName("description/a-definition-described-without-its-nested-description-fails-the-load")
    void aDefinitionDescribedWithoutItsNestedDescriptionFailsTheLoad() {
        LayerWitness witness = assertWitnessed(
                "description/a-definition-described-without-its-nested-description-fails-the-load");

        assertTrue(witness.loadMistake().get().contains("check refuses"), witness.loadMistake().toString());
    }

    @Test
    @DisplayName("description/a-description-answering-another-definition-fails-the-load")
    void aDescriptionAnsweringAnotherDefinitionFailsTheLoad() {
        LayerWitness witness = assertWitnessed(
                "description/a-description-answering-another-definition-fails-the-load");

        assertTrue(witness.loadMistake().get().contains("at answered_by"), witness.loadMistake().toString());
    }

    @Test
    @DisplayName("description/a-definition-declared-without-its-description-loads-as-before")
    void aDefinitionDeclaredWithoutItsDescriptionLoadsAsBefore() {
        assertWitnessed("description/a-definition-declared-without-its-description-loads-as-before");
    }

    @Test
    @DisplayName("description/a-library-publishing-only-contracts-loads-and-registers-nothing")
    void aLibraryPublishingOnlyContractsLoadsAndRegistersNothing() {
        assertWitnessed("description/a-library-publishing-only-contracts-loads-and-registers-nothing");
    }

    @Test
    @DisplayName("description/binding/a-broken-offset-description-fails-the-load")
    void aBrokenOffsetDescriptionFailsTheLoad() {
        assertWitnessed(UNBROKEN);
        LayerWitness witness = assertWitnessed("description/binding/a-broken-offset-description-fails-the-load");

        Matcher moved = MOVED.matcher(witness.loadMistake().get());
        assertTrue(moved.find(), witness.loadMistake().toString());
        assertEquals(4, Long.parseLong(moved.group(1)) - Long.parseLong(moved.group(3)));
        assertEquals(moved.group(2), moved.group(4));
    }

    @Test
    @DisplayName("description/binding/a-blocking-operation-answers-as-any-does-and-is-reported-blocking")
    void aBlockingOperationAnswersAsAnyDoesAndIsReportedBlocking() {
        assertWitnessed("description/binding/a-blocking-operation-answers-as-any-does-and-is-reported-blocking");
    }

    @Test
    @DisplayName("description/binding/a-nested-definitions-answers-are-released-exactly-once")
    void aNestedDefinitionsAnswersAreReleasedExactlyOnce() {
        assertWitnessed("description/binding/a-nested-definitions-answers-are-released-exactly-once");
    }

    @Test
    @DisplayName("description/binding/an-answer-carrying-a-definition-is-callable-until-released")
    void anAnswerCarryingADefinitionIsCallableUntilReleased() {
        assertWitnessed("description/binding/an-answer-carrying-a-definition-is-callable-until-released");
    }

    @Test
    @DisplayName("description/binding/a-handle-the-plugin-never-released-is-released-at-the-unload")
    void aHandleThePluginNeverReleasedIsReleasedAtTheUnload() {
        assertWitnessed("description/binding/a-handle-the-plugin-never-released-is-released-at-the-unload");
    }

    @Test
    @DisplayName("description/binding/a-missing-required-input-answers-malformed")
    void aMissingRequiredInputAnswersMalformed() {
        assertWitnessed("description/binding/a-missing-required-input-answers-malformed");
    }

    @Test
    @DisplayName("description/binding/an-input-the-operation-does-not-take-is-the-callers-mistake")
    void anInputTheOperationDoesNotTakeIsTheCallersMistake() {
        assertWitnessed("description/binding/an-input-the-operation-does-not-take-is-the-callers-mistake");
    }

    @Test
    @DisplayName("description/binding/an-operation-its-provider-does-not-offer-is-the-callers-mistake")
    void anOperationItsProviderDoesNotOfferIsTheCallersMistake() {
        assertWitnessed("description/binding/an-operation-its-provider-does-not-offer-is-the-callers-mistake");
    }

    @Test
    @DisplayName("description/binding/a-none-answer-arrives-as-none")
    void aNoneAnswerArrivesAsNone() {
        assertWitnessed("description/binding/a-none-answer-arrives-as-none");
    }

    @Test
    @DisplayName("description/binding/a-refusal-arrives-with-its-detail-and-fix")
    void aRefusalArrivesWithItsDetailAndFix() {
        assertWitnessed("description/binding/a-refusal-arrives-with-its-detail-and-fix");
    }

    @Test
    @DisplayName("description/binding/every-answer-is-released-exactly-once")
    void everyAnswerIsReleasedExactlyOnce() {
        assertWitnessed("description/binding/every-answer-is-released-exactly-once");
    }

    @Test
    @DisplayName("description/binding/find-answers-a-text-the-provider-keeps")
    void findAnswersATextTheProviderKeeps() {
        assertWitnessed("description/binding/find-answers-a-text-the-provider-keeps");
    }

    @Test
    @DisplayName("description/binding/grow-takes-and-answers-a-size")
    void growTakesAndAnswersASize() {
        assertWitnessed("description/binding/grow-takes-and-answers-a-size");
    }

    @Test
    @DisplayName("description/binding/halve-takes-and-answers-a-double")
    void halveTakesAndAnswersADouble() {
        assertWitnessed("description/binding/halve-takes-and-answers-a-double");
    }

    @Test
    @DisplayName("description/binding/invert-takes-and-answers-a-32-bit-flag-word")
    void invertTakesAndAnswersA32BitFlagWord() {
        assertWitnessed("description/binding/invert-takes-and-answers-a-32-bit-flag-word");
    }

    @Test
    @DisplayName("description/binding/measure-takes-bytes-and-their-length")
    void measureTakesBytesAndTheirLength() {
        assertWitnessed("description/binding/measure-takes-bytes-and-their-length");
    }

    @Test
    @DisplayName("description/binding/negate-takes-and-answers-a-signed-64-bit-integer")
    void negateTakesAndAnswersASigned64BitInteger() {
        assertWitnessed("description/binding/negate-takes-and-answers-a-signed-64-bit-integer");
    }

    @Test
    @DisplayName("description/binding/open-answers-a-handle-and-use-takes-it")
    void openAnswersAHandleAndUseTakesIt() {
        assertWitnessed("description/binding/open-answers-a-handle-and-use-takes-it");
    }

    @Test
    @DisplayName("description/binding/outline-copies-nodes-holding-nodes-both-ways")
    void outlineCopiesNodesHoldingNodesBothWays() {
        assertWitnessed("description/binding/outline-copies-nodes-holding-nodes-both-ways");
    }

    @Test
    @DisplayName("description/binding/reflect-answers-every-member-kind")
    void reflectAnswersEveryMemberKind() {
        assertWitnessed("description/binding/reflect-answers-every-member-kind");
    }

    @Test
    @DisplayName("description/binding/swap-shade-takes-and-answers-an-enum-value")
    void swapShadeTakesAndAnswersAnEnumValue() {
        assertWitnessed("description/binding/swap-shade-takes-and-answers-an-enum-value");
    }

    @Test
    @DisplayName("description/binding/tree-answers-a-value-tree")
    void treeAnswersAValueTree() {
        assertWitnessed("description/binding/tree-answers-a-value-tree");
    }

    @Test
    @DisplayName("description/binding/visit-takes-a-handle-inside-a-struct")
    void visitTakesAHandleInsideAStruct() {
        assertWitnessed("description/binding/visit-takes-a-handle-inside-a-struct");
    }

    @Test
    @DisplayName("description/binding/weigh-takes-a-value-by-pointer")
    void weighTakesAValueByPointer() {
        assertWitnessed("description/binding/weigh-takes-a-value-by-pointer");
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
