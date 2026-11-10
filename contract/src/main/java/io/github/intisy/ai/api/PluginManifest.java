package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsInterface;
import io.github.intisy.ai.tsemit.TsOptional;
import java.util.List;
import java.util.Map;

/**
 * The contents of a repo's plugin.json once the repository's identity is applied.
 *
 * @implNote {@code id} and {@code name} come from outside the repository, never from its plugin.json,
 * so a manifest cannot claim another plugin's identity. The TypeScript contract splits this into a
 * source manifest and an extension of it; the emitter does not follow inheritance, so the two are one
 * interface here.
 *
 * @implNote No index signature and no additionalProperties: an author gets excess-property checking
 * while writing one, and the runtime validator still ignores fields it does not know, so a manifest
 * written against a later version loads on today's host.
 */
@TsInterface(data = true)
public interface PluginManifest {
    /**
     * Pointer at the published manifest schema, for an editor's completion and validation.
     *
     * @implNote Declared although nothing reads it, because every manifest in the ecosystem carries
     * it and the published schema accepts it: without it an author writing a manifest literal in
     * TypeScript could not include a field their own plugin.json has.
     * @return the schema URL, or absent when this manifest declares none
     */
    @TsOptional
    String $schema();

    /**
     * The name other plugins import this plugin by, fixed by the plugin itself.
     *
     * @return the import name
     */
    String importName();

    /**
     * The repository identity, for example {@code github:123}, supplied by whoever loaded the manifest.
     *
     * @return the plugin id
     */
    String id();

    /**
     * The repository's name, supplied with the identity.
     *
     * @return the repository name
     */
    String name();

    /**
     * The lowest API major version this plugin needs. A floor, not a build tag.
     *
     * @return the minimum API major version this plugin needs
     */
    int api();

    /**
     * The built module a host imports. Required once `capabilities` is non-empty.
     *
     * @return the entry module path, or absent when this plugin declares no capabilities
     */
    @TsOptional
    String entry();

    /**
     * The name a surface shows instead of the id.
     *
     * @return the display name, or absent when the id is shown instead
     */
    @TsOptional
    String displayName();

    /**
     * Path to a square-viewBox SVG mark, relative to the repo root.
     *
     * @return the icon path, or absent when this plugin has no mark
     */
    @TsOptional
    String icon();

    /**
     * Further marks this repo ships, each keyed by the id of the thing it belongs to.
     *
     * @implNote One repo can contribute several named things, and a single repo-level {@code icon}
     * cannot serve them. Keyed by id rather than by kind, so this package carries the marks without
     * learning what any of the ids name.
     * @return the ids mapped to their mark paths, or absent when this plugin ships only its own icon
     */
    @TsOptional
    Map<String, String> icons();

    /**
     * Host-facing abilities this plugin provides at activation, declared statically so a host can answer what it can do without executing it.
     *
     * @return the capability ids this plugin provides, or absent when it provides none
     */
    @TsOptional
    List<String> capabilities();

    /**
     * The applications this artifact is for. Absent means every application.
     *
     * @return the application ids this artifact targets, or absent when it is for every application
     * @implNote An artifact narrows itself deliberately and is never narrowed by omission, which is
     * what keeps this additive: every manifest written before the field existed stays loadable
     * everywhere. A host refuses only when both sides name applications and the two sets are
     * disjoint.
     */
    @TsOptional
    List<String> targets();

    /**
     * What this manifest describes. Absent means a plugin; a loader is a host.
     *
     * @return the kind, or absent when this is a plugin
     */
    @TsOptional
    ManifestKind kind();

    /**
     * The install method this listing is a provider for, for example {@code npm}.
     *
     * @return the method id this listing provides, or absent when it provides none
     */
    @TsOptional
    String installMethod();

    /**
     * The package this item is under each install method, keyed by method id.
     *
     * @implNote Each value is a package name string, or an object carrying {@code name} and
     * optionally {@code options}; the published schema checks which, because Java has no union for
     * a map's values.
     * @return the method ids mapped to their package, or absent when none is declared
     */
    @TsOptional
    Map<String, Object> packages();

    /**
     * The executables this host puts on the user's PATH.
     *
     * @return the PATH commands this host asks for, or absent when it asks for none
     */
    @TsOptional
    ManifestPathCommands pathCommands();

    /**
     * The install methods this listing supports, by method id.
     *
     * @return the method ids, or absent when the listing declares none
     */
    @TsOptional
    List<String> methods();

    /**
     * The inter-plugin contract.
     *
     * @return the services this plugin provides and consumes, or absent when it declares none
     */
    @TsOptional
    ManifestServices services();

    /**
     * Slash commands this plugin contributes, which a host deploys without importing it.
     *
     * @return the commands this plugin contributes, or absent when it contributes none
     */
    @TsOptional
    List<ManifestCommand> commands();

    /**
     * This plugin's settings as it ships them.
     *
     * @return the plugin's config declaration, or absent when it has no settings
     */
    @TsOptional
    ManifestConfig config();

    /**
     * Where this plugin keeps state that is not named after it.
     *
     * @return the plugin's data paths, or absent when it declares none
     */
    @TsOptional
    ManifestData data();

    /**
     * The plugins this plugin needs installed beside it, each carrying exactly one of {@code id} or {@code name}.
     *
     * @return the dependencies, or absent when this plugin needs none
     */
    @TsOptional
    List<ManifestDependency> dependencies();

    /**
     * Declared permissions, surfaced at install and in dashboards. Not sandbox-enforced.
     *
     * @return the declared permission ids, or absent when this plugin declares none
     */
    @TsOptional
    List<String> permissions();

    /**
     * Which optional lifecycle hooks the entry exports.
     *
     * @return the lifecycle declaration, or absent when this plugin exports no optional hook
     */
    @TsOptional
    ManifestLifecycle lifecycle();

    /**
     * How the repo is published to npm.
     *
     * @return the publish declaration, or absent when this plugin uses the ecosystem default
     */
    @TsOptional
    ManifestPublish publish();

    /**
     * Repository metadata.
     *
     * @return the repo metadata, or absent when this manifest declares none
     */
    @TsOptional
    RepoMeta repo();

    /**
     * What this plugin contributes to a host's catalog of installable things.
     *
     * @return the marketplace declaration, or absent when this plugin contributes none
     */
    @TsOptional
    ManifestMarketplace marketplace();

    /**
     * Settings a user can change, keyed by setting key.
     *
     * @return the settings, or absent when this plugin offers none
     */
    @TsOptional
    UiSettings settings();

    /**
     * Operations a host offers the user, each a call into one of this plugin's own services.
     *
     * @return the actions keyed by action id, or absent when this plugin offers none
     */
    @TsOptional
    Map<String, ActionDeclaration> actions();

    /**
     * Screens a host draws, keyed by view id, each a flat list of nodes that name their parent.
     *
     * @return the views keyed by view id, or absent when this plugin draws none
     */
    @TsOptional
    Map<String, ViewDeclaration> views();

    /**
     * The app this repo is the loader for, declared by the app's own project.
     *
     * @implNote Present only on a repo that IS an app's loader, which is what makes "whose loader is
     * this" answerable from the manifest alone, with no consumer naming a plugin.
     * @return the app this repo is the loader for, or absent when it is not an app's loader
     */
    @TsOptional
    AppDescriptor app();
}
