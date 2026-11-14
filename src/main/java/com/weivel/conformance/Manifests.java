package com.weivel.conformance;

import com.weivel.core.Choice;
import com.weivel.core.Manifest;
import com.weivel.core.NamedNumber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** A vector's plugins as manifest values, every declaration exactly as written, so the core reads it itself. */
final class Manifests {
    private Manifests() {
    }

    static Manifest capabilities(String importName, List<String> provides, List<String> consumes) {
        return new Manifest(importName, 0, Optional.<String>empty(), Collections.<String>emptyList(), provides,
                consumes, Collections.<Manifest.ServiceOffer>emptyList(), Collections.<Manifest.ServiceWant>emptyList(),
                Collections.<String>emptyList(), Optional.<String>empty());
    }

    static Manifest services(String importName, List<String> provides, List<String> consumes) {
        List<Manifest.ServiceOffer> offers = new ArrayList<>();
        for (String id : provides) {
            offers.add(new Manifest.ServiceOffer(id, Collections.<NamedNumber>emptyList()));
        }
        List<Manifest.ServiceWant> wants = new ArrayList<>();
        for (String id : consumes) {
            wants.add(new Manifest.ServiceWant(id, Optional.<String>empty(), Choice.NO, Choice.NO,
                    Collections.<NamedNumber>emptyList()));
        }
        return new Manifest(importName, 0, Optional.<String>empty(), Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(), offers, wants,
                Collections.<String>emptyList(), Optional.<String>empty());
    }
}
