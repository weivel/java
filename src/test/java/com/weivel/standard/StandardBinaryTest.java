package com.weivel.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.CLayouts;
import com.weivel.core.CoreProbes;
import com.weivel.core.Layout;
import com.weivel.core.LayoutComparison;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardBinaryTest {
    @Test
    void theStandardsBinaryLoadsAndCloses() {
        StandardBinary binary = CoreProbes.ok(StandardBinary.load(StandardFixture.file("weivel.test.standardBinary")));

        binary.close();
    }

    @Test
    void aPathThatIsNoLibraryIsAnsweredAsAMistake(@TempDir Path directory) {
        Path nothing = directory.resolve("nothing.bin");

        Answer<StandardBinary> loaded = StandardBinary.load(nothing);

        assertInstanceOf(Answer.Mistake.class, loaded);
        assertTrue(((Answer.Mistake<?>) loaded).detail().contains("no library"), loaded.toString());
    }

    @Test
    void aLibraryLackingAFunctionOfTheStandardsIsRefusedNamingIt() {
        Answer<StandardBinary> loaded = StandardBinary.load(StandardFixture.file("weivel.test.coreBinary"));

        assertInstanceOf(Answer.Mistake.class, loaded);
        assertTrue(((Answer.Mistake<?>) loaded).detail().endsWith("exports no function "
                + StandardFunction.values()[0].symbol()), loaded.toString());
    }

    @Test
    void everyStandardLayoutTheLayerDeclaresMatchesTheCompilers() throws IOException, InterruptedException {
        Map<String, CLayouts.CStruct> printed = new LinkedHashMap<>(
                CLayouts.printedBy(StandardFixture.file("weivel.test.standardLayouts")).structs());
        CLayouts core = CLayouts.printedBy(StandardFixture.file("weivel.test.coreLayouts"));
        printed.put("weivel_strings", core.structs().get("weivel_strings"));

        LayoutComparison.assertEveryDeclaredLayoutMatches(StandardLayouts.class, new CLayouts(printed));
    }

    @Test
    void everyStructTheStandardsLayoutsProgramPrintsIsDeclared() throws IOException, InterruptedException {
        CLayouts printed = CLayouts.printedBy(StandardFixture.file("weivel.test.standardLayouts"));
        Set<String> declared = new HashSet<>();
        for (Field field : StandardLayouts.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Layout.class) {
                declared.add(layoutOf(field).name().orElse(""));
            }
        }

        Set<String> undeclared = new TreeSet<>(printed.structs().keySet());
        undeclared.removeAll(declared);
        assertEquals(Collections.emptySet(), undeclared);
    }

    private static Layout layoutOf(Field field) {
        try {
            field.setAccessible(true);
            return (Layout) field.get(null);
        } catch (IllegalAccessException inaccessible) {
            throw new AssertionError(inaccessible);
        }
    }
}
