package com.weivel.standard;

/** Told of every struct this package hands to one of the standard's releases, just before the release runs. */
interface ReleaseObserver {
    ReleaseObserver NONE = (release, struct) -> {
    };

    void releasing(StandardFunction release, long struct);
}
