package com.weivel.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * The strong check behind the kit's {@code layers} drift check: every layer vector of the pinned kit is the display
 * name of a test of this package, as the JUnit Platform discovers them, but those below a directory the kit's
 * {@code checks/languages.txt} exempts Java from, each of which must still hold a vector.
 */
class LayerCoverageTest {
    @Test
    void everyLayerVectorOfThePinnedKitIsTheDisplayNameOfATest() {
        List<String> ids = vectorIds();

        assertFalse(ids.isEmpty(), "the pinned kit carries no layer vector, so nothing was checked");
        assertEquals(Collections.<String>emptyList(), unnamed(ids, displayNames()));
    }

    @Test
    void everyDirectoryTheKitExemptsJavaFromHoldsAVector() {
        List<String> exemptions = KitFixture.exemptions();
        List<String> empty = new ArrayList<>();
        for (String exemption : exemptions) {
            if (exempted(KitFixture.vectors(), exemption).isEmpty()) {
                empty.add(exemption);
            }
        }

        assertFalse(exemptions.isEmpty(), "the pinned kit exempts Java from no directory, so nothing was checked");
        assertEquals(Collections.<String>emptyList(), empty);
    }

    @Test
    void aVectorNoTestNamesIsFoundHoweverDeepItSits() {
        List<String> ids = new ArrayList<>(vectorIds());
        ids.add("nested/deeper/a-vector-no-test-names");

        assertEquals(Collections.singletonList("nested/deeper/a-vector-no-test-names"), unnamed(ids, displayNames()));
    }

    private static List<String> vectorIds() {
        List<LayerVector> vectors = KitFixture.vectors();
        List<String> exempt = new ArrayList<>();
        for (String exemption : KitFixture.exemptions()) {
            exempt.addAll(exempted(vectors, exemption));
        }
        List<String> ids = new ArrayList<>();
        for (LayerVector vector : vectors) {
            if (!exempt.contains(vector.id())) {
                ids.add(vector.id());
            }
        }
        return ids;
    }

    private static List<String> exempted(List<LayerVector> vectors, String directory) {
        List<String> ids = new ArrayList<>();
        for (LayerVector vector : vectors) {
            if (vector.id().startsWith(directory + "/")) {
                ids.add(vector.id());
            }
        }
        return ids;
    }

    private static List<String> unnamed(List<String> ids, Collection<String> names) {
        List<String> unnamed = new ArrayList<>();
        for (String id : ids) {
            if (!names.contains(id)) {
                unnamed.add(id);
            }
        }
        return unnamed;
    }

    /** The display name of every test the JUnit Platform discovers in this package. */
    private static Set<String> displayNames() {
        Launcher launcher = LauncherFactory.create();
        TestPlan plan = launcher.discover(LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage(LayerCoverageTest.class.getPackage().getName()))
                .build());
        Set<String> names = new TreeSet<>();
        for (TestIdentifier root : plan.getRoots()) {
            for (TestIdentifier identifier : plan.getDescendants(root)) {
                if (identifier.isTest()) {
                    names.add(identifier.getDisplayName());
                }
            }
        }
        return names;
    }
}
