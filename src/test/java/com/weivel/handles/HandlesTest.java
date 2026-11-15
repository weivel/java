package com.weivel.handles;

import static com.weivel.core.CoreConstants.HOST;
import static com.weivel.core.CoreProbes.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weivel.core.Answer;
import com.weivel.core.CallSteps;
import com.weivel.core.Choice;
import com.weivel.core.Core;
import com.weivel.core.CoreCall;
import com.weivel.core.CoreCalls;
import com.weivel.core.CoreEvent;
import com.weivel.core.CoreInternals;
import com.weivel.core.CoreProbes;
import com.weivel.core.Failure;
import com.weivel.core.Manifest;
import com.weivel.core.WantOptions;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The table from the core's handles to the layer's objects, each rule of the contract's {@code handles} section. */
class HandlesTest {
    /**
     * Less than the table's smallest growth and than one boxed handle per lookup measured, and more than a
     * deoptimization of compiled code rematerializes on the measuring thread, which was measured at 112 bytes.
     */
    private static final long JIT_NOISE_BYTES = 256;

    private final List<CoreEvent> heard = Collections.synchronizedList(new ArrayList<>());
    private final List<Failure> failures = Collections.synchronizedList(new ArrayList<>());
    private final Core core = CoreProbes.core(heard::add, failures::add);
    private final Handles handles = new Handles();

    @AfterEach
    void destroy() {
        core.destroy();
        core.close();
        assertEquals(Collections.emptyList(), failures);
    }

    @Test
    void anEntryIsMadeOnceTheCoreAnswersAndBeforeAnyEventNamingItIsDispatched() {
        ok(core.call(CoreCalls.watchService(HOST, "made")));
        Object provider = new Object();
        List<Object> foundWhenHeard = new ArrayList<>();
        core.addListener(event -> {
            if (event instanceof CoreEvent.WatchEvent) {
                CoreEvent.WatchEvent watch = (CoreEvent.WatchEvent) event;
                HandleEntry entry = handles.find(watch.implementation());
                foundWhenHeard.add(entry == null ? "nothing" : entry.object());
            }
        });

        Handles.Reservation room = handles.reserve();

        long registration = ok(CoreInternals.call(core, CoreCalls.registerService(HOST, "made"),
                room.making(HandleKind.SERVICE_REGISTRATION, HOST, provider)));

        assertEquals(Collections.singletonList(provider), foundWhenHeard);
        HandleEntry entry = handles.find(registration);
        assertSame(provider, entry.object());
        assertEquals(HandleKind.SERVICE_REGISTRATION, entry.kind());
        assertEquals(HOST, entry.owner());
        assertEquals(registration, entry.handle());
    }

    /**
     * Making an entry in room already taken allocates nothing.
     *
     * @implNote Every core call is made first and every entry is then made between two reads of the allocation
     *     counter, so any allocation per entry is multiplied by the count while the counter's own cost, which varies
     *     by platform, is paid once; measuring each entry alone made that cost the subject.
     */
    @Test
    void makingAnEntryAllocatesNothingOnceItsRoomIsTaken() {
        int count = 300;
        List<Handles.Reservation> rooms = new ArrayList<>(count);
        long[] answeredHandles = new long[count];
        Object[] providers = new Object[count];
        for (int index = 0; index < count; index++) {
            rooms.add(handles.reserve());
            providers[index] = new Object();
            answeredHandles[index] = ok(CoreInternals.call(core, CoreCalls.registerService(HOST, "room-" + index),
                    new CallSteps<>(() -> Optional.empty(), handle -> new Answer.Ok<>(handle))));
        }

        long before = allocatedBytes();
        for (int index = 0; index < count; index++) {
            rooms.get(index).add(HandleKind.SERVICE_REGISTRATION, answeredHandles[index], HOST, providers[index]);
        }
        long allocated = allocatedBytes() - before;

        assertEquals(count, handles.entryCount());
        assertTrue(allocated <= COUNTER_READ_BYTES, "making " + count + " entries allocated " + allocated + " bytes");
    }

    @Test
    void aReservationTheCallDidNotUseIsGivenBack() {
        made(CoreCalls.registerService(HOST, "taken"), HandleKind.SERVICE_REGISTRATION, HOST, new Object());
        long plugin = made(CoreCalls.registerManifest(Manifest.named("refused")), HandleKind.PLUGIN,
                Handles.NO_OWNER, new Object());
        Handles.Reservation room = handles.reserve();

        Answer<Long> refused = CoreInternals.call(core, CoreCalls.registerService(plugin, "refused:service"),
                room.making(HandleKind.SERVICE_REGISTRATION, plugin, new Object()));
        room.cancel();

        assertInstanceOf(Answer.Refused.class, refused);
        assertEquals(2, handles.entryCount());
        assertThrows(IllegalStateException.class,
                () -> room.add(HandleKind.SERVICE_REGISTRATION, 1, HOST, new Object()));
    }

    @Test
    void aReservationIsUsedOnce() {
        Handles.Reservation room = handles.reserve();
        long registration = ok(CoreInternals.call(core, CoreCalls.registerService(HOST, "once"),
                room.making(HandleKind.SERVICE_REGISTRATION, HOST, new Object())));

        assertThrows(IllegalStateException.class, () -> room.hold(HOST, registration, new Object()));
        assertEquals(0, handles.holdingCount());
    }

    @Test
    void anUnregistrationEndsARegistration() {
        Object provider = new Object();
        long registration = made(CoreCalls.registerService(HOST, "ending"), HandleKind.SERVICE_REGISTRATION, HOST,
                provider);

        ok(core.call(CoreCalls.unregister(registration)));
        HandleEntry ended = handles.end(registration);

        assertSame(provider, ended.object());
        assertNull(handles.find(registration));
        assertEquals(0, handles.entryCount());
    }

    @Test
    void aStoppedWatchEnds() {
        long watch = made(CoreCalls.watchService(HOST, "watched"), HandleKind.WATCH, HOST, new Object());

        ok(core.call(CoreCalls.unregister(watch)));

        assertEquals(HandleKind.WATCH, handles.end(watch).kind());
        assertNull(handles.find(watch));
    }

    @Test
    void aSettledWantEndsWhenItsSettlementIsHeard() {
        Object waiter = new Object();
        List<Object> settledWith = new ArrayList<>();
        core.addListener(event -> {
            if (event instanceof CoreEvent.WantSettledEvent) {
                CoreEvent.WantSettledEvent settled = (CoreEvent.WantSettledEvent) event;
                settledWith.add(handles.end(settled.want()).object());
            }
        });
        long want = made(CoreCalls.wantService(HOST, "settling", WantOptions.unbounded()), HandleKind.WANT, HOST,
                waiter);

        ok(core.call(CoreCalls.registerService(HOST, "settling")));

        assertEquals(Collections.singletonList(waiter), settledWith);
        assertNull(handles.find(want));
    }

    @Test
    void aWithdrawnWantEnds() {
        long want = made(CoreCalls.wantService(HOST, "withdrawn", WantOptions.unbounded()), HandleKind.WANT, HOST,
                new Object());

        ok(core.call(CoreCalls.unregister(want)));

        assertEquals(HandleKind.WANT, handles.end(want).kind());
        assertNull(handles.find(want));
    }

    @Test
    void aReleasedPluginsEntriesEndAndItsOwnEndsWhenItIsForgotten() {
        Manifest manifest = new Manifest("released", 0, Optional.empty(), Collections.emptyList(),
                Collections.singletonList("released:capability"),
                Collections.emptyList(), Collections.emptyList(), Arrays.asList(consumed("released:service"),
                        consumed("released:awaited")), Collections.emptyList(),
                Optional.empty());
        Object record = new Object();
        long plugin = made(CoreCalls.registerManifest(manifest), HandleKind.PLUGIN, Handles.NO_OWNER, record);
        ok(core.call(CoreCalls.openPlugin(plugin, handles.find(plugin).token())));
        long registration = made(CoreCalls.registerService(plugin, "released:service"),
                HandleKind.SERVICE_REGISTRATION, plugin, new Object());
        long implementation = made(CoreCalls.provideCapability(plugin, "released:capability"),
                HandleKind.CAPABILITY_IMPLEMENTATION, plugin, new Object());
        long watch = made(CoreCalls.watchService(plugin, "released:service"), HandleKind.WATCH, plugin,
                new Object());
        long want = made(CoreCalls.wantService(plugin, "released:awaited", WantOptions.unbounded()),
                HandleKind.WANT, plugin, new Object());

        ok(core.call(CoreCalls.releasePlugin(plugin)));
        Set<Long> ended = handles.endOwned(plugin).stream().map(HandleEntry::handle).collect(Collectors.toSet());

        assertEquals(new HashSet<>(Arrays.asList(registration, implementation, watch, want)), ended);
        assertSame(record, handles.find(plugin).object());
        assertEquals(1, handles.entryCount());

        ok(core.call(CoreCalls.forgetPlugin(plugin)));

        assertSame(record, handles.end(plugin).object());
        assertEquals(0, handles.entryCount());
    }

    @Test
    void endingAnOwnersEntriesOfSomeKindsLeavesItsOthers() {
        Manifest manifest = new Manifest("kinds", 0, Optional.empty(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Arrays.asList(consumed("kinds:service"), consumed("kinds:awaited")), Collections.emptyList(),
                        Optional.empty());
        long plugin = made(CoreCalls.registerManifest(manifest), HandleKind.PLUGIN, Handles.NO_OWNER, new Object());
        ok(core.call(CoreCalls.openPlugin(plugin, handles.find(plugin).token())));
        long registration = made(CoreCalls.registerService(plugin, "kinds:service"),
                HandleKind.SERVICE_REGISTRATION, plugin, new Object());
        long want = made(CoreCalls.wantService(plugin, "kinds:awaited", WantOptions.unbounded()),
                HandleKind.WANT, plugin, new Object());

        List<HandleEntry> ended = handles.endOwned(plugin, EnumSet.of(HandleKind.SERVICE_REGISTRATION));

        assertEquals(Collections.singletonList(registration),
                ended.stream().map(HandleEntry::handle).collect(Collectors.toList()));
        assertNull(handles.find(registration));
        assertEquals(HandleKind.WANT, handles.find(want).kind());
        assertEquals(HandleKind.PLUGIN, handles.find(plugin).kind());
    }

    @Test
    void aHandleTheTableDoesNotHoldAnswersNothing() {
        long registration = made(CoreCalls.registerService(HOST, "answered"), HandleKind.SERVICE_REGISTRATION, HOST,
                new Object());
        long unknown = ok(core.call(CoreCalls.registerService(HOST, "unknown")));
        handles.end(registration);

        assertNull(handles.find(unknown));
        assertNull(handles.find(registration));
        assertNull(handles.end(registration));
        assertNull(handles.findByToken(0));
        assertNull(handles.held(HOST, unknown));
    }

    @Test
    void anEndedHandleIsNeverMatchedToANewerObject() {
        Object first = new Object();
        long earlier = made(CoreCalls.registerManifest(Manifest.named("earlier")), HandleKind.PLUGIN,
                Handles.NO_OWNER, first);
        long earlierToken = handles.find(earlier).token();
        ok(core.call(CoreCalls.releasePlugin(earlier)));
        ok(core.call(CoreCalls.forgetPlugin(earlier)));
        handles.end(earlier);

        Object second = new Object();
        long later = made(CoreCalls.registerManifest(Manifest.named("later")), HandleKind.PLUGIN, Handles.NO_OWNER,
                second);

        assertNotEquals(earlier, later);
        assertNull(handles.find(earlier));
        assertNull(handles.findByToken(earlierToken));
        assertSame(second, handles.find(later).object());
        assertSame(second, handles.findByToken(handles.find(later).token()).object());
    }

    @Test
    void aHoldingIsKeptByThePairAndARevocationDropsOnlyThePairItNames() {
        long registration = made(CoreCalls.registerService(HOST, "held"), HandleKind.SERVICE_REGISTRATION, HOST,
                new Object());
        long first = openConsumer("first", "held");
        long second = openConsumer("second", "held");
        Object firstHeld = holdGot(first, "held");
        Object secondHeld = holdGot(second, "held");
        heard.clear();

        ok(core.call(CoreCalls.unregister(registration)));
        List<CoreEvent.RevokedEvent> revocations = revocations();
        CoreEvent.RevokedEvent ofFirst = revocations.stream().filter(revoked -> revoked.plugin() == first)
                .findFirst().get();

        assertSame(firstHeld, handles.revoke(ofFirst));
        assertNull(handles.held(first, registration));
        assertSame(secondHeld, handles.held(second, registration));
        assertEquals(1, handles.holdingCount());
    }

    @Test
    void aRevocationDropsTheHoldingOfAForgottenPlugin() {
        long registration = made(CoreCalls.registerService(HOST, "kept"), HandleKind.SERVICE_REGISTRATION, HOST,
                new Object());
        long plugin = openConsumer("forgotten", "kept");
        ok(core.call(CoreCalls.releasePlugin(plugin)));
        Object lateHeld = holdGot(plugin, "kept");
        heard.clear();

        ok(core.call(CoreCalls.forgetPlugin(plugin)));
        handles.end(plugin);
        CoreEvent.RevokedEvent revoked = revocations().get(0);

        assertEquals(plugin, revoked.plugin());
        assertEquals(registration, revoked.implementation());
        assertNull(handles.find(plugin));
        assertSame(lateHeld, handles.revoke(revoked));
        assertEquals(0, handles.holdingCount());
    }

    @Test
    void droppingAPluginsHoldingsLeavesOthers() {
        made(CoreCalls.registerService(HOST, "dropped"), HandleKind.SERVICE_REGISTRATION, HOST, new Object());
        long first = openConsumer("dropping", "dropped");
        long second = openConsumer("keeping", "dropped");
        holdGot(first, "dropped");
        Object kept = holdGot(second, "dropped");

        handles.dropHoldings(first);

        assertEquals(1, handles.holdingCount());
        assertSame(kept, handles.held(second, ok(core.call(CoreCalls.getService(second, "dropped")))));
    }

    @Test
    void aPluginsPayloadReachesItsRecordWithoutASearch() {
        made(CoreCalls.registerService(HOST, "before-the-payload"), HandleKind.SERVICE_REGISTRATION, HOST,
                new Object());
        Object record = new Object();
        long plugin = made(CoreCalls.registerManifest(Manifest.named("payload")), HandleKind.PLUGIN,
                Handles.NO_OWNER, record);
        long token = handles.find(plugin).token();
        assertNotEquals(0, token);

        ok(core.call(CoreCalls.openPlugin(plugin, token)));
        long payload = ok(CoreProbes.pluginPayload(core, plugin));

        assertEquals(token, payload);
        assertSame(record, handles.findByToken(payload).object());
    }

    @Test
    void lookingUpAllocatesNothing() {
        List<Long> registrations = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            registrations.add(made(CoreCalls.registerService(HOST, "looked-up-" + index),
                    HandleKind.SERVICE_REGISTRATION, HOST, new Object()));
        }
        long[] handlesToFind = registrations.stream().mapToLong(Long::longValue).toArray();
        long[] tokens = registrations.stream().mapToLong(handle -> handles.find(handle).token()).toArray();
        lookUp(handlesToFind, tokens);

        long first = allocatedBytes();
        long second = allocatedBytes();
        long found = lookUp(handlesToFind, tokens);
        long third = allocatedBytes();

        assertEquals(200L * 64 * 3, found);
        long allocated = (third - second) - (second - first);
        assertTrue(allocated < JIT_NOISE_BYTES, "looking up allocated " + allocated + " bytes");
    }

    /**
     * Looks every handle up 200 times by handle, by token and as a holding, answering how many were found.
     *
     * @implNote The lookup test runs this once unmeasured: on JDK 8 the first pass, run alone, allocated a fixed 720
     *     bytes whatever the count, which is the run warming up rather than a lookup allocating.
     */
    private long lookUp(long[] handlesToFind, long[] tokens) {
        long found = 0;
        for (int round = 0; round < 200; round++) {
            for (int index = 0; index < handlesToFind.length; index++) {
                found += handles.find(handlesToFind[index]).handle() == handlesToFind[index] ? 1 : 0;
                found += handles.findByToken(tokens[index]) != null ? 1 : 0;
                found += handles.held(HOST, handlesToFind[index]) == null ? 1 : 0;
            }
        }
        return found;
    }

    private long made(CoreCall<Long> call, HandleKind kind, long owner, Object object) {
        Handles.Reservation room = handles.reserve();
        Answer<Long> answer = CoreInternals.call(core, call, room.making(kind, owner, object));
        room.cancel();
        long handle = ok(answer);
        assertNotNull(handles.find(handle), "no entry was made for " + handle);
        return handle;
    }

    private long openConsumer(String name, String serviceId) {
        Manifest manifest = new Manifest(name, 0, Optional.empty(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(consumed(serviceId)), Collections.emptyList(), Optional.empty());
        long plugin = made(CoreCalls.registerManifest(manifest), HandleKind.PLUGIN, Handles.NO_OWNER, new Object());
        ok(core.call(CoreCalls.openPlugin(plugin, handles.find(plugin).token())));
        return plugin;
    }

    private static Manifest.ServiceWant consumed(String serviceId) {
        return new Manifest.ServiceWant(serviceId, Optional.empty(), Choice.DEFAULT, Choice.DEFAULT,
                Collections.emptyList());
    }

    private Object holdGot(long plugin, String serviceId) {
        Handles.Reservation room = handles.reserve();
        Object handedOver = new Object();
        ok(CoreInternals.call(core, CoreCalls.getService(plugin, serviceId), new CallSteps<>(() -> Optional.empty(),
                registration -> {
                    room.hold(plugin, registration, handedOver);
                    return new Answer.Ok<>(registration);
                })));
        return handedOver;
    }

    private List<CoreEvent.RevokedEvent> revocations() {
        synchronized (heard) {
            return heard.stream().filter(CoreEvent.RevokedEvent.class::isInstance)
                    .map(CoreEvent.RevokedEvent.class::cast).collect(Collectors.toList());
        }
    }

    @BeforeAll
    static void warmTheAllocationCounter() {
        for (int round = 0; round < 20_000; round++) {
            allocatedBytes();
        }
    }

    /** What reading the allocation counter may cost; one escaping object per entry costs 300 times 16 bytes. */
    private static final long COUNTER_READ_BYTES = 1024;

    static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
                .getCurrentThreadAllocatedBytes();
    }
}
