package com.weivel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A place in a call's input, spelled as a refusal names it. */
class CallPathTest {
    @Test
    void spellsMembersElementsAndKeysAsTheRefusalsName() {
        CallPath sample = CallPath.of("reflect's sample");

        assertEquals("reflect's sample", sample.toString());
        assertEquals("reflect's sample.tags[2]", sample.member("tags").element(2).toString());
        assertEquals("reflect's sample.points[0].label", sample.member("points").element(0).member("label")
                .toString());
        assertEquals("weigh's value[1].x's key k", CallPath.of("weigh's value").element(1).member("x").key("k")
                .toString());
    }
}
