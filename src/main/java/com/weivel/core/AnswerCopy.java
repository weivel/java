package com.weivel.core;

import static com.weivel.core.CoreLayouts.offsetOf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Copies an answer the core filled into a caller's struct out into Java values, before the struct's release. */
final class AnswerCopy {
    private AnswerCopy() {
    }

    static List<String> serviceIds(NativeRegion list) {
        long ids = offsetOf(CoreLayouts.SERVICE_LIST, "ids");
        int count = list.getInt(ids + offsetOf(CoreLayouts.STRINGS, "count"));
        if (count == 0) {
            return Collections.emptyList();
        }
        long addressSize = Layout.ADDRESS.byteSize();
        NativeRegion items = list.at(list.getAddress(ids + offsetOf(CoreLayouts.STRINGS, "items")),
                addressSize * count);
        List<String> copied = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            copied.add(items.textAt(items.getAddress(index * addressSize)));
        }
        return Collections.unmodifiableList(copied);
    }

    /** @implNote Every array of a plan is of the kind whose elements never grow, so each is stepped at its layout. */
    static ActivationPlan plan(NativeRegion plan) {
        Layout layout = CoreLayouts.ACTIVATION_PLAN;
        List<Long> order = handles(plan, plan.getAddress(offsetOf(layout, "order")),
                plan.getInt(offsetOf(layout, "order_count")));
        int edgeCount = plan.getInt(offsetOf(layout, "edge_count"));
        NativeRegion edges = elements(plan, offsetOf(layout, "edges"), CoreLayouts.EDGE, edgeCount);
        List<ActivationPlan.Edge> copiedEdges = new ArrayList<>(edgeCount);
        for (int index = 0; index < edgeCount; index++) {
            long base = index * CoreLayouts.EDGE.byteSize();
            copiedEdges.add(new ActivationPlan.Edge(edges.getLong(base + offsetOf(CoreLayouts.EDGE, "provider")),
                    edges.getLong(base + offsetOf(CoreLayouts.EDGE, "consumer"))));
        }
        int cycleCount = plan.getInt(offsetOf(layout, "cycle_count"));
        NativeRegion cycles = elements(plan, offsetOf(layout, "cycles"), CoreLayouts.CYCLE, cycleCount);
        List<List<Long>> copiedCycles = new ArrayList<>(cycleCount);
        for (int index = 0; index < cycleCount; index++) {
            long base = index * CoreLayouts.CYCLE.byteSize();
            copiedCycles.add(handles(plan, cycles.getAddress(base + offsetOf(CoreLayouts.CYCLE, "members")),
                    cycles.getInt(base + offsetOf(CoreLayouts.CYCLE, "count"))));
        }
        return new ActivationPlan(order, copiedEdges, copiedCycles, planRefusals(plan));
    }

    private static List<ActivationPlan.PluginRefusal> planRefusals(NativeRegion plan) {
        Layout layout = CoreLayouts.ACTIVATION_PLAN;
        Layout element = CoreLayouts.PLUGIN_REFUSAL;
        Layout subjectElement = CoreLayouts.REFUSAL_SUBJECT;
        int count = plan.getInt(offsetOf(layout, "refusal_count"));
        NativeRegion refusals = elements(plan, offsetOf(layout, "refusals"), element, count);
        NativeRegion subjects = elements(plan, offsetOf(layout, "refusal_subjects"), subjectElement, count);
        List<ActivationPlan.PluginRefusal> copied = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long base = index * element.byteSize();
            long refusal = base + offsetOf(element, "refusal");
            long subjectBase = index * subjectElement.byteSize();
            long subject = subjects.getAddress(subjectBase + offsetOf(subjectElement, "subject"));
            copied.add(new ActivationPlan.PluginRefusal(refusals.getLong(base + offsetOf(element, "plugin")),
                    new Refusal(refusals.getInt(refusal + offsetOf(CoreLayouts.REFUSAL, "code")),
                            textOrEmpty(refusals, refusals.getAddress(refusal + offsetOf(CoreLayouts.REFUSAL,
                                    "detail"))),
                            textOrEmpty(refusals, refusals.getAddress(refusal + offsetOf(CoreLayouts.REFUSAL,
                                    "fix"))),
                            subject == 0 ? Optional.<String>empty() : Optional.of(subjects.textAt(subject)),
                            subjects.getInt(subjectBase + offsetOf(subjectElement, "has_fix")) != 0)));
        }
        return copied;
    }

    private static NativeRegion elements(NativeRegion owner, long pointerOffset, Layout element, int count) {
        return owner.at(owner.getAddress(pointerOffset), element.byteSize() * Math.max(count, 1));
    }

    private static List<Long> handles(NativeRegion owner, long address, int count) {
        if (count == 0) {
            return Collections.emptyList();
        }
        long handleSize = Layout.LONG.byteSize();
        NativeRegion items = owner.at(address, handleSize * count);
        List<Long> copied = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            copied.add(items.getLong(index * handleSize));
        }
        return copied;
    }

    private static String textOrEmpty(NativeRegion memory, long address) {
        return address == 0 ? "" : memory.textAt(address);
    }

    /** @implNote Stepped at the first element's {@code size}, the core's, reading only the fields both know. */
    static List<CapabilityImplementation> capabilities(NativeRegion list) {
        Layout element = CoreLayouts.CAPABILITY_IMPLEMENTATION;
        int count = list.getInt(offsetOf(CoreLayouts.CAPABILITY_LIST, "count"));
        if (count == 0) {
            return Collections.emptyList();
        }
        long itemsAddress = list.getAddress(offsetOf(CoreLayouts.CAPABILITY_LIST, "items"));
        NativeRegion first = list.at(itemsAddress, element.byteSize());
        long stride = Integer.toUnsignedLong(first.getInt(offsetOf(element, "size")));
        if (stride < element.byteSize()) {
            throw new IllegalStateException("a capability element smaller than the fields the layer reads");
        }
        NativeRegion items = list.at(itemsAddress, stride * count);
        List<CapabilityImplementation> copied = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long base = index * stride;
            copied.add(new CapabilityImplementation(items.getLong(base + offsetOf(element, "implementation")),
                    items.getLong(base + offsetOf(element, "provider"))));
        }
        return Collections.unmodifiableList(copied);
    }
}
