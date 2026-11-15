package com.weivel.benchmarks;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.LongBinaryOperator;
import org.junit.jupiter.api.Test;

/** The same-language gate's refusal, which no real resolution reaches while the core answers the provider's object. */
class SameLanguageGateTest {
    @Test
    void refusesToTimeAResolutionThatIsNotTheProvidersOwnObject() {
        LongBinaryOperator provided = Long::sum;
        LongBinaryOperator lookalike = Long::sum;
        SameLanguageBenchmark.Resolution resolution = new SameLanguageBenchmark.Resolution(provided, lookalike);

        assertThrows(IllegalStateException.class,
                () -> SameLanguageBenchmark.measure(resolution, new Sampling(0, 1, 1)));
    }
}
