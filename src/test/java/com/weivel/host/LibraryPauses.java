package com.weivel.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The host's library steps as a test chooses them: each close of a binary recorded, one refused, and a gate or an
 * action at each point a library's load, call or unload passes, each used once.
 */
final class LibraryPauses implements Pauses {
    private final List<String> closingAsked = Collections.synchronizedList(new ArrayList<>());
    private final List<String> closed = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean callHeld = new AtomicBoolean();
    private final AtomicBoolean keptHeld = new AtomicBoolean();
    private final AtomicBoolean claimHeld = new AtomicBoolean();
    private final AtomicBoolean registeredHeld = new AtomicBoolean();
    private final AtomicBoolean unregisteringRun = new AtomicBoolean();
    volatile Gates.Gate holdCalls;
    volatile Gates.Gate holdBeforeKept;
    volatile Gates.Gate holdAfterClaim;
    volatile Gates.Gate holdAfterRegistered;
    volatile Runnable whileUnregistering;
    volatile String refuseToClose;
    volatile String failAfterRegistering;

    @Override
    public void whileALibraryCallRuns(String id) {
        holdOnce(holdCalls, callHeld);
    }

    @Override
    public void beforeALibraryIsKept(String importName) {
        holdOnce(holdBeforeKept, keptHeld);
    }

    @Override
    public void afterAHostServiceIsClaimed(String id) {
        holdOnce(holdAfterClaim, claimHeld);
    }

    @Override
    public void afterALibraryServiceIsRegistered(String id) {
        if (id.equals(failAfterRegistering)) {
            throw new IllegalStateException("a defect of the layer, standing in after " + id + " was registered");
        }
    }

    @Override
    public void afterALibrarysServicesAreRegistered(String importName) {
        holdOnce(holdAfterRegistered, registeredHeld);
    }

    @Override
    public void beforeALibrarysServicesAreUnregistered(String importName) {
        Runnable action = whileUnregistering;
        if (action != null && !unregisteringRun.getAndSet(true)) {
            action.run();
        }
    }

    @Override
    public void beforeALibraryCloses(String importName) {
        closingAsked.add(importName);
        if (importName.equals(refuseToClose)) {
            throw new IllegalStateException("the platform refused to close " + importName);
        }
        closed.add(importName);
    }

    List<String> closingAsked() {
        synchronized (closingAsked) {
            return new ArrayList<>(closingAsked);
        }
    }

    List<String> closed() {
        synchronized (closed) {
            return new ArrayList<>(closed);
        }
    }

    private static void holdOnce(Gates.Gate gate, AtomicBoolean held) {
        if (gate != null && !held.getAndSet(true)) {
            gate.hold();
        }
    }
}
