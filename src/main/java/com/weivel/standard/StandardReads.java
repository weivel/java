package com.weivel.standard;

import com.weivel.core.ForeignMemory;
import com.weivel.core.Layout;
import com.weivel.core.Manifest;
import com.weivel.host.LibraryDeclaration;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongUnaryOperator;

/**
 * Copies the structs the standard fills into Java values, every array of its non-growing elements stepped at this
 * package's own stride, as the standard's convention for them says. Nothing here frees: the caller releases each
 * struct through the standard once it is copied.
 */
final class StandardReads {
    private static final long WORD = Layout.ADDRESS.byteSize();

    private StandardReads() {
    }

    static long offset(Layout layout, String... names) {
        return layout.offsetOf(names);
    }

    static Optional<String> optionalText(ForeignMemory memory, long pointer) {
        return pointer == 0 ? Optional.<String>empty() : Optional.of(memory.readText(pointer));
    }

    static String textOrEmpty(ForeignMemory memory, long pointer) {
        return optionalText(memory, pointer).orElse("");
    }

    static Optional<StandardRefusal> refusal(ForeignMemory memory, long refusal) {
        Layout layout = StandardLayouts.REFUSAL;
        int code = memory.getInt(refusal, offset(layout, "code"));
        if (code == 0) {
            return Optional.empty();
        }
        return Optional.of(new StandardRefusal(code,
                textOrEmpty(memory, memory.getLong(refusal, offset(layout, "detail"))),
                textOrEmpty(memory, memory.getLong(refusal, offset(layout, "fix"))),
                textOrEmpty(memory, memory.getLong(refusal, offset(layout, "path")))));
    }

    /** The texts of a list of {@code count} pointers at {@code items}. */
    static List<String> texts(ForeignMemory memory, long items, long count) {
        List<String> texts = new ArrayList<>();
        for (long index = 0; index < count; index++) {
            texts.add(memory.readText(memory.getLong(items, index * WORD)));
        }
        return texts;
    }

    /** The core's {@code weivel_strings} at an address. */
    static List<String> strings(ForeignMemory memory, long strings) {
        Layout layout = StandardLayouts.STRINGS_OF_THE_CORE;
        return texts(memory, memory.getLong(strings, offset(layout, "items")),
                memory.getInt(strings, offset(layout, "count")));
    }

    static Identity identity(ForeignMemory memory, long identity) {
        Layout layout = StandardLayouts.IDENTITY;
        boolean local = memory.getInt(identity, offset(layout, "kind")) == 0;
        long segments = memory.getLong(identity, offset(layout, "segments"));
        long segmentCount = memory.getLong(identity, offset(layout, "segment_count"));
        return new Identity(local ? Identity.Kind.LOCAL : Identity.Kind.FORGE,
                local ? Optional.of(localName(memory, identity)) : Optional.<String>empty(),
                optionalText(memory, memory.getLong(identity, offset(layout, "forge"))),
                optionalText(memory, memory.getLong(identity, offset(layout, "host"))),
                new BigInteger(Long.toUnsignedString(memory.getLong(identity, offset(layout, "number")))),
                texts(memory, segments, segmentCount));
    }

    /** A local name read by its length, since it may hold a NUL character. */
    private static String localName(ForeignMemory memory, long identity) {
        Layout layout = StandardLayouts.IDENTITY;
        long length = memory.getLong(identity, offset(layout, "name_length"));
        if (length > Integer.MAX_VALUE) {
            throw new IllegalStateException("a local name of " + length + " bytes is longer than Java holds");
        }
        long name = memory.getLong(identity, offset(layout, "name"));
        return new String(memory.readBytes(name, 0, (int) length), StandardCharsets.UTF_8);
    }

    static Discovery discovery(ForeignMemory memory, long discovery, LongUnaryOperator manifestView) {
        Layout layout = StandardLayouts.DISCOVERY;
        long plugins = memory.getLong(discovery, offset(layout, "plugins"));
        long pluginCount = memory.getLong(discovery, offset(layout, "plugin_count"));
        long failures = memory.getLong(discovery, offset(layout, "failures"));
        long failureCount = memory.getLong(discovery, offset(layout, "failure_count"));
        List<DiscoveredPlugin> found = new ArrayList<>();
        for (long index = 0; index < pluginCount; index++) {
            found.add(discoveredPlugin(memory, plugins + index * StandardLayouts.DISCOVERED_PLUGIN.byteSize(),
                    manifestView));
        }
        List<Discovery.Failure> failed = new ArrayList<>();
        for (long index = 0; index < failureCount; index++) {
            failed.add(failure(memory, failures + index * StandardLayouts.DISCOVERY_FAILURE.byteSize()));
        }
        return new Discovery(found, failed);
    }

    private static DiscoveredPlugin discoveredPlugin(ForeignMemory memory, long plugin,
                                                     LongUnaryOperator manifestView) {
        Layout layout = StandardLayouts.DISCOVERED_PLUGIN;
        Optional<String> identityPath = optionalText(memory, memory.getLong(plugin, offset(layout, "identity_path")));
        Optional<String> identityId = optionalText(memory, memory.getLong(plugin, offset(layout, "identity_id")));
        Manifest manifest = memory.manifest(manifestView.applyAsLong(
                memory.getLong(plugin, offset(layout, "manifest"))));
        return new DiscoveredPlugin(identified(manifest, identityId),
                memory.readText(memory.getLong(plugin, offset(layout, "manifest_path"))),
                identityPath,
                optionalText(memory, memory.getLong(plugin, offset(layout, "repository_name"))),
                identityPath.isPresent() ? Optional.of(identity(memory, plugin + offset(layout, "identity")))
                        : Optional.<Identity>empty(),
                optionalText(memory, memory.getLong(plugin, offset(layout, "entry_path")))
                        .map(java.nio.file.Paths::get),
                memory.getInt(plugin, offset(layout, "entry_present")) != 0);
    }

    /** The manifest carrying the identity file's id, the identity the standard leaves for its caller to set. */
    private static Manifest identified(Manifest manifest, Optional<String> identityId) {
        if (!identityId.isPresent()) {
            return manifest;
        }
        return new Manifest(manifest.importName(), manifest.api(), manifest.entry(), manifest.targets(),
                manifest.capabilitiesProvided(), manifest.capabilitiesConsumed(), manifest.servicesProvided(),
                manifest.servicesConsumed(), manifest.permissions(), identityId);
    }

    private static Discovery.Failure failure(ForeignMemory memory, long failure) {
        Layout layout = StandardLayouts.DISCOVERY_FAILURE;
        String file = memory.readText(memory.getLong(failure, offset(layout, "file")));
        StandardRefusal why = refusal(memory, failure + offset(layout, "refusal")).orElseThrow(() ->
                new IllegalStateException("the standard named " + file + " a failure with no refusal"));
        return new Discovery.Failure(file, why);
    }

    static LibraryDeclaration library(ForeignMemory memory, long library) {
        Layout layout = StandardLayouts.LIBRARY;
        long services = memory.getLong(library, offset(layout, "services"));
        long serviceCount = memory.getLong(library, offset(layout, "service_count"));
        long descriptions = memory.getLong(library, offset(layout, "descriptions"));
        List<LibraryDeclaration.Service> declared = new ArrayList<>();
        for (long index = 0; index < serviceCount; index++) {
            Optional<String> description = descriptions == 0 ? Optional.<String>empty()
                    : optionalText(memory, memory.getLong(descriptions, index * WORD));
            declared.add(service(memory, services + index * StandardLayouts.LIBRARY_SERVICE.byteSize(),
                    description));
        }
        long release = memory.getLong(library, offset(layout, "release"));
        return new LibraryDeclaration(memory.readText(memory.getLong(library, offset(layout, "import_name"))),
                memory.readText(memory.getLong(library, offset(layout, "version"))),
                release == 0 ? Optional.<String>empty() : Optional.of(memory.readText(release)), declared,
                contracts(memory, library));
    }

    private static List<LibraryDeclaration.Contract> contracts(ForeignMemory memory, long library) {
        Layout layout = StandardLayouts.LIBRARY_CONTRACT;
        long contracts = memory.getLong(library, offset(StandardLayouts.LIBRARY, "contracts"));
        long contractCount = memory.getLong(library, offset(StandardLayouts.LIBRARY, "contract_count"));
        List<LibraryDeclaration.Contract> published = new ArrayList<>();
        for (long index = 0; index < contractCount; index++) {
            long contract = contracts + index * layout.byteSize();
            published.add(new LibraryDeclaration.Contract(
                    memory.readText(memory.getLong(contract, offset(layout, "capability"))),
                    memory.readText(memory.getLong(contract, offset(layout, "description")))));
        }
        return published;
    }

    private static LibraryDeclaration.Service service(ForeignMemory memory, long service,
                                                      Optional<String> description) {
        Layout layout = StandardLayouts.LIBRARY_SERVICE;
        long functions = memory.getLong(service, offset(layout, "functions"));
        long functionCount = memory.getLong(service, offset(layout, "function_count"));
        List<LibraryDeclaration.Function> declared = new ArrayList<>();
        for (long index = 0; index < functionCount; index++) {
            declared.add(function(memory, functions + index * StandardLayouts.LIBRARY_FUNCTION.byteSize()));
        }
        return new LibraryDeclaration.Service(memory.readText(memory.getLong(service, offset(layout, "id"))),
                declared, description);
    }

    private static LibraryDeclaration.Function function(ForeignMemory memory, long function) {
        Layout layout = StandardLayouts.LIBRARY_FUNCTION;
        return new LibraryDeclaration.Function(memory.readText(memory.getLong(function, offset(layout, "name"))),
                memory.readText(memory.getLong(function, offset(layout, "symbol"))),
                memory.getInt(function, offset(layout, "kind")),
                memory.getInt(function, offset(layout, "context")));
    }

    static List<String> templates(ForeignMemory memory, long list) {
        Layout layout = StandardLayouts.MIRROR_LIST;
        return texts(memory, memory.getLong(list, offset(layout, "templates")),
                memory.getLong(list, offset(layout, "count")));
    }
}
