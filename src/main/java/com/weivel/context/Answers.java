package com.weivel.context;

import com.weivel.core.Answer;

/** Answers of one result type read as another, for every answer but a success, which carries its own value. */
final class Answers {
    private Answers() {
    }

    static <T> Answer<T> retyped(Answer<?> answer) {
        if (answer instanceof Answer.Ok) {
            throw new IllegalArgumentException("a success carries its own value");
        }
        if (answer instanceof Answer.Absent) {
            return new Answer.Absent<>();
        }
        if (answer instanceof Answer.Refused) {
            Answer.Refused<?> refused = (Answer.Refused<?>) answer;
            return new Answer.Refused<>(refused.status(), refused.refusal());
        }
        if (answer instanceof Answer.Detached) {
            return new Answer.Detached<>();
        }
        if (answer instanceof Answer.Destroyed) {
            return new Answer.Destroyed<>();
        }
        if (answer instanceof Answer.Defect) {
            return new Answer.Defect<>(((Answer.Defect<?>) answer).detail());
        }
        return new Answer.Mistake<>(((Answer.Mistake<?>) answer).detail());
    }
}
