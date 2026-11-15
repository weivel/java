package com.weivel.benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.BareServiceGet;
import com.weivel.core.CoreBinary;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreInternals;
import com.weivel.core.CoreLoad;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** The bare call the control ratio weighs the wrapper against, on the real core. */
class BareServiceGetTest {
    @Test
    void answersWhatTheWrapperAnswersAndIsRefusedOnceTheCoreIsDestroyed() {
        try (CoreBinary binary = assertInstanceOf(CoreLoad.Loaded.class,
                CoreBinary.load(Paths.get(System.getProperty("weivel.test.coreBinary")))).binary()) {
            CoreWorld world = CoreWorld.open(binary);
            world.provide(Long::sum);
            Answer<BareServiceGet> prepared = CoreInternals.bareServiceGet(world.core(), world.consumerPlugin(),
                    CoreWorld.ARITHMETIC);
            try (BareServiceGet bare = CoreWorld.ok(prepared, "the bare call's preparation")) {
                long throughTheWrapper = CoreWorld.ok(world.core().call(CoreCalls.getService(world.consumerPlugin(),
                        CoreWorld.ARITHMETIC)), "the wrapper's get");
                assertEquals(throughTheWrapper, bare.call());

                world.close();

                IllegalStateException refused = assertThrows(IllegalStateException.class, bare::call);
                assertTrue(refused.getMessage().contains("destroyed"), refused.getMessage());
            }
            assertInstanceOf(Answer.Destroyed.class, CoreInternals.bareServiceGet(world.core(),
                    world.consumerPlugin(), CoreWorld.ARITHMETIC));
        }
    }
}
