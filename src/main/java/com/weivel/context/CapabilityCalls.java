package com.weivel.context;

import com.weivel.core.Answer;
import com.weivel.core.CapabilityImplementation;
import com.weivel.core.CoreCalls;
import com.weivel.core.FailureKind;
import com.weivel.handles.HandleKind;
import com.weivel.handles.Handles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** A context's calls about capabilities, each taking the room it needs before the core is asked. */
final class CapabilityCalls {
    private static final int FIRST_LISTING_ROOM = 4;

    private CapabilityCalls() {
    }

    static Answer<Void> provide(ContextState state, PluginContext context, String id, Object implementation) {
        Handles.Reservation room = state.handles.reserve();
        Records.Implementation record = new Records.Implementation(id, implementation);
        Answer<Long> answer = state.call(context, CoreCalls.provideCapability(context.plugin(), id), provided -> {
            room.add(HandleKind.CAPABILITY_IMPLEMENTATION, provided, context.plugin(), record);
            return new Answer.Ok<>(provided);
        });
        room.cancel();
        return answer instanceof Answer.Ok ? new Answer.Ok<>(null) : Answers.retyped(answer);
    }

    /**
     * Lists the implementations, holding each for the plugin in room taken before the call.
     *
     * @implNote The count is not known before the core answers, so a listing longer than the room taken holds
     * nothing and is asked again with room for all of it; the core answers it afresh, and a plugin holds an
     * implementation once however often it is handed it.
     */
    static Answer<List<Object>> implementations(ContextState state, PluginContext context, String id) {
        int roomWanted = FIRST_LISTING_ROOM;
        while (true) {
            Listing listing = new Listing(state.handles, roomWanted);
            Answer<List<CapabilityImplementation>> answer = state.call(context,
                    CoreCalls.allCapabilities(context.plugin(), id), listed -> {
                        listing.hold(state, context.plugin(), listed);
                        return new Answer.Ok<>(listed);
                    });
            listing.giveBackUnusedRoom();
            if (!(answer instanceof Answer.Ok)) {
                return Answers.retyped(answer);
            }
            if (listing.shortOf > 0) {
                roomWanted = listing.shortOf;
                continue;
            }
            if (listing.unknown > 0) {
                state.report(FailureKind.OBJECT_UNKNOWN, "the core listed an implementation the layer holds no "
                        + "object for, which was left out", Optional.empty(), Optional.empty());
            }
            return new Answer.Ok<>(Collections.unmodifiableList(new ArrayList<>(listing.found)));
        }
    }

    static Answer<Watching> watch(ContextState state, PluginContext context, String id, CapabilityWatcher watcher) {
        Handles.Reservation room = state.handles.reserve();
        Answer<Long> answer = state.call(context, CoreCalls.watchCapability(context.plugin(), id), watch -> {
            room.add(HandleKind.WATCH, watch, context.plugin(), new Records.CapabilityWatch(watcher));
            return new Answer.Ok<>(watch);
        });
        room.cancel();
        return answer instanceof Answer.Ok
                ? new Answer.Ok<>(new Watching(state, context, ((Answer.Ok<Long>) answer).value()))
                : Answers.retyped(answer);
    }

    /** One asking of the core for a listing: the room taken for it, and what its answered step found. */
    private static final class Listing {
        private final List<Handles.Reservation> rooms = new ArrayList<>();
        private final List<Object> found;
        private int used;
        private int unknown;
        private int shortOf;

        Listing(Handles handles, int roomWanted) {
            found = new ArrayList<>(roomWanted);
            for (int index = 0; index < roomWanted; index++) {
                rooms.add(handles.reserve());
            }
        }

        /** Run under the layer lock, once the core answered: needs no memory beyond the room already taken. */
        void hold(ContextState state, long plugin, List<CapabilityImplementation> listed) {
            if (listed.size() > rooms.size()) {
                shortOf = listed.size();
                return;
            }
            for (CapabilityImplementation each : listed) {
                Optional<Records.Implementation> record = state.recordOf(each.implementation(),
                        HandleKind.CAPABILITY_IMPLEMENTATION, Records.Implementation.class);
                if (!record.isPresent()) {
                    unknown++;
                    continue;
                }
                rooms.get(used++).hold(plugin, each.implementation(), record.get().implementation());
                found.add(record.get().implementation());
            }
        }

        void giveBackUnusedRoom() {
            rooms.forEach(Handles.Reservation::cancel);
        }
    }
}
