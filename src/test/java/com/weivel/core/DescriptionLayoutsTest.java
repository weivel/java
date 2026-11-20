package com.weivel.core;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class DescriptionLayoutsTest {
    @Test
    void everyDescriptionAndValueLayoutTheCoreDeclaresMatchesTheCompilers() throws IOException, InterruptedException {
        CLayouts printed = CLayouts.printedBy(NativeFiles.standardLayouts());

        LayoutComparison.assertEveryDeclaredLayoutMatches(DescriptionLayouts.class, printed);
    }
}
