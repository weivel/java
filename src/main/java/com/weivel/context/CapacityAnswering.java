package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.CapacityAnswer;
import com.weivel.core.Choice;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreConstants;
import com.weivel.core.CoreEvent;
import com.weivel.core.FailureKind;
import com.weivel.core.NamedNumber;
import com.weivel.handles.HandleKind;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Puts a constrained want's question to the registration it names and reports the answer to the core.
 *
 * @implNote A question that arrived incomplete is left unanswered and put to no one, since its constraints are
 * missing rather than empty; the wrapper that marked it told the sink. A registration that offered no answerer answers
 * at once with no live attributes and that it serves every consumer.
 */
final class CapacityAnswering {
    private CapacityAnswering() {
    }

    static void answer(ContextState state, CoreEvent.CapacityWantedEvent question) {
        if (question.incomplete()) {
            return;
        }
        Optional<Records.Service> registration = state.recordOf(question.registration(),
                HandleKind.SERVICE_REGISTRATION, Records.Service.class);
        if (registration.isEmpty()) {
            return;
        }
        Optional<CapacityAnswer> answer = answerOf(state, registration.get().answerer(), question);
        answer.ifPresent(answered -> report(state, question, answered));
    }

    private static Optional<CapacityAnswer> answerOf(ContextState state, Optional<CapacityAnswerer> answerer,
                                                     CoreEvent.CapacityWantedEvent question) {
        try {
            return switch (question.question()) {
                case CoreConstants.CAPACITY_ATTRIBUTES -> Optional.of(new CapacityAnswer(question.request(),
                        answerer.map(CapacityAnswerer::attributes).orElse(List.of()), Choice.DEFAULT));
                case CoreConstants.CAPACITY_SERVE -> Optional.of(new CapacityAnswer(question.request(), List.of(),
                        serves(answerer, question.constraints()) ? Choice.YES : Choice.NO));
                default -> Optional.empty();
            };
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable failure) {
            state.report(FailureKind.PLUGIN_CALLBACK, "a capacity answerer threw, so its question went unanswered",
                    Optional.of(question), Optional.of(failure));
            return Optional.empty();
        }
    }

    private static boolean serves(Optional<CapacityAnswerer> answerer, List<NamedNumber> constraints) {
        return answerer.map(offered -> offered.serves(constraints)).orElse(true);
    }

    /** Hands the core the answer; one it does not take goes to the sink, an absence being a question already closed. */
    private static void report(ContextState state, CoreEvent.CapacityWantedEvent question, CapacityAnswer answer) {
        OptionalLong now = state.timer.time();
        if (now.isEmpty()) {
            state.report(FailureKind.CAPACITY_UNANSWERED, "a capacity question has no clock to be answered by",
                    Optional.of(question), Optional.empty());
            return;
        }
        Answer<Void> reported = state.core.call(CoreCalls.reportCapacity(question.want(), question.registration(),
                answer, now.getAsLong()));
        if (!(reported instanceof Answer.Ok<Void>) && !(reported instanceof Answer.Absent<Void>)
                && !(reported instanceof Answer.Destroyed<Void>)) {
            state.report(FailureKind.CAPACITY_UNANSWERED, "the core did not take a capacity answer: " + reported,
                    Optional.of(question), Optional.empty());
        }
    }
}
