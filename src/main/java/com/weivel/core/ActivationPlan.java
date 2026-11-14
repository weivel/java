package com.weivel.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** The core's activation plan, as {@code weivel_plan_activation} answers it, copied out before its release. */
public final class ActivationPlan {
    private final List<Long> order;
    private final List<Edge> edges;
    private final List<List<Long>> cycles;
    private final List<PluginRefusal> refusals;

    /**
     * Copies every list.
     *
     * @param order the plugins to start, in the order to start them
     * @param edges the provider and consumer pairs between plugins considered for start
     * @param cycles each set of plugins that need each other
     * @param refusals the plugins that will not start, and why
     */
    public ActivationPlan(List<Long> order, List<Edge> edges, List<List<Long>> cycles, List<PluginRefusal> refusals) {
        this.order = Collections.unmodifiableList(new ArrayList<>(order));
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
        List<List<Long>> copiedCycles = new ArrayList<>(cycles.size());
        for (List<Long> cycle : cycles) {
            copiedCycles.add(Collections.unmodifiableList(new ArrayList<>(cycle)));
        }
        this.cycles = Collections.unmodifiableList(copiedCycles);
        this.refusals = Collections.unmodifiableList(new ArrayList<>(refusals));
    }

    /**
     * Answers {@code order}.
     *
     * @return the plugins to start, each after every plugin it has an edge from
     */
    public List<Long> order() {
        return order;
    }

    /**
     * Answers {@code edges}.
     *
     * @return the provider and consumer pairs, in the core's order
     */
    public List<Edge> edges() {
        return edges;
    }

    /**
     * Answers {@code cycles}.
     *
     * @return each cycle's members, which are neither refused nor in the order
     */
    public List<List<Long>> cycles() {
        return cycles;
    }

    /**
     * Answers {@code refusals}.
     *
     * @return the plugins that will not start, in the order the core found them
     */
    public List<PluginRefusal> refusals() {
        return refusals;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ActivationPlan)) {
            return false;
        }
        ActivationPlan that = (ActivationPlan) other;
        return order.equals(that.order) && edges.equals(that.edges) && cycles.equals(that.cycles)
                && refusals.equals(that.refusals);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + order.hashCode();
        hash = 31 * hash + edges.hashCode();
        hash = 31 * hash + cycles.hashCode();
        hash = 31 * hash + refusals.hashCode();
        return hash;
    }

    @Override
    public String toString() {
        return "ActivationPlan[order=" + order + ", edges=" + edges + ", cycles=" + cycles + ", refusals="
                + refusals + "]";
    }

    /** One edge of a plan: its provider activates before its consumer. */
    public static final class Edge {
        private final long provider;
        private final long consumer;

        /**
         * Makes the value.
         *
         * @param provider the plugin that activates first
         * @param consumer the plugin that activates after it
         */
        public Edge(long provider, long consumer) {
            this.provider = provider;
            this.consumer = consumer;
        }

        /**
         * Answers {@code provider}.
         *
         * @return the plugin that activates first
         */
        public long provider() {
            return provider;
        }

        /**
         * Answers {@code consumer}.
         *
         * @return the plugin that activates after it
         */
        public long consumer() {
            return consumer;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Edge)) {
                return false;
            }
            Edge that = (Edge) other;
            return provider == that.provider && consumer == that.consumer;
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(provider) + Long.hashCode(consumer);
        }

        @Override
        public String toString() {
            return "Edge[provider=" + provider + ", consumer=" + consumer + "]";
        }
    }

    /** A plugin a plan will not start, and the core's refusal saying why. */
    public static final class PluginRefusal {
        private final long plugin;
        private final Refusal refusal;

        /**
         * Checks that the refusal is present.
         *
         * @param plugin the plugin
         * @param refusal why it will not start
         */
        public PluginRefusal(long plugin, Refusal refusal) {
            this.plugin = plugin;
            this.refusal = Objects.requireNonNull(refusal, "refusal");
        }

        /**
         * Answers {@code plugin}.
         *
         * @return the plugin
         */
        public long plugin() {
            return plugin;
        }

        /**
         * Answers {@code refusal}.
         *
         * @return why it will not start
         */
        public Refusal refusal() {
            return refusal;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PluginRefusal)) {
                return false;
            }
            PluginRefusal that = (PluginRefusal) other;
            return plugin == that.plugin && refusal.equals(that.refusal);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(plugin) + refusal.hashCode();
        }

        @Override
        public String toString() {
            return "PluginRefusal[plugin=" + plugin + ", refusal=" + refusal + "]";
        }
    }
}
