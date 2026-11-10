package io.github.intisy.ai.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Who a repository is, supplied by whatever loaded it and never by its own manifest.
 *
 * @implNote The grammar ends in a negative lookahead rather than {@code $}, because {@code $} also
 * matches before a final line terminator in Java and not in JavaScript, and a one-character
 * difference in what counts as an id is the kind of disagreement the two engines must not have.
 */
public final class Identity {

    /** The grammar of an id: {@code local:<name>}, or a forge, an optional host and a numeric repository id, then path segments. */
    public static final String ID_PATTERN =
            "^(local:[^/\\s]+|[a-z][a-z0-9-]*(@[a-z0-9.-]+)?:[0-9]+)(/[a-z0-9._-]+)*(?![\\s\\S])";

    private static final Pattern ID = Pattern.compile(ID_PATTERN);

    private final String id;
    private final String name;

    /**
     * @param id the repository identity, for example {@code github:123}
     * @param name the repository's name
     */
    public Identity(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /** @return the repository identity */
    public String getId() {
        return id;
    }

    /** @return the repository's name */
    public String getName() {
        return name;
    }

    /**
     * @param value a string offered as a repository identity
     * @return true when it is {@code local:<name>}, or a forge (with an optional host) and a numeric repository id, then any path segments
     */
    public static boolean isValidId(String value) {
        return value != null && ID.matcher(value).find();
    }

    /**
     * @param importName the name the plugin imports itself by
     * @return the identity of a plugin no forge supplied one for
     */
    public static Identity local(String importName) {
        return new Identity("local:" + importName, importName);
    }

    /**
     * The namespaced ids a manifest consumes that name no provider.
     *
     * @param manifest a parsed manifest, whose {@code services.consumes} holds ids or objects carrying {@code id} and {@code provider}
     * @return the unbound ids in the manifest's order
     * @implNote A bare id names a host or shared service, which has no plugin to bind to, so it is never reported.
     */
    public static List<String> unboundServices(Object manifest) {
        List<String> unbound = new ArrayList<String>();
        for (Object entry : consumed(manifest)) {
            ServiceRequirement requirement = ServiceRequirement.of(entry);
            if (requirement.getId().indexOf(':') >= 0 && requirement.getProvider() == null) {
                unbound.add(requirement.getId());
            }
        }
        return unbound;
    }

    private static List<?> consumed(Object manifest) {
        Object services = manifest instanceof Map ? ((Map<?, ?>) manifest).get("services") : null;
        Object consumes = services instanceof Map ? ((Map<?, ?>) services).get("consumes") : null;
        return consumes instanceof List ? (List<?>) consumes : new ArrayList<Object>();
    }
}
