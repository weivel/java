package com.weivel.handles;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.CoreProbes.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.CallSteps;
import com.weivel.core.Core;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreInternals;
import com.weivel.core.CoreProbes;
import com.weivel.core.Failure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The table under several threads: its own lock, never the layer's, and every entry kept under contention. */
class HandlesThreadsTest {
    private static final long GATE_SECONDS = 20;
    private static final int THREADS = 8;
    private static final int HANDLES_PER_THREAD = 400;

    private final List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
    private final Core core = CoreProbes.core(event -> { }, failures::add);
    private final Handles handles = new Handles();

    @AfterEach
    void destroy() {
        core.destroy();
        core.close();
        assertEquals(List.of(), failures);
    }

    @Test
    @Timeout(60)
    void anotherThreadUsesTheTableWhileAStepMakingAnEntryHoldsTheLayerLock() throws Exception {
        long other = ok(core.call(CoreCalls.registerService(HOST, "other")));
        Object provider = new Object();
        AtomicLong madeHandle = new AtomicLong();
        CountDownLatch made = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Handles.Reservation room = handles.reserve();
        ExecutorService threads = Executors.newSingleThreadExecutor();
        try {
            Future<Answer<Long>> holding = threads.submit(() -> CoreInternals.call(core,
                    CoreCalls.registerService(HOST, "gated"), new CallSteps<>(Optional::empty, handle -> {
                        room.add(HandleKind.SERVICE_REGISTRATION, handle, HOST, provider);
                        madeHandle.set(handle);
                        made.countDown();
                        await(release);
                        return new Answer.Ok<>(handle);
                    })));
            assertTrue(made.await(GATE_SECONDS, TimeUnit.SECONDS));

            assertSame(provider, handles.find(madeHandle.get()).object());
            Handles.Reservation otherRoom = handles.reserve();
            Object handedOver = new Object();
            otherRoom.hold(HOST, other, handedOver);
            assertSame(handedOver, handles.held(HOST, other));

            release.countDown();
            assertEquals(madeHandle.get(), ok(holding.get(GATE_SECONDS, TimeUnit.SECONDS)));
        } finally {
            release.countDown();
            threads.shutdownNow();
        }
    }

    @Test
    @Timeout(120)
    void manyThreadsMakingFindingAndEndingEntriesLoseNone() throws Exception {
        long[][] handlesOfThread = new long[THREADS][HANDLES_PER_THREAD];
        for (int thread = 0; thread < THREADS; thread++) {
            for (int index = 0; index < HANDLES_PER_THREAD; index++) {
                handlesOfThread[thread][index] = ok(core.call(CoreCalls.registerService(HOST,
                        "contended-" + thread + "-" + index)));
            }
        }
        Object[][] objects = new Object[THREADS][HANDLES_PER_THREAD];
        CyclicBarrier start = new CyclicBarrier(THREADS);
        ExecutorService threads = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Integer>> mismatches = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                int own = thread;
                mismatches.add(threads.submit(() -> {
                    start.await(GATE_SECONDS, TimeUnit.SECONDS);
                    return workThrough(handlesOfThread[own], objects[own], handlesOfThread[(own + 1) % THREADS]);
                }));
            }
            for (Future<Integer> mismatch : mismatches) {
                assertEquals(0, mismatch.get(GATE_SECONDS * 3, TimeUnit.SECONDS));
            }
        } finally {
            threads.shutdownNow();
        }

        assertEquals(THREADS * HANDLES_PER_THREAD / 2, handles.entryCount());
        for (int thread = 0; thread < THREADS; thread++) {
            for (int index = 0; index < HANDLES_PER_THREAD; index++) {
                HandleEntry entry = handles.find(handlesOfThread[thread][index]);
                if (index % 2 == 0) {
                    assertNull(entry);
                } else {
                    assertSame(objects[thread][index], entry.object());
                    assertSame(entry, handles.findByToken(entry.token()));
                }
            }
        }
    }

    private int workThrough(long[] own, Object[] objects, long[] neighbours) {
        int mismatches = 0;
        for (int index = 0; index < own.length; index++) {
            objects[index] = new Object();
            handles.reserve().add(HandleKind.SERVICE_REGISTRATION, own[index], HOST, objects[index]);
            HandleEntry neighbour = handles.find(neighbours[index]);
            if (neighbour != null && neighbour.handle() != neighbours[index]) {
                mismatches++;
            }
        }
        for (int index = 0; index < own.length; index++) {
            HandleEntry entry = handles.find(own[index]);
            if (entry == null || entry.object() != objects[index]) {
                mismatches++;
            }
            if (index % 2 == 0 && handles.end(own[index]) != entry) {
                mismatches++;
            }
        }
        return mismatches;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(GATE_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("a gate was never opened");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
