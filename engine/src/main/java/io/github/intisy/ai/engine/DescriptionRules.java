package io.github.intisy.ai.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks rule V3 of weivel/model's VALIDATION.md: every reference in a manifest's actions and views
 * resolves.
 *
 * @implNote Runs only on a manifest the schema accepted, so every member present has the shape the
 * schema gave it. An absent {@code settings}, {@code actions}, {@code services} or
 * {@code services.provides} is an empty set, so a reference into it is refused rather than crashing.
 */
public final class DescriptionRules {

    private DescriptionRules() {
    }

    /**
     * @param manifest a parsed plugin.json the schema has already accepted
     * @return every unresolved reference, actions first, then views in key order, nodes by index
     */
    public static List<SchemaIssue> issues(Object manifest) {
        Map<String, Object> description = mapOf(manifest);
        Set<String> providedServices = providedServiceIds(description);
        List<SchemaIssue> issues = new ArrayList<SchemaIssue>();
        for (Map.Entry<String, Object> action : mapOf(description.get("actions")).entrySet()) {
            Object service = mapOf(mapOf(action.getValue()).get("call")).get("service");
            unprovidedService(issues, "actions." + action.getKey() + ".call.service", service, providedServices);
        }
        for (Map.Entry<String, Object> view : mapOf(description.get("views")).entrySet()) {
            List<Object> nodes = listOf(mapOf(view.getValue()).get("nodes"));
            for (int index = 0; index < nodes.size(); index++) {
                nodeIssues(issues, description, providedServices, "views." + view.getKey() + ".nodes[" + index + "]", nodes, index);
            }
        }
        return issues;
    }

    private static void nodeIssues(List<SchemaIssue> issues, Map<String, Object> description, Set<String> providedServices,
            String base, List<Object> nodes, int index) {
        Map<String, Object> node = mapOf(nodes.get(index));
        Object id = node.get("id");
        if (isEarlierId(nodes, index, id)) {
            issues.add(new SchemaIssue(base + ".id", "\"" + id + "\" is already the id of an earlier node in this view",
                    "give each node in a view its own id"));
        }
        Object parent = node.get("parent");
        if (parent != null && !isEarlierId(nodes, index, parent)) {
            issues.add(new SchemaIssue(base + ".parent", "\"" + parent + "\" is not the id of an earlier node in this view",
                    "name the id of a node listed before this one"));
        }
        unresolvedName(issues, base + ".action", node.get("action"), description.get("actions"), "actions");
        unresolvedName(issues, base + ".field", node.get("field"), description.get("settings"), "settings");
        Object bind = node.get("bind");
        if (bind != null) {
            unprovidedService(issues, base + ".bind.service", mapOf(bind).get("service"), providedServices);
        }
    }

    private static boolean isEarlierId(List<Object> nodes, int index, Object id) {
        for (int earlier = 0; earlier < index; earlier++) {
            if (id.equals(mapOf(nodes.get(earlier)).get("id"))) {
                return true;
            }
        }
        return false;
    }

    private static void unresolvedName(List<SchemaIssue> issues, String path, Object name, Object declared, String members) {
        if (name == null || mapOf(declared).containsKey(name)) {
            return;
        }
        issues.add(new SchemaIssue(path, "\"" + name + "\" is not one of this manifest's " + members,
                "name one of this manifest's " + members));
    }

    private static void unprovidedService(List<SchemaIssue> issues, String path, Object service, Set<String> providedServices) {
        if (providedServices.contains(service)) {
            return;
        }
        issues.add(new SchemaIssue(path, "\"" + service + "\" is not a service this plugin provides",
                "bind to a service this plugin provides"));
    }

    private static Set<String> providedServiceIds(Map<String, Object> description) {
        Set<String> ids = new HashSet<String>();
        for (Object entry : listOf(mapOf(description.get("services")).get("provides"))) {
            ids.add(ServiceRequirement.idOf(entry));
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.<String, Object>emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listOf(Object value) {
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }
}
