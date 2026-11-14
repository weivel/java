package com.weivel.core;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class CoreLayoutsTest {
    @Test
    void everyCoreLayoutTheLayerDeclaresMatchesTheCompilers() throws IOException, InterruptedException {
        CLayouts printed = CLayouts.printedBy(NativeFiles.coreLayouts());

        LayoutComparison.assertEveryDeclaredLayoutMatches(CoreLayouts.class, printed);
    }
}
