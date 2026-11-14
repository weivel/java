package com.weivel.conformance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers a vector's {@code then} field by field from what a run shows, in the field's own shape. */
final class Observations {
    private static final String EQUALS = "equals";
    private static final String BEFORE = "before";

    private Observations() {
    }

    /** What a run shows for one {@code then} field, handed the field's expectation for a matcher that names it. */
    interface Observer {
        Object observe(Object expectation);
    }

    /**
     * Every field of {@code then} as the run shows it.
     *
     * @throws IllegalArgumentException for a field no observer answers, so a field the driver does not know fails
     */
    static Map<String, Object> observe(String vectorId, Map<String, Object> then, Map<String, Observer> observers) {
        Map<String, Object> observed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> field : then.entrySet()) {
            Observer observer = observers.get(field.getKey());
            if (observer == null) {
                throw new IllegalArgumentException("the vector " + vectorId + " states then." + field.getKey()
                        + ", which this driver does not witness");
            }
            Object answer = observer.observe(field.getValue());
            observed.put(field.getKey(), isEqualsMatcher(field.getValue())
                    ? Collections.singletonMap(EQUALS, answer)
                    : answer);
        }
        return observed;
    }

    /**
     * The pairs a {@code before} matcher asks about, each one the trace keeps in that order answered as asked and
     * any other marked with the trace, so the answer equals the expectation only when every pair holds.
     */
    static Map<String, Object> before(Object expectation, List<String> trace) {
        Map<String, Object> matcher = Shapes.object("a trace expectation", expectation);
        Shapes.refuseUnknownKeys("a trace expectation", matcher, BEFORE);
        List<Object> answered = new ArrayList<>();
        for (Object pair : Shapes.array("a trace expectation", matcher, BEFORE)) {
            answered.add(pairAsTheTraceHasIt(pair, trace));
        }
        return Collections.<String, Object>singletonMap(BEFORE, answered);
    }

    private static List<Object> pairAsTheTraceHasIt(Object pair, List<String> trace) {
        if (!(pair instanceof List) || ((List<?>) pair).size() != 2) {
            throw new IllegalArgumentException("a before pair " + pair + " is no pair");
        }
        Object earlier = ((List<?>) pair).get(0);
        Object later = ((List<?>) pair).get(1);
        int earlierAt = trace.indexOf(earlier);
        int laterAt = trace.indexOf(later);
        if (earlierAt != -1 && laterAt != -1 && earlierAt < laterAt) {
            return Arrays.asList(earlier, later);
        }
        return Arrays.asList(earlier, later, "not in that order in " + trace);
    }

    private static boolean isEqualsMatcher(Object expectation) {
        return expectation instanceof Map && ((Map<?, ?>) expectation).size() == 1
                && ((Map<?, ?>) expectation).containsKey(EQUALS);
    }
}
