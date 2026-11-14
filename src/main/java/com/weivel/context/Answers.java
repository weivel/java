package com.weivel.context;

import com.weivel.core.Answer;

/** Answers of one result type read as another, for every answer but a success, which carries its own value. */
final class Answers {
    private Answers() {
    }

    static <T> Answer<T> retyped(Answer<?> answer) {
        return switch (answer) {
            case Answer.Ok<?> ok -> throw new IllegalArgumentException("a success carries its own value");
            case Answer.Absent<?> absent -> new Answer.Absent<>();
            case Answer.Refused<?> refused -> new Answer.Refused<>(refused.status(), refused.refusal());
            case Answer.Detached<?> detached -> new Answer.Detached<>();
            case Answer.Destroyed<?> destroyed -> new Answer.Destroyed<>();
            case Answer.Defect<?> defect -> new Answer.Defect<>(defect.detail());
            case Answer.Mistake<?> mistake -> new Answer.Mistake<>(mistake.detail());
        };
    }
}
