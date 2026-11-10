package io.github.intisy.ai.engine;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The schema of plugin.json.
 *
 * @implNote No object declares additionalProperties as absent-meaning-closed: an unknown field is
 * ignored, which is what lets a manifest written against a later version of this package load on
 * today's host. Marks beyond the repo's own live in {@code icons}, keyed by the id of whatever wears
 * them, so this package carries them without learning what any id names: a block keyed by CATEGORY
 * would name one, which is what it must not do.
 * @implNote This is the second home of every manifest field. The first is the contract interface an
 * author writes against; a field added to one and not the other passes its own repo's build and
 * fails the validator, which is what {@code manifest-contract-drift} exists to catch.
 */
public final class ManifestSchema {

    /** Canonical URL of the vocabulary, served by weivel/model's GitHub Pages. */
    public static final String SCHEMA_ID = "https://weivel.github.io/model/plugin.schema.json";

    /** Draft the published schema declares, which no validator in this package reads. */
    public static final String DRAFT = "http://json-schema.org/draft-07/schema#";

    private static final String KEY_PATTERN = "^[a-z][A-Za-z0-9]*(?![\\s\\S])";

    private static final String ICON_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*(?![\\s\\S])";

    private ManifestSchema() {
    }

    /** @return the schema every parsed manifest is checked against */
    public static JsonSchema get() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("$schema", described(JsonSchema.ofType("string"),
                "Pointer at the published manifest schema, for an editor's completion and validation."));

        JsonSchema importName = described(JsonSchema.ofType("string"),
                "The name other plugins import this plugin by. It is fixed by the plugin, and its identity as a repository is supplied from outside the manifest.");
        importName.setPattern("^(?!(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:[\\.]|(?![\\s\\S])))[a-z0-9]+([-.][a-z0-9]+)*(?![\\s\\S])");
        importName.setFix("use lowercase words joined by single hyphens or dots, for example \"config-ledger\" or \"api.http\", and not a Windows reserved name, with or without a dot after it (con, prn, aux, nul, com1 to com9, lpt1 to lpt9)");
        properties.put("importName", importName);

        JsonSchema api = described(JsonSchema.ofType("integer"), "The lowest API major version this plugin needs. A floor, not a build tag.");
        api.setMinimum(Integer.valueOf(1));
        api.setFix("set \"api\" to the lowest API major version this plugin needs, for example 1");
        properties.put("api", api);

        JsonSchema entry = described(JsonSchema.ofType("string"), "The built module a host imports. Required once capabilities are declared.");
        entry.setPattern("^(?![\\\\/])(?![A-Za-z]:)(?![\\s\\S]*[\\u0000-\\u001f\\u007f-\\u009f\\u2028\\u2029])(?!(?:[\\s\\S]*[\\\\/])?\\.\\.(?:[\\\\/]|(?![\\s\\S])))");
        entry.setFix("point \"entry\" at the built module a host imports, inside the repo, for example \"dist/index.js\", with no leading slash, drive letter, control character or ..");
        properties.put("entry", entry);

        properties.put("displayName", described(JsonSchema.ofType("string"), "The name a surface shows instead of the id."));
        properties.put("icon", described(JsonSchema.ofType("string"), "Path to a square-viewBox SVG mark, relative to the repo root."));

        JsonSchema icons = described(JsonSchema.ofType("object"),
                "Further marks this repo ships, each keyed by the id of the thing it belongs to.");
        icons.setAdditionalProperties(described(JsonSchema.ofType("string"),
                "Path to a square-viewBox SVG mark, relative to the repo root."));
        properties.put("icons", icons);

        JsonSchema capabilities = described(JsonSchema.ofType("array"), "Host-facing abilities this plugin provides at activation.");
        capabilities.setItems(JsonSchema.ofType("string"));
        capabilities.setUniqueItems(Boolean.TRUE);
        capabilities.setFix("list capability ids as strings, for example [\"provider\", \"screens\"]");
        properties.put("capabilities", capabilities);

        JsonSchema targets = described(JsonSchema.ofType("array"),
                "The spisor applications this artifact is for. Absent means every application.");
        targets.setItems(JsonSchema.ofType("string"));
        targets.setFix("list the application ids this is built for, for example [\"forebay\"], or remove \"targets\" to mean every application");
        properties.put("targets", targets);

        properties.put("kind", kind());
        properties.put("installMethod", installMethod());
        properties.put("packages", packages());
        properties.put("pathCommands", pathCommands());
        properties.put("methods", methods());
        properties.put("services", services());
        properties.put("commands", commands());
        properties.put("config", config());
        properties.put("data", data());

        properties.put("dependencies", dependencies());

        JsonSchema permissions = described(JsonSchema.ofType("array"), "Declared permissions, surfaced at install and in dashboards.");
        permissions.setItems(JsonSchema.ofType("string"));
        permissions.setUniqueItems(Boolean.TRUE);
        properties.put("permissions", permissions);

        properties.put("lifecycle", lifecycle());
        properties.put("publish", publish());
        properties.put("repo", repo());
        properties.put("marketplace", marketplace());
        properties.put("settings", uiSettings());
        properties.put("actions", actions());
        properties.put("views", views());
        properties.put("app", app());

        JsonSchema root = described(JsonSchema.ofType("object"), "The single machine-readable description of a plugin, whichever application it targets.");
        root.setSchemaDraft(DRAFT);
        root.setSchemaId(SCHEMA_ID);
        root.setTitle("weivel plugin manifest");
        root.setRequired(Arrays.asList("importName", "api"));
        root.setProperties(properties);
        root.setIf(capabilitiesDeclared());
        root.setThen(entryNamed());
        return root;
    }

    private static JsonSchema capabilitiesDeclared() {
        JsonSchema atLeastOne = JsonSchema.ofType("array");
        atLeastOne.setMinItems(Integer.valueOf(1));
        return requiring("capabilities", atLeastOne);
    }

    private static JsonSchema entryNamed() {
        JsonSchema nonEmpty = JsonSchema.ofType("string");
        nonEmpty.setMinLength(Integer.valueOf(1));
        nonEmpty.setFix("capabilities are declared, so add \"entry\" naming the module that provides them, for example \"dist/index.js\"");
        return requiring("entry", nonEmpty);
    }

    private static JsonSchema requiring(String key, JsonSchema shape) {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put(key, shape);
        JsonSchema object = JsonSchema.ofType("object");
        object.setRequired(Arrays.asList(key));
        object.setProperties(properties);
        return object;
    }

    private static JsonSchema dependencies() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("importName", described(JsonSchema.ofType("string"), "The name this plugin imports the dependency by."));
        JsonSchema id = described(JsonSchema.ofType("string"), "The repository identity of the dependency, which survives a rename.");
        id.setPattern(Identity.ID_PATTERN);
        id.setFix("write the dependency id as a repository id, for example \"github:123\" or \"gitea@git.example.com:12/runtime\"");
        properties.put("id", id);
        properties.put("name", dependencyName());
        properties.put("commit", described(JsonSchema.ofType("string"), "The commit the dependency is pinned to."));
        properties.put("sha256", described(JsonSchema.ofType("string"), "The SHA-256 digest the dependency's artifact must match."));
        JsonSchema dependency = described(JsonSchema.ofType("object"), "One plugin this plugin needs, named by exactly one of id or name.");
        dependency.setProperties(properties);
        dependency.setRequired(Arrays.asList("importName"));
        dependency.setIf(requiring("id", JsonSchema.ofType("string")));
        dependency.setThen(forbidding("name"));
        dependency.setElse(requiring("name", JsonSchema.ofType("string")));
        JsonSchema dependencies = described(JsonSchema.ofType("array"),
                "The plugins this plugin needs installed beside it, each named by the import name it is imported by and located by a repository id or an owner/name.");
        dependencies.setItems(dependency);
        dependencies.setFix("list each dependency as an object carrying \"importName\" and exactly one of \"id\" or \"name\"");
        return dependencies;
    }

    private static JsonSchema dependencyName() {
        JsonSchema name = described(JsonSchema.ofType("string"),
                "The owner and name of the dependency's repository, for a repository whose id is not known. A name can be taken by someone else, so the host warns about it.");
        name.setPattern("^[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9._-]+(?![\\s\\S])");
        name.setFix("write the dependency name as owner/name, for example \"spisor/runtime\"; a dependency carries an id or a name, never both");
        return name;
    }

    private static JsonSchema forbidding(String key) {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put(key, JsonSchema.never());
        JsonSchema object = JsonSchema.ofType("object");
        object.setProperties(properties);
        return object;
    }

    private static JsonSchema services() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        JsonSchema provides = described(JsonSchema.ofType("array"),
                "Service ids this plugin registers, each namespaced by its own id or a well-known bare id.");
        provides.setItems(entryOf(offered(), "write the service id as a string, for example \"identity:accounts\", or an object carrying \"id\" and \"attributes\""));
        provides.setFix("list service ids as strings, for example [\"identity:accounts\"], or objects carrying \"id\" and \"attributes\"");
        properties.put("provides", provides);
        JsonSchema consumes = described(JsonSchema.ofType("array"), "Service ids this plugin asks for."
                + " A bare string asks for it optionally, which is what every manifest written before this object form meant.");
        consumes.setItems(entryOf(required(), "write the service id as a string, for example \"accounts\", or an object carrying \"id\" and what its absence costs"));
        consumes.setFix("list service ids as strings, for example [\"accounts\"], or objects carrying \"id\" and what its absence costs");
        properties.put("consumes", consumes);
        JsonSchema services = described(JsonSchema.ofType("object"),
                "The inter-plugin contract: what this plugin offers other plugins, and what it asks of them.");
        services.setProperties(properties);
        return services;
    }

    /**
     * @implNote The fix is on the ENTRY rather than only on the array, because the two failures are
     * different: a field that is not a list at all needs the array's instruction, and one bad entry
     * in a good list needs this one. The array's fix is what a reader sees for the first and never
     * for the second.
     */
    private static JsonSchema entryOf(JsonSchema object, String fix) {
        JsonSchema entry = JsonSchema.anyOf(Arrays.asList(JsonSchema.ofType("string"), object));
        entry.setFix(fix);
        return entry;
    }

    private static JsonSchema offered() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("id", described(JsonSchema.ofType("string"), "The service id this plugin registers."));
        properties.put("attributes", keyedNumbers(
                "What this service claims, keyed by attribute name, until the live service reports its own.",
                "One advertised value, in the units its attribute name names."));
        JsonSchema offered = described(JsonSchema.ofType("object"),
                "One service this plugin registers, with what it advertises.");
        offered.setProperties(properties);
        offered.setRequired(Arrays.asList("id"));
        return offered;
    }

    private static JsonSchema required() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("id", described(JsonSchema.ofType("string"), "The service id this plugin asks for."));
        JsonSchema provider = described(JsonSchema.ofType("string"),
                "The repository identity of the plugin this plugin takes the service from. Absent means any provider of the service id.");
        provider.setPattern(Identity.ID_PATTERN);
        provider.setFix("write the provider as a repository id, for example \"github:123\" or \"gitea@git.example.com:12/runtime\"");
        properties.put("provider", provider);
        properties.put("optional", described(JsonSchema.ofType("boolean"),
                "Whether this plugin still starts when nothing provides the service."
                        + " Absent means true, which is what a bare string means."));
        properties.put("degradable", described(JsonSchema.ofType("boolean"),
                "Whether this plugin accepts a service that never satisfies its constraints once the budget is spent."
                        + " Absent means false, and it never excuses an absence."));
        properties.put("constraints", keyedNumbers(
                "What this plugin needs of the service, keyed by constraint name, compared against what the service advertises.",
                "One required value, in the units its constraint name names."));
        JsonSchema required = described(JsonSchema.ofType("object"),
                "One service this plugin asks for, and what its absence or its shortfall costs.");
        required.setProperties(properties);
        required.setRequired(Arrays.asList("id"));
        return required;
    }

    private static JsonSchema keyedNumbers(String description, String valueDescription) {
        JsonSchema keyed = described(JsonSchema.ofType("object"), description);
        keyed.setAdditionalProperties(described(JsonSchema.ofType("number"), valueDescription));
        return keyed;
    }

    private static JsonSchema kind() {
        JsonSchema kind = described(JsonSchema.ofType("string"),
                "What this manifest describes. Absent means a plugin; a loader is a host.");
        kind.setEnumValues(Arrays.asList("plugin", "host"));
        kind.setFix("set \"kind\" to \"plugin\" or \"host\", or remove it to mean a plugin");
        return kind;
    }

    private static JsonSchema installMethod() {
        JsonSchema installMethod = described(JsonSchema.ofType("string"),
                "The install method this listing is a provider for, for example npm. Absent means it provides none.");
        installMethod.setMinLength(Integer.valueOf(1));
        installMethod.setFix("set \"installMethod\" to the id of the package manager this listing provides, for example \"npm\"");
        return installMethod;
    }

    private static JsonSchema packages() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("name", described(JsonSchema.ofType("string"), "The package name under this method."));
        properties.put("options", described(JsonSchema.ofType("object"),
                "Method-specific options, which only that method's provider reads."));
        JsonSchema withOptions = described(JsonSchema.ofType("object"),
                "A package name with the options its method takes, for example a registry or a tap.");
        withOptions.setRequired(Arrays.asList("name"));
        withOptions.setProperties(properties);
        JsonSchema entry = described(JsonSchema.anyOf(Arrays.asList(JsonSchema.ofType("string"), withOptions)),
                "The package name under one method, or the name with that method's options.");
        entry.setFix("write the package as a name string, for example \"@scope/tool\", or an object carrying \"name\" and optionally \"options\"");
        JsonSchema packages = described(JsonSchema.ofType("object"),
                "The package this item is under each install method, keyed by method id.");
        packages.setAdditionalProperties(entry);
        packages.setFix("map each install method id to a package name string, or to an object carrying \"name\" and optionally \"options\", for example {\"npm\": \"@scope/tool\"}");
        return packages;
    }

    private static JsonSchema commandName() {
        JsonSchema name = JsonSchema.ofType("string");
        name.setPattern("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}(?![\\s\\S])");
        name.setFix("name each command as one portable file name: a letter or digit, then letters, digits, \".\", \"_\" or \"-\", at most 64 characters");
        return name;
    }

    private static JsonSchema pathCommands() {
        JsonSchema requested = described(JsonSchema.ofType("array"),
                "The command names this host asks for, which a name already held may turn into a fallback.");
        requested.setItems(commandName());
        requested.setUniqueItems(Boolean.TRUE);
        JsonSchema minimum = described(JsonSchema.ofType("integer"),
                "How many of its commands this host must keep: 1 means it cannot be left with none, 0 means it can. Absent means 0.");
        minimum.setMinimum(Integer.valueOf(0));
        minimum.setMaximum(Integer.valueOf(1));
        minimum.setFix("set \"minimum\" to 0 when the host works without a command on PATH, or 1 when it needs one");
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("requested", requested);
        properties.put("minimum", minimum);
        JsonSchema pathCommands = described(JsonSchema.ofType("object"),
                "The executables this host puts on the user's PATH, not the in-application slash commands, which are \"commands\".");
        pathCommands.setRequired(Arrays.asList("requested"));
        pathCommands.setProperties(properties);
        pathCommands.setFix("declare the PATH commands as {\"requested\": [\"name\"], \"minimum\": 0 or 1}; slash commands go in \"commands\" instead");
        return pathCommands;
    }

    private static JsonSchema methods() {
        JsonSchema methods = described(JsonSchema.ofType("array"), "The install methods this listing supports, by method id.");
        methods.setItems(JsonSchema.ofType("string"));
        methods.setUniqueItems(Boolean.TRUE);
        methods.setFix("list install method ids as strings, for example [\"npm\", \"homebrew\"]");
        return methods;
    }

    private static JsonSchema commands() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("name", described(JsonSchema.ofType("string"), "The command's name, which is also the file it is written to."));
        properties.put("description", described(JsonSchema.ofType("string"), "What a command picker shows beside the name."));
        properties.put("argumentHint", described(JsonSchema.ofType("string"),
                "The argument shape a picker hints at, such as \"list | get <key>\"."));
        properties.put("body", described(JsonSchema.ofType("string"), "Markdown the model is shown, after any shell output."));
        properties.put("shell", described(JsonSchema.ofType("string"),
                "A shell line run before the body, which may use $ARGUMENTS and {{BUNDLE}}."));
        JsonSchema command = described(JsonSchema.ofType("object"), "One slash command this plugin contributes.");
        command.setRequired(Arrays.asList("name", "description"));
        command.setProperties(properties);
        JsonSchema commands = described(JsonSchema.ofType("array"),
                "Slash commands this plugin contributes, which a host deploys without importing it.");
        commands.setItems(command);
        return commands;
    }

    private static JsonSchema data() {
        JsonSchema paths = described(JsonSchema.ofType("array"),
                "Paths this plugin writes to, relative to the home it runs in.");
        paths.setItems(JsonSchema.ofType("string"));
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("paths", paths);
        JsonSchema data = described(JsonSchema.ofType("object"),
                "Where this plugin keeps state that is not named after it.");
        data.setRequired(Arrays.asList("paths"));
        data.setProperties(properties);
        return data;
    }

    private static JsonSchema config() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("name", described(JsonSchema.ofType("string"),
                "The file these settings live in, config/<name>.json, when that is not the plugin's id."));
        properties.put("defaults", described(JsonSchema.ofType("object"),
                "Every setting this plugin has, and what it is worth until a home changes it."));
        JsonSchema config = described(JsonSchema.ofType("object"), "This plugin's settings as it ships them.");
        config.setRequired(Arrays.asList("defaults"));
        config.setProperties(properties);
        return config;
    }

    private static JsonSchema lifecycle() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("install", described(JsonSchema.ofType("boolean"), "The entry exports install(ctx), run once after first deploy."));
        properties.put("repair", described(JsonSchema.ofType("boolean"), "The entry exports repair(ctx), run on demand from a host."));
        JsonSchema lifecycle = described(JsonSchema.ofType("object"), "Which optional lifecycle hooks the entry module exports.");
        lifecycle.setProperties(properties);
        return lifecycle;
    }

    private static JsonSchema publish() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("scopedOnly", described(JsonSchema.ofType("boolean"),
                "Publish only as @intisy-ai/<name>, because the unscoped name is unavailable."));
        JsonSchema jarModule = described(JsonSchema.ofType("array"),
                "The Gradle modules whose jars ship as release assets, each named by its own classifier.");
        jarModule.setItems(JsonSchema.ofType("string"));
        properties.put("jarModule", jarModule);
        properties.put("generatedReadme", described(JsonSchema.ofType("boolean"),
                "The README is rendered at build time, so the release promotes it rather than testing it."));
        properties.put("jarPretest", described(JsonSchema.ofType("boolean"),
                "Run the Gradle build before the tests, because a test needs its jar installed first."));
        JsonSchema publish = described(JsonSchema.ofType("object"),
                "How the repo is published, to npm and as Java release assets.");
        publish.setProperties(properties);
        return publish;
    }

    private static JsonSchema repo() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("role", described(JsonSchema.ofType("string"),
                "The role phrase, capitalized, without the fixed \"for the forebay AI-proxy ecosystem.\" suffix."));
        properties.put("category", described(JsonSchema.ofType("string"),
                "The single category topic, for example core-library or ai-provider."));
        JsonSchema domains = described(JsonSchema.ofType("array"), "Domain topics, for example claude or gemini.");
        domains.setItems(JsonSchema.ofType("string"));
        properties.put("domains", domains);
        JsonSchema tech = described(JsonSchema.ofType("array"),
                "The tech topics, for example typescript, java or svelte.");
        tech.setItems(JsonSchema.ofType("string"));
        properties.put("tech", tech);
        JsonSchema topics = described(JsonSchema.ofType("array"),
                "Topics this repo needs that no other rule derives, for example github-actions.");
        topics.setItems(JsonSchema.ofType("string"));
        properties.put("topics", topics);
        JsonSchema repo = described(JsonSchema.ofType("object"),
                "Repository metadata: the GitHub description and topic set are derived from it.");
        repo.setRequired(Arrays.asList("role", "category", "tech"));
        repo.setProperties(properties);
        return repo;
    }

    private static JsonSchema marketplace() {
        Map<String, JsonSchema> matchProperties = new LinkedHashMap<String, JsonSchema>();
        JsonSchema topics = described(JsonSchema.ofType("array"), "Repository topics an entry must carry.");
        topics.setItems(JsonSchema.ofType("string"));
        matchProperties.put("topics", topics);
        matchProperties.put("kind", described(JsonSchema.ofType("string"),
                "The catalog kind an entry must be, as the reading host names its kinds."));
        JsonSchema match = described(JsonSchema.ofType("object"), "Which entries this category holds.");
        match.setProperties(matchProperties);

        Map<String, JsonSchema> categoryProperties = new LinkedHashMap<String, JsonSchema>();
        categoryProperties.put("id", described(JsonSchema.ofType("string"),
                "The category's id, unique across every plugin declaring one."));
        categoryProperties.put("label", described(JsonSchema.ofType("string"),
                "The name a surface shows. Absent means the id is shown."));
        categoryProperties.put("match", match);
        JsonSchema category = described(JsonSchema.ofType("object"),
                "One category a plugin adds to a host's catalog of installable things.");
        category.setRequired(Arrays.asList("id", "match"));
        category.setProperties(categoryProperties);

        JsonSchema categories = described(JsonSchema.ofType("array"), "Categories this plugin adds.");
        categories.setItems(category);
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("categories", categories);
        JsonSchema marketplace = described(JsonSchema.ofType("object"),
                "What this plugin contributes to a host's catalog of installable things.");
        marketplace.setRequired(Arrays.asList("categories"));
        marketplace.setProperties(properties);
        return marketplace;
    }

    /**
     * The app this repo is the loader for.
     *
     * @implNote Only id, label and home are required, although the contract's AppDescriptor marks
     * more than that non-optional: the interface describes a RESOLVED descriptor and this schema
     * validates a DECLARATION. A reader fills what a declaration omits, so requiring those fields
     * here would reject a valid app whose project left them to the defaults.
     */
    private static JsonSchema app() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("id", described(JsonSchema.ofType("string"),
                "The app's permanent id, for example claude or opencode."));
        properties.put("label", described(JsonSchema.ofType("string"), "The name a surface shows instead of the id."));
        properties.put("icon", described(JsonSchema.ofType("string"),
                "Path to a square-viewBox SVG mark for the app, relative to the repo root."));
        properties.put("home", appHome());
        properties.put("detect", appDetect());
        properties.put("loader", appLoader());
        properties.put("commandsSubdir", described(JsonSchema.ofType("string"),
                "The subdirectory inside the app home holding its slash commands."));
        properties.put("paths", appPathNames());
        properties.put("proxyPort", described(JsonSchema.ofType("integer"),
                "The port this app's proxy listens on, or 0 when it needs none."));
        JsonSchema integration = described(JsonSchema.ofType("string"), "How this app reaches the local API.");
        integration.setEnumValues(Arrays.asList("env-baseurl", "native"));
        integration.setFix("set \"integration\" to \"env-baseurl\" when the app is pointed at the proxy by an environment variable, or \"native\" when it loads the plugin itself");
        properties.put("integration", integration);
        properties.put("wireFormat", described(JsonSchema.ofType("string"),
                "The wire format this app speaks, for example anthropic."));
        properties.put("usage", appUsage());
        properties.put("accent", described(JsonSchema.ofType("string"),
                "Accent colour for this app's surfaces, as a #rrggbb hex string."));
        properties.put("wrapperCommand", described(JsonSchema.ofType("string"),
                "The command a user types to launch this app through its loader's wrapper."));
        properties.put("npmPlugins", appNpmPlugins());
        properties.put("startupHook", appStartupHook());
        properties.put("discovery", appDiscovery());
        properties.put("projects", appProjects());
        properties.put("modelCatalog", appModelCatalog());

        JsonSchema app = described(JsonSchema.ofType("object"),
                "The app this repo is the loader for, declared by the app's own project.");
        app.setRequired(Arrays.asList("id", "label", "home"));
        app.setProperties(properties);
        return app;
    }

    private static JsonSchema appHome() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("envOverride", described(JsonSchema.ofType("string"),
                "Environment variable that overrides every candidate, set by a host driving this app."));
        properties.put("nativeEnv", described(JsonSchema.ofType("string"),
                "The app's OWN environment variable for its config directory, which it reads itself."));
        properties.put("xdgSubdir", described(JsonSchema.ofType("string"),
                "Subdirectory under the XDG config directory, when the app follows that layout."));
        JsonSchema candidates = described(JsonSchema.ofType("array"),
                "Paths to try in order, each with a leading ~ for the user home.");
        candidates.setItems(JsonSchema.ofType("string"));
        properties.put("candidates", candidates);
        JsonSchema home = described(JsonSchema.ofType("object"),
                "Where this app keeps its home directory, in the order a resolver tries.");
        home.setRequired(Arrays.asList("candidates"));
        home.setProperties(properties);
        return home;
    }

    private static JsonSchema appDetect() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("binary", described(JsonSchema.ofType("string"),
                "The executable a user launches, looked up on the path."));
        properties.put("pkg", described(JsonSchema.ofType("string"),
                "The npm package the app ships as, for a global-install check."));
        JsonSchema detect = described(JsonSchema.ofType("object"), "How to tell whether this app is installed.");
        detect.setProperties(properties);
        return detect;
    }

    private static JsonSchema appLoader() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("id", described(JsonSchema.ofType("string"), "The loader plugin's id."));
        properties.put("url", described(JsonSchema.ofType("string"),
                "Where the loader is cloned from, as owner/repo or a full URL."));
        JsonSchema loader = described(JsonSchema.ofType("object"),
                "The plugin this app is reached through. Absent means the app has no loader.");
        loader.setRequired(Arrays.asList("id", "url"));
        loader.setProperties(properties);
        return loader;
    }

    private static JsonSchema appPathNames() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("repos", described(JsonSchema.ofType("string"), "Where plugin checkouts live."));
        properties.put("plugin", described(JsonSchema.ofType("string"),
                "Where deployed plugin bundles and their manifest sidecars live."));
        properties.put("cache", described(JsonSchema.ofType("string"), "Where cached downloads live."));
        properties.put("config", described(JsonSchema.ofType("string"), "Where configuration files live."));
        JsonSchema paths = described(JsonSchema.ofType("object"),
                "The names of the storage subdirectories inside this app's home.");
        paths.setProperties(properties);
        return paths;
    }

    private static JsonSchema appUsage() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        JsonSchema formats = described(JsonSchema.ofType("array"),
                "Format ids, each of which a consumer maps to a parser of its own.");
        formats.setItems(JsonSchema.ofType("string"));
        properties.put("formats", formats);
        JsonSchema usage = described(JsonSchema.ofType("object"),
                "Session-storage formats this app writes, for usage readers.");
        usage.setRequired(Arrays.asList("formats"));
        usage.setProperties(properties);
        return usage;
    }

    private static JsonSchema appNpmPlugins() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        JsonSchema configFiles = described(JsonSchema.ofType("array"),
                "Config files to look in, in order, for the plugin list.");
        configFiles.setItems(JsonSchema.ofType("string"));
        properties.put("configFiles", configFiles);
        properties.put("pluginsKey", described(JsonSchema.ofType("string"),
                "The key inside those files holding the plugin list."));
        properties.put("packageCache", described(JsonSchema.ofType("string"),
                "Where the app caches the packages it installed."));
        properties.put("schemaUrl", described(JsonSchema.ofType("string"),
                "The app's config schema, for an editor's completion."));
        JsonSchema npmPlugins = described(JsonSchema.ofType("object"),
                "This app's own npm-plugin mechanism. Absent means it has none.");
        npmPlugins.setRequired(Arrays.asList("configFiles", "pluginsKey"));
        npmPlugins.setProperties(properties);
        return npmPlugins;
    }

    private static JsonSchema appStartupHook() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("file", described(JsonSchema.ofType("string"), "The file to write, relative to the app home."));
        JsonSchema path = described(JsonSchema.ofType("array"), "The key path to the array the entry joins.");
        path.setItems(JsonSchema.ofType("string"));
        properties.put("path", path);
        properties.put("entry", described(JsonSchema.ofType("object"),
                "A JSON template whose strings have the {plugin} placeholder replaced with the plugin's name."));
        JsonSchema startupHook = described(JsonSchema.ofType("object"),
                "How this app runs a plugin at startup when it has no npm-plugin list of its own.");
        startupHook.setRequired(Arrays.asList("file", "path", "entry"));
        startupHook.setProperties(properties);
        return startupHook;
    }

    private static JsonSchema appDiscovery() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("topic", described(JsonSchema.ofType("string"),
                "The repository topic a community plugin carries."));
        properties.put("searchQuery", described(JsonSchema.ofType("string"),
                "A free-text search to run where the topic alone under-reports."));
        properties.put("awesomeList", described(JsonSchema.ofType("string"),
                "A curated list to read, as a raw URL."));
        JsonSchema discovery = described(JsonSchema.ofType("object"),
                "Where a marketplace looks for this app's community plugins.");
        discovery.setProperties(properties);
        return discovery;
    }

    private static JsonSchema appProjects() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("historyFile", described(JsonSchema.ofType("string"), "A history file inside the app home."));
        JsonSchema sessionDb = described(JsonSchema.ofType("array"),
                "Session databases to try in order, absolute or relative to the app home.");
        sessionDb.setItems(JsonSchema.ofType("string"));
        properties.put("sessionDb", sessionDb);
        properties.put("markerFile", described(JsonSchema.ofType("string"),
                "The file the app writes inside a project's git directory to record the project id."));
        JsonSchema projects = described(JsonSchema.ofType("object"),
                "Where this app records the projects a user has worked in.");
        projects.setProperties(properties);
        return projects;
    }

    private static JsonSchema appModelCatalog() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        JsonSchema files = described(JsonSchema.ofType("array"), "Files to try in order, relative to the app home.");
        files.setItems(JsonSchema.ofType("string"));
        properties.put("files", files);
        properties.put("envOverride", described(JsonSchema.ofType("string"),
                "Environment variable naming the config file outright."));
        properties.put("schemaUrl", described(JsonSchema.ofType("string"),
                "The app's config schema, for an editor's completion."));
        properties.put("providerKey", described(JsonSchema.ofType("string"),
                "The key inside that file holding the catalog, named after the app's own config key."));
        JsonSchema modelCatalog = described(JsonSchema.ofType("object"),
                "The app config file a model catalog is merged into.");
        modelCatalog.setRequired(Arrays.asList("files", "providerKey"));
        modelCatalog.setProperties(properties);
        return modelCatalog;
    }

    private static JsonSchema keyedBy(JsonSchema record, String fix) {
        JsonSchema names = JsonSchema.ofType(null);
        names.setPattern(KEY_PATTERN);
        names.setFix(fix);
        record.setPropertyNames(names);
        return record;
    }

    private static JsonSchema withFix(JsonSchema schema, String fix) {
        schema.setFix(fix);
        return schema;
    }

    private static JsonSchema enumerated(String description, String fix, String... values) {
        JsonSchema schema = described(JsonSchema.ofType("string"), description);
        schema.setEnumValues(Arrays.asList(values));
        schema.setFix(fix);
        return schema;
    }

    private static JsonSchema uiTone() {
        return enumerated("A semantic colour role; a host maps it to its own palette.",
                "set the tone to one of neutral, accent, success, warning or danger; a raw colour is not accepted",
                "neutral", "accent", "success", "warning", "danger");
    }

    private static JsonSchema uiBinding() {
        JsonSchema method = described(JsonSchema.ofType("string"), "The service method to call.");
        method.setPattern(KEY_PATTERN);
        method.setFix("start a method name with a lowercase letter, then letters and digits only, for example \"read\"");
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("service", described(JsonSchema.ofType("string"), "The id of a service this plugin provides."));
        properties.put("method", method);
        properties.put("argument", described(JsonSchema.ofType("object"), "The argument sent with the call."));
        JsonSchema binding = described(JsonSchema.ofType("object"), "A call into one of this plugin's own services.");
        binding.setProperties(properties);
        binding.setRequired(Arrays.asList("service", "method"));
        binding.setFix("write the binding as {\"service\": \"<id>\", \"method\": \"<name>\"}, optionally with an \"argument\" object");
        return binding;
    }

    private static JsonSchema uiSettings() {
        JsonSchema settings = described(JsonSchema.ofType("object"), "Settings this plugin lets a user change, keyed by setting key.");
        keyedBy(settings, "start a setting key with a lowercase letter, then letters and digits only, for example \"retryLimit\"");
        settings.setAdditionalProperties(setting());
        settings.setFix("map each setting key to an object carrying \"type\" and \"label\"");
        return settings;
    }

    private static JsonSchema setting() {
        JsonSchema choices = described(JsonSchema.ofType("array"), "The values a choice or multi-choice setting offers.");
        choices.setItems(JsonSchema.ofType("string"));
        choices.setFix("list the allowed values as strings, for example [\"fast\", \"safe\"]");
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("type", enumerated("How a host edits the value.",
                "set the type to one of text, secret, number, toggle, choice, multi-choice, path, list or key-value",
                "text", "secret", "number", "toggle", "choice", "multi-choice", "path", "list", "key-value"));
        properties.put("label", described(JsonSchema.ofType("string"), "The text a host shows beside the control."));
        properties.put("help", described(JsonSchema.ofType("string"), "A sentence a host shows under the control."));
        properties.put("default", described(JsonSchema.ofType(null), "The value in force until a user changes it."));
        properties.put("layer", enumerated(
                "Whether the value travels with the user (portable) or stays with this installation (application). Absent means portable.",
                "set the layer to portable or application", "portable", "application"));
        properties.put("choices", choices);
        properties.put("minimum", described(JsonSchema.ofType("integer"), "The lowest value a number setting accepts."));
        properties.put("maximum", described(JsonSchema.ofType("integer"), "The highest value a number setting accepts."));
        properties.put("pattern", described(JsonSchema.ofType("string"), "A regular expression a text value must match."));
        JsonSchema setting = described(JsonSchema.ofType("object"), "One setting: its type, its label and the limits a host enforces.");
        setting.setProperties(properties);
        setting.setRequired(Arrays.asList("type", "label"));
        setting.setIf(choiceTyped());
        setting.setThen(choicesListed());
        setting.setFix("write the setting as an object carrying \"type\" and \"label\"");
        return setting;
    }

    private static JsonSchema choiceTyped() {
        JsonSchema choiceType = JsonSchema.ofType(null);
        choiceType.setEnumValues(Arrays.asList("choice", "multi-choice"));
        return requiring("type", choiceType);
    }

    private static JsonSchema choicesListed() {
        String fix = "a choice or multi-choice setting needs \"choices\", for example [\"fast\", \"safe\"]";
        JsonSchema atLeastOne = withFix(JsonSchema.ofType("array"), fix);
        atLeastOne.setMinItems(Integer.valueOf(1));
        return withFix(requiring("choices", atLeastOne), fix);
    }

    private static JsonSchema actions() {
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("label", described(JsonSchema.ofType("string"), "The text a host shows on the control."));
        properties.put("call", uiBinding());
        properties.put("confirm", described(JsonSchema.ofType("string"),
                "A question a host asks before running the action. Absent means it runs at once."));
        properties.put("input", uiSettings());
        properties.put("tone", uiTone());
        JsonSchema action = described(JsonSchema.ofType("object"), "One action: what a host labels it and which service method it calls.");
        action.setProperties(properties);
        action.setRequired(Arrays.asList("label", "call"));
        action.setFix("write the action as an object carrying \"label\" and \"call\", for example {\"label\": \"Refresh\", \"call\": {\"service\": \"demo:screens\", \"method\": \"refresh\"}}");
        JsonSchema actions = described(JsonSchema.ofType("object"),
                "Operations a host offers the user for this plugin, each a call into one of its own services.");
        keyedBy(actions, "start an action id with a lowercase letter, then letters and digits only, for example \"refresh\"");
        actions.setAdditionalProperties(action);
        actions.setFix("map each action id to an object carrying \"label\" and \"call\"");
        return actions;
    }

    private static JsonSchema views() {
        JsonSchema nodes = described(JsonSchema.ofType("array"),
                "The view's nodes as a flat list; a node's \"parent\" names the node it sits under, which must come earlier in the list.");
        nodes.setMinItems(Integer.valueOf(1));
        nodes.setItems(uiNode());
        nodes.setFix("list at least one node, each an object carrying \"id\" and \"component\"");
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("title", described(JsonSchema.ofType("string"), "The name a host shows for the view."));
        properties.put("nodes", nodes);
        JsonSchema view = described(JsonSchema.ofType("object"), "One view: a title and the nodes that make it up.");
        view.setProperties(properties);
        view.setRequired(Arrays.asList("title", "nodes"));
        view.setFix("write the view as an object carrying \"title\" and a non-empty \"nodes\" list");
        JsonSchema views = described(JsonSchema.ofType("object"),
                "Screens a host draws for this plugin, each a flat list of nodes that name their parent.");
        keyedBy(views, "start a view id with a lowercase letter, then letters and digits only, for example \"screens\"");
        views.setAdditionalProperties(view);
        views.setFix("map each view id to an object carrying \"title\" and \"nodes\"");
        return views;
    }

    private static JsonSchema uiNode() {
        JsonSchema id = described(JsonSchema.ofType("string"), "The node's identity within its view.");
        id.setPattern(KEY_PATTERN);
        id.setFix("start a node id with a lowercase letter, then letters and digits only, for example \"mainTable\"");
        JsonSchema icon = described(JsonSchema.ofType("string"), "The name of a host icon.");
        icon.setPattern(ICON_PATTERN);
        icon.setFix("name the icon in lowercase words joined by single hyphens, for example \"arrow-right\"");
        JsonSchema span = described(JsonSchema.ofType("integer"), "How many of a grid's twelve columns the node takes.");
        span.setMinimum(Integer.valueOf(1));
        span.setMaximum(Integer.valueOf(12));
        span.setFix("set the span to a whole number from 1 to 12");
        Map<String, JsonSchema> properties = new LinkedHashMap<String, JsonSchema>();
        properties.put("id", id);
        properties.put("parent", described(JsonSchema.ofType("string"), "The id of the node this one sits under. Absent means the view's root."));
        properties.put("component", component());
        properties.put("size", enumerated("The node's size as a role.",
                "set the size to small, medium or large; a pixel value is not accepted", "small", "medium", "large"));
        properties.put("emphasis", enumerated("How much attention the node asks for.",
                "set the emphasis to low, normal or high", "low", "normal", "high"));
        properties.put("tone", uiTone());
        properties.put("label", described(JsonSchema.ofType("string"), "Text a host shows as the node's heading or caption."));
        properties.put("text", described(JsonSchema.ofType("string"), "The node's own text."));
        properties.put("icon", icon);
        properties.put("bind", uiBinding());
        properties.put("action", described(JsonSchema.ofType("string"), "The id of one of this plugin's actions the node runs."));
        properties.put("field", described(JsonSchema.ofType("string"), "The key of one of this plugin's settings the node edits."));
        properties.put("chart", enumerated("The chart form of a chart node.",
                "set the chart to line, bar, sparkline or meter", "line", "bar", "sparkline", "meter"));
        properties.put("span", span);
        JsonSchema node = described(JsonSchema.ofType("object"),
                "One element of a view. It names what it is and how it looks as roles, never as raw colours or sizes.");
        node.setProperties(properties);
        node.setRequired(Arrays.asList("id", "component"));
        node.setFix("write the node as an object carrying \"id\" and \"component\"");
        return node;
    }

    private static JsonSchema component() {
        return enumerated("What the node is.",
                "set the component to one of stack, grid, split, tabs, section, card, divider, text, list, table, tree, detail, chart, log, code, diff, badge, icon, field, search, form, button, dialog, toast, progress, spinner, empty, loading, error or unavailable",
                "stack", "grid", "split", "tabs", "section", "card", "divider", "text", "list", "table",
                "tree", "detail", "chart", "log", "code", "diff", "badge", "icon", "field", "search",
                "form", "button", "dialog", "toast", "progress", "spinner", "empty", "loading", "error", "unavailable");
    }

    private static JsonSchema described(JsonSchema schema, String description) {
        schema.setDescription(description);
        return schema;
    }
}
